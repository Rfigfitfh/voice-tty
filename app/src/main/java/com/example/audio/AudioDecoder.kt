package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioDecoder {
    private const val TAG = "AudioDecoder"

    fun decodeToWav(context: Context, uri: Uri, outputFile: File): Boolean {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var fos: FileOutputStream? = null
        val tempPcmFile = File(outputFile.parent, "temp_decode_${System.currentTimeMillis()}.pcm")

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: return false

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }

            if (trackIndex < 0 || format == null) {
                Log.e(TAG, "No audio track found in the file")
                return false
            }

            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            fos = FileOutputStream(tempPcmFile)

            val info = MediaCodec.BufferInfo()
            var isInputEOS = false
            var isOutputEOS = false
            val timeoutUs = 10000L

            val inputSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                44100
            }
            val inputChannels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                1
            }

            var totalBytesWritten = 0L

            while (!isOutputEOS) {
                if (!isInputEOS) {
                    val inputBufferIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inputBufferIndex >= 0) {
                        val dstBuf = codec.getInputBuffer(inputBufferIndex)
                        if (dstBuf != null) {
                            dstBuf.clear()
                            val sampleSize = extractor.readSampleData(dstBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                isInputEOS = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    sampleSize,
                                    presentationTimeUs,
                                    0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputBufferIndex = codec.dequeueOutputBuffer(info, timeoutUs)
                if (outputBufferIndex >= 0) {
                    val outBuf = codec.getOutputBuffer(outputBufferIndex)
                    if (outBuf != null && info.size > 0) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)

                        val chunk = ByteArray(info.size)
                        outBuf.get(chunk)

                        val processedBytes = processPCM(chunk, inputSampleRate, inputChannels)
                        fos.write(processedBytes)
                        totalBytesWritten += processedBytes.size
                    }

                    codec.releaseOutputBuffer(outputBufferIndex, false)

                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEOS = true
                    }
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Update sample rate/channels dynamically if they changed
                    val newFormat = codec.outputFormat
                    Log.d(TAG, "Decoder output format changed: $newFormat")
                }
            }

            codec.stop()
            codec.release()
            codec = null
            fos.close()
            fos = null

            // Write the WAV header with 44100Hz Mono 16-bit specifications
            writeWavHeader(outputFile, tempPcmFile, totalBytesWritten)
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error decoding audio file", e)
            return false
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (ex: Exception) {
                // Ignore
            }
            try {
                extractor.release()
            } catch (ex: Exception) {
                // Ignore
            }
            try {
                fos?.close()
            } catch (ex: Exception) {
                // Ignore
            }
            if (tempPcmFile.exists()) {
                tempPcmFile.delete()
            }
        }
    }

    private fun processPCM(inputData: ByteArray, sourceSampleRate: Int, sourceChannels: Int): ByteArray {
        val numShorts = inputData.size / 2
        if (numShorts <= 0) return ByteArray(0)

        val inputShorts = ShortArray(numShorts)
        ByteBuffer.wrap(inputData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(inputShorts)

        // 1. Convert channels to Mono if multiple channels
        var monoShorts = if (sourceChannels > 1) {
            val outputSize = inputShorts.size / sourceChannels
            val mono = ShortArray(outputSize)
            for (i in 0 until outputSize) {
                var sum = 0
                for (c in 0 until sourceChannels) {
                    sum += inputShorts[i * sourceChannels + c]
                }
                mono[i] = (sum / sourceChannels).coerceIn(-32768, 32767).toShort()
            }
            mono
        } else {
            inputShorts
        }

        // 2. Resample to 44100Hz if sample rate is different (linear interpolation)
        if (sourceSampleRate != 44100 && sourceSampleRate > 0) {
            val ratio = sourceSampleRate.toDouble() / 44100.0
            val resampledSize = (monoShorts.size / ratio).toInt()
            if (resampledSize > 0) {
                val resampled = ShortArray(resampledSize)
                for (i in 0 until resampledSize) {
                    val srcIndex = i * ratio
                    val index1 = srcIndex.toInt()
                    val index2 = (index1 + 1).coerceAtMost(monoShorts.size - 1)
                    val frac = srcIndex - index1
                    val s1 = monoShorts[index1].toFloat()
                    val s2 = monoShorts[index2].toFloat()
                    resampled[i] = (s1 + frac * (s2 - s1)).toInt().coerceIn(-32768, 32767).toShort()
                }
                monoShorts = resampled
            }
        }

        // Convert ShortArray back to ByteArray (little-endian)
        val outputBytes = ByteArray(monoShorts.size * 2)
        ByteBuffer.wrap(outputBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(monoShorts)
        return outputBytes
    }

    private fun writeWavHeader(wavFile: File, pcmFile: File, rawLength: Long) {
        val totalDataLen = rawLength + 36
        val sampleRate = 44100
        val byteRate = sampleRate * 2 // 44100 * 1 channel * 2 bytes/sample

        var fis: FileInputStream? = null
        var fos: FileOutputStream? = null

        try {
            fis = FileInputStream(pcmFile)
            fos = FileOutputStream(wavFile)

            val header = ByteArray(44)
            header[0] = 'R'.toByte() // RIFF
            header[1] = 'I'.toByte()
            header[2] = 'F'.toByte()
            header[3] = 'F'.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte()
            header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            header[8] = 'W'.toByte() // WAVE
            header[9] = 'A'.toByte()
            header[10] = 'V'.toByte()
            header[11] = 'E'.toByte()
            header[12] = 'f'.toByte() // fmt
            header[13] = 'm'.toByte()
            header[14] = 't'.toByte()
            header[15] = ' '.toByte()
            header[16] = 16 // Subchunk1Size (16 for PCM)
            header[17] = 0
            header[18] = 0
            header[19] = 0
            header[20] = 1 // AudioFormat = 1 (PCM)
            header[21] = 0
            header[22] = 1 // NumChannels = 1 (Mono)
            header[23] = 0
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = 2 // BlockAlign (Channels * BytesPerSample)
            header[33] = 0
            header[34] = 16 // BitsPerSample
            header[35] = 0
            header[36] = 'd'.toByte() // data
            header[37] = 'a'.toByte()
            header[38] = 't'.toByte()
            header[39] = 'a'.toByte()
            header[40] = (rawLength and 0xff).toByte()
            header[41] = ((rawLength shr 8) and 0xff).toByte()
            header[42] = ((rawLength shr 16) and 0xff).toByte()
            header[43] = ((rawLength shr 24) and 0xff).toByte()

            fos.write(header, 0, 44)

            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                fos.write(buffer, 0, bytesRead)
            }
        } finally {
            try { fis?.close() } catch (e: Exception) {}
            try { fos?.close() } catch (e: Exception) {}
        }
    }
}
