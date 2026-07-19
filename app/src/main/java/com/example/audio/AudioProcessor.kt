package com.example.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class AudioProcessor(val sampleRate: Float = 44100f) {

    // DSP State Toggles
    var isDeRumbleEnabled = true
    var isNoiseGateEnabled = true
    var isCompressorEnabled = true
    var selectedPreset = "Original" // Original, Studio, Clear, Podcast, Deep Bass, Radio, Ambient
    var noiseThreshold = 120f // User adjustable slider threshold

    // DSP Blocks
    private val highPassFilter = HighPassFilter(sampleRate, 85f)
    private val noiseGate = NoiseGate()
    private val compressor = DynamicCompressor()
    private val reverb = ReverbEffect(sampleRate.toInt())

    // 3-Band Parametric Equalizer
    private val lowShelf = BiquadFilter(BiquadFilter.Type.LOW_SHELF, sampleRate, 200f, 0.707f, 0f)
    private val midPeak = BiquadFilter(BiquadFilter.Type.PEAKING, sampleRate, 3000f, 1.0f, 0f)
    private val highShelf = BiquadFilter(BiquadFilter.Type.HIGH_SHELF, sampleRate, 8000f, 0.707f, 0f)

    init {
        updatePresetCoefficients()
    }

    fun updatePresetCoefficients() {
        // Clear all filter states to prevent transition clicks
        lowShelf.reset()
        midPeak.reset()
        highShelf.reset()
        reverb.reset()
        highPassFilter.reset()

        when (selectedPreset) {
            "Original" -> {
                lowShelf.dbGain = 0f
                midPeak.dbGain = 0f
                highShelf.dbGain = 0f
            }
            "Studio Mic" -> {
                // Warm lows, polished vocal clarity, smooth high-end air
                lowShelf.dbGain = 3.5f
                midPeak.dbGain = 4.0f
                highShelf.dbGain = 5.5f
            }
            "Clear Voice" -> {
                // High low cut, maximum clarity in vocal ranges
                lowShelf.dbGain = -7.0f
                midPeak.dbGain = 8.0f
                highShelf.dbGain = 4.0f
            }
            "Podcast Pro" -> {
                // Broadcast proximity effect, warm speaking presence
                lowShelf.dbGain = 5.0f
                midPeak.dbGain = 3.0f
                highShelf.dbGain = 2.0f
            }
            "Deep Bass" -> {
                // Deep, rich baritone boost
                lowShelf.dbGain = 8.5f
                midPeak.dbGain = -1.5f
                highShelf.dbGain = -3.0f
            }
            "Radio Broadcast" -> {
                // Lo-fi telephone-style mid-focus
                lowShelf.dbGain = -15.0f
                midPeak.dbGain = 7.5f
                highShelf.dbGain = -8.0f
            }
            "Ambient Space" -> {
                // Studio EQ + Reverb enabled during processing
                lowShelf.dbGain = 2.0f
                midPeak.dbGain = 3.5f
                highShelf.dbGain = 4.0f
            }
        }
        lowShelf.recalculateCoefficients()
        midPeak.recalculateCoefficients()
        highShelf.recalculateCoefficients()
    }

    /**
     * Processes a block of 16-bit PCM samples in place.
     */
    fun process(buffer: ShortArray): ShortArray {
        var workBuffer = ShortArray(buffer.size)
        buffer.copyInto(workBuffer)

        // 1. De-Rumble (High Pass Filter)
        if (isDeRumbleEnabled) {
            val out = ShortArray(workBuffer.size)
            highPassFilter.process(workBuffer, out)
            workBuffer = out
        }

        // 2. Intelligent Noise Gate
        if (isNoiseGateEnabled) {
            val out = ShortArray(workBuffer.size)
            noiseGate.threshold = noiseThreshold
            noiseGate.process(workBuffer, out)
            workBuffer = out
        }

        // 3. 3-Band Studio Equalizer (Low-Shelf, Mid-Peak, High-Shelf)
        for (i in workBuffer.indices) {
            var sample = workBuffer[i].toFloat()
            sample = lowShelf.process(sample)
            sample = midPeak.process(sample)
            sample = highShelf.process(sample)
            workBuffer[i] = sample.coerceIn(-32768f, 32767f).toInt().toShort()
        }

        // 4. Dynamic Range Vocal Compressor
        if (isCompressorEnabled) {
            val out = ShortArray(workBuffer.size)
            compressor.process(workBuffer, out)
            workBuffer = out
        }

        // 5. Spacious Reverb (For "Ambient Space" Preset)
        if (selectedPreset == "Ambient Space") {
            val out = ShortArray(workBuffer.size)
            reverb.process(workBuffer, out)
            workBuffer = out
        }

        return workBuffer
    }

    // ==========================================
    // DSP Blocks Implementations
    // ==========================================

    class HighPassFilter(private val sampleRate: Float, private val cutoffFreq: Float) {
        private var lastX = 0f
        private var lastY = 0f
        private val alpha: Float

        init {
            val dt = 1.0f / sampleRate
            val rc = 1.0f / (2.0f * PI.toFloat() * cutoffFreq)
            alpha = rc / (rc + dt)
        }

        fun process(input: ShortArray, output: ShortArray) {
            for (i in input.indices) {
                val x = input[i].toFloat()
                val y = alpha * (lastY + x - lastX)
                lastX = x
                lastY = y
                output[i] = y.coerceIn(-32768f, 32767f).toInt().toShort()
            }
        }

        fun reset() {
            lastX = 0f
            lastY = 0f
        }
    }

    class NoiseGate {
        var threshold = 120f
        var reductionDb = -28f // Deep background suppression
        private var currentGain = 1.0f

        fun process(input: ShortArray, output: ShortArray) {
            var sumSquare = 0.0
            for (i in input.indices) {
                val s = input[i].toDouble()
                sumSquare += s * s
            }
            val rms = sqrt(sumSquare / input.size).toFloat()

            // If RMS is below threshold, smoothly apply gating attenuation
            val targetGain = if (rms < threshold) {
                10.0f.pow(reductionDb / 20.0f)
            } else {
                1.0f
            }

            // Exponentially smoothed gain to prevent clicking artifacts
            val smoothingFactor = 0.08f 
            for (i in input.indices) {
                currentGain += (targetGain - currentGain) * smoothingFactor
                output[i] = (input[i].toFloat() * currentGain).coerceIn(-32768f, 32767f).toInt().toShort()
            }
        }
    }

    class DynamicCompressor {
        var ratio = 2.2f
        var threshold = 7500f // Threshold above which we compress peak levels
        private var currentGain = 1.0f

        fun process(input: ShortArray, output: ShortArray) {
            for (i in input.indices) {
                val x = input[i].toFloat()
                val absVal = abs(x)

                val targetGain = if (absVal > threshold) {
                    val excess = absVal - threshold
                    val compressedAbsVal = threshold + excess / ratio
                    compressedAbsVal / absVal
                } else {
                    1.0f
                }

                // Smooth attack vs release times
                val factor = if (targetGain < currentGain) 0.12f else 0.02f
                currentGain += (targetGain - currentGain) * factor
                output[i] = (x * currentGain).coerceIn(-32768f, 32767f).toInt().toShort()
            }
        }
    }

    class ReverbEffect(val sampleRate: Int) {
        private val delayMs = 150
        private val decay = 0.38f // Feedback damping factor
        private val wetMix = 0.22f // Reverb blend
        private val delayBuffer = ShortArray((sampleRate * delayMs) / 1000)
        private var writeIndex = 0

        fun process(input: ShortArray, output: ShortArray) {
            for (i in input.indices) {
                val x = input[i].toFloat()
                val delayedSample = delayBuffer[writeIndex].toFloat()

                // Save feedback back into the delay circular buffer
                delayBuffer[writeIndex] = (x + delayedSample * decay).coerceIn(-32768f, 32767f).toInt().toShort()
                writeIndex = (writeIndex + 1) % delayBuffer.size

                // Mix original Dry and wet Reverb signals
                val mixed = x * (1f - wetMix) + delayedSample * wetMix
                output[i] = mixed.coerceIn(-32768f, 32767f).toInt().toShort()
            }
        }

        fun reset() {
            delayBuffer.fill(0)
            writeIndex = 0
        }
    }

    class BiquadFilter(
        val type: Type,
        val sampleRate: Float,
        var centerFreq: Float,
        var Q: Float,
        var dbGain: Float
    ) {
        enum class Type { LOW_SHELF, PEAKING, HIGH_SHELF }

        private var b0 = 0f; private var b1 = 0f; private var b2 = 0f
        private var a0 = 0f; private var a1 = 0f; private var a2 = 0f

        private var x1 = 0f; private var x2 = 0f
        private var y1 = 0f; private var y2 = 0f

        init {
            recalculateCoefficients()
        }

        fun recalculateCoefficients() {
            val A = 10.0f.pow(dbGain / 40.0f)
            val w0 = 2.0f * PI.toFloat() * centerFreq / sampleRate
            val alpha = (sin(w0.toDouble()) / (2.0 * Q)).toFloat()
            val cosW0 = cos(w0.toDouble()).toFloat()

            when (type) {
                Type.PEAKING -> {
                    b0 = 1.0f + alpha * A
                    b1 = -2.0f * cosW0
                    b2 = 1.0f - alpha * A
                    a0 = 1.0f + alpha / A
                    a1 = -2.0f * cosW0
                    a2 = 1.0f - alpha / A
                }
                Type.LOW_SHELF -> {
                    val sqrtA = sqrt(A)
                    val shelfAlpha = (sin(w0.toDouble()) / 2.0 * sqrt((A + 1.0f / A) * (1.0f / Q - 1.0f) + 2.0f)).toFloat()
                    b0 = A * ((A + 1.0f) - (A - 1.0f) * cosW0 + 2.0f * sqrtA * shelfAlpha)
                    b1 = 2.0f * A * ((A - 1.0f) - (A + 1.0f) * cosW0)
                    b2 = A * ((A + 1.0f) - (A - 1.0f) * cosW0 - 2.0f * sqrtA * shelfAlpha)
                    a0 = (A + 1.0f) + (A - 1.0f) * cosW0 + 2.0f * sqrtA * shelfAlpha
                    a1 = -2.0f * ((A - 1.0f) + (A + 1.0f) * cosW0)
                    a2 = (A + 1.0f) + (A - 1.0f) * cosW0 - 2.0f * sqrtA * shelfAlpha
                }
                Type.HIGH_SHELF -> {
                    val sqrtA = sqrt(A)
                    val shelfAlpha = (sin(w0.toDouble()) / 2.0 * sqrt((A + 1.0f / A) * (1.0f / Q - 1.0f) + 2.0f)).toFloat()
                    b0 = A * ((A + 1.0f) + (A - 1.0f) * cosW0 + 2.0f * sqrtA * shelfAlpha)
                    b1 = -2.0f * A * ((A - 1.0f) + (A + 1.0f) * cosW0)
                    b2 = A * ((A + 1.0f) + (A - 1.0f) * cosW0 - 2.0f * sqrtA * shelfAlpha)
                    a0 = (A + 1.0f) - (A - 1.0f) * cosW0 + 2.0f * sqrtA * shelfAlpha
                    a1 = 2.0f * ((A - 1.0f) - (A + 1.0f) * cosW0)
                    a2 = (A + 1.0f) - (A - 1.0f) * cosW0 - 2.0f * sqrtA * shelfAlpha
                }
            }
        }

        fun process(sample: Float): Float {
            val x0 = sample
            val y0 = (b0 / a0) * x0 + (b1 / a0) * x1 + (b2 / a0) * x2 - (a1 / a0) * y1 - (a2 / a0) * y2

            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0

            return y0
        }

        fun reset() {
            x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
        }
    }
}
