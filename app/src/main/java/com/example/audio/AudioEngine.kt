package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.Recording
import com.example.data.RecordingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

class AudioEngine(private val context: Context) {

    private val TAG = "AudioEngine"
    val processor = AudioProcessor(44100f)
    private val db = AppDatabase.getDatabase(context)
    private val repository = RecordingRepository(db.recordingDao())

    // Coroutine scope for running async audio tasks
    private val engineScope = CoroutineScope(Dispatchers.IO)
    private var recordJob: Job? = null
    private var playJob: Job? = null

    // Recording States
    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0L) // milliseconds
    val recordingDuration = _recordingDuration.asStateFlow()

    private val _rawAmplitude = MutableStateFlow(0f)
    val rawAmplitude = _rawAmplitude.asStateFlow()

    private val _enhancedAmplitude = MutableStateFlow(0f)
    val enhancedAmplitude = _enhancedAmplitude.asStateFlow()

    // Real-time audio monitoring loopback
    var isMonitorEnabled = false

    // Playback States
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f) // 0.0 to 1.0
    val playbackProgress = _playbackProgress.asStateFlow()

    private val _playingRecordingId = MutableStateFlow<Int?>(null)
    val playingRecordingId = _playingRecordingId.asStateFlow()

    private var currentPlayFile: String? = null

    // Recording Configuration
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = max(
        AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat),
        2048 * 2
    )

    @SuppressLint("MissingPermission")
    fun startRecording(title: String) {
        if (_isRecording.value) return
        _isRecording.value = true

        val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
        val tempPcmFile = File(outputDir, "temp_voice_recording.pcm")
        val finalWavFile = File(outputDir, "enhanced_${System.currentTimeMillis()}.wav")

        recordJob = engineScope.launch {
            var audioRecord: AudioRecord? = null
            var audioTrackMonitor: AudioTrack? = null
            var outStream: FileOutputStream? = null
            var startTime = SystemClock.elapsedRealtime()

            try {
                // Initialize AudioRecord
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord initialization failed")
                    _isRecording.value = false
                    return@launch
                }

                // If live monitoring is enabled, set up AudioTrack loopback
                if (isMonitorEnabled) {
                    val outBufferSize = AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    audioTrackMonitor = AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        outBufferSize,
                        AudioTrack.MODE_STREAM
                    )
                    audioTrackMonitor.play()
                }

                outStream = FileOutputStream(tempPcmFile)
                audioRecord.startRecording()
                startTime = SystemClock.elapsedRealtime()

                val buffer = ShortArray(1024)

                while (_isRecording.value) {
                    val readSize = audioRecord.read(buffer, 0, buffer.size)
                    if (readSize > 0) {
                        // 1. Calculate raw amplitude peak
                        var rawPeak = 0f
                        for (i in 0 until readSize) {
                            val absVal = kotlin.math.abs(buffer[i].toFloat())
                            if (absVal > rawPeak) rawPeak = absVal
                        }
                        _rawAmplitude.value = rawPeak / 32768f

                        // 2. Process PCM shorts through DSP pipeline
                        val processedBuffer = processor.process(buffer.copyOf(readSize))

                        // 3. Calculate enhanced amplitude peak
                        var enhancedPeak = 0f
                        for (i in processedBuffer.indices) {
                            val absVal = kotlin.math.abs(processedBuffer[i].toFloat())
                            if (absVal > enhancedPeak) enhancedPeak = absVal
                        }
                        _enhancedAmplitude.value = enhancedPeak / 32768f

                        // 4. Write processed buffer to PCM file
                        val byteBuffer = ByteBuffer.allocate(processedBuffer.size * 2)
                        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
                        for (s in processedBuffer) {
                            byteBuffer.putShort(s)
                        }
                        outStream.write(byteBuffer.array())

                        // 5. Play to monitor if active
                        if (isMonitorEnabled && audioTrackMonitor != null) {
                            audioTrackMonitor.write(processedBuffer, 0, processedBuffer.size)
                        }

                        // Update elapsed duration
                        _recordingDuration.value = SystemClock.elapsedRealtime() - startTime
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Recording error: ${e.message}", e)
            } finally {
                // Safely release system audio resources
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing recorder: ${e.message}")
                }

                try {
                    audioTrackMonitor?.stop()
                    audioTrackMonitor?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing monitor: ${e.message}")
                }

                try {
                    outStream?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing outStream: ${e.message}")
                }

                val duration = SystemClock.elapsedRealtime() - startTime

                // Convert Temp raw PCM into a standard WAV file with proper 44-byte Header
                if (tempPcmFile.exists() && tempPcmFile.length() > 0) {
                    val rawLength = tempPcmFile.length()
                    writeWavFile(tempPcmFile, finalWavFile, rawLength)
                    tempPcmFile.delete()

                    // Insert recording record into Database
                    val recTitle = if (title.isBlank()) "Enhanced Audio Recording" else title
                    val recording = Recording(
                        title = recTitle,
                        filePath = finalWavFile.absolutePath,
                        durationMs = duration,
                        fileSize = finalWavFile.length(),
                        filterPreset = processor.selectedPreset,
                        noiseThreshold = processor.noiseThreshold,
                        isEnhanced = true
                    )
                    repository.insert(recording)
                }
                _rawAmplitude.value = 0f
                _enhancedAmplitude.value = 0f
                _recordingDuration.value = 0L
            }
        }
    }

    fun stopRecording() {
        _isRecording.value = false
    }

    /**
     * Custom Wav player with real-time DSP filter application.
     * This reads the WAV PCM data, passes it through the AudioProcessor,
     * and streams it to AudioTrack.
     */
    fun startPlayback(recording: Recording) {
        if (_isPlaying.value) {
            stopPlayback()
        }

        _isPlaying.value = true
        _playingRecordingId.value = recording.id
        currentPlayFile = recording.filePath

        playJob = engineScope.launch {
            var audioTrack: AudioTrack? = null
            var fis: FileInputStream? = null

            try {
                val file = File(recording.filePath)
                if (!file.exists()) {
                    Log.e(TAG, "Wav playback file does not exist")
                    _isPlaying.value = false
                    _playingRecordingId.value = null
                    return@launch
                }

                val fileLength = file.length()
                fis = FileInputStream(file)

                // Skip WAV header (44 bytes)
                val skipped = fis.skip(44)
                if (skipped < 44) {
                    Log.e(TAG, "Invalid WAV file structure")
                    _isPlaying.value = false
                    _playingRecordingId.value = null
                    return@launch
                }

                val trackBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    trackBufferSize,
                    AudioTrack.MODE_STREAM
                )

                audioTrack.play()

                val byteBuffer = ByteArray(2048) // 1024 shorts
                val shortBuffer = ShortArray(1024)

                val totalDataBytes = fileLength - 44
                var readBytesTotal = 0L

                while (_isPlaying.value) {
                    val readCount = fis.read(byteBuffer, 0, byteBuffer.size)
                    if (readCount <= 0) break

                    readBytesTotal += readCount

                    // Convert raw bytes to short array (little-endian)
                    val shortsToProcess = readCount / 2
                    ByteBuffer.wrap(byteBuffer, 0, readCount)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer()
                        .get(shortBuffer, 0, shortsToProcess)

                    // Process processedBuffer in real-time with currently selected filters!
                    // This lets users change filters dynamically during playback!
                    val processedShorts = processor.process(shortBuffer.copyOf(shortsToProcess))

                    // Feed to AudioTrack
                    audioTrack.write(processedShorts, 0, processedShorts.size)

                    // Update visual progress state
                    _playbackProgress.value = readBytesTotal.toFloat() / totalDataBytes.toFloat()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Playback error: ${e.message}", e)
            } finally {
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing track: ${e.message}")
                }
                try {
                    fis?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing FileInputStream: ${e.message}")
                }
                _isPlaying.value = false
                _playingRecordingId.value = null
                _playbackProgress.value = 0f
            }
        }
    }

    fun stopPlayback() {
        _isPlaying.value = false
        playJob?.cancel()
        playJob = null
    }

    /**
     * Converts a raw PCM file to standard WAV by prepending a 44-byte RIFF header.
     */
    private fun writeWavFile(pcmFile: File, wavFile: File, rawLength: Long) {
        val totalDataLen = rawLength + 36
        val byteRate = sampleRate * 2 // SampleRate * ChannelCount * BytesPerSample = 44100 * 1 * 2

        var fis: FileInputStream? = null
        var fos: FileOutputStream? = null

        try {
            fis = FileInputStream(pcmFile)
            fos = FileOutputStream(wavFile)

            // 1. Write standard 44-byte WAV Header
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
            header[16] = 16 // Header subchunk1 size (16 for PCM)
            header[17] = 0
            header[18] = 0
            header[19] = 0
            header[20] = 1 // Audio format (1 for PCM)
            header[21] = 0
            header[22] = 1 // Channels (1 for Mono)
            header[23] = 0
            header[24] = (sampleRate and 0xff).toByte() // Sample rate
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte() // Byte rate
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = 2 // Block align (Channels * BytesPerSample = 1 * 2)
            header[33] = 0
            header[34] = 16 // Bits per sample (16 bit PCM)
            header[35] = 0
            header[36] = 'd'.toByte() // data chunk header
            header[37] = 'a'.toByte()
            header[38] = 't'.toByte()
            header[39] = 'a'.toByte()
            header[40] = (rawLength and 0xff).toByte() // Data size length
            header[41] = ((rawLength shr 8) and 0xff).toByte()
            header[42] = ((rawLength shr 16) and 0xff).toByte()
            header[43] = ((rawLength shr 24) and 0xff).toByte()

            fos.write(header, 0, 44)

            // 2. Stream raw PCM audio content straight into WAV body
            val buffer = ByteArray(2048)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                fos.write(buffer, 0, bytesRead)
            }

        } catch (e: Exception) {
            Log.e(TAG, "WAV write failed: ${e.message}")
        } finally {
            fis?.close()
            fos?.close()
        }
    }
}
