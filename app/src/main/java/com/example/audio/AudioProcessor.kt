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
    var selectedPreset = "Original" // Original, Studio Mic, Clear Voice, Podcast Pro, Deep Bass, Radio Broadcast, Ambient Space, Crispy Studio
    var noiseThreshold = 120f // User adjustable slider threshold

    // DSP Blocks
    private val highPassFilter = HighPassFilter(sampleRate, 85f)
    private val noiseGate = PowerfulNoiseCleaner()
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
                lowShelf.dbGain = 4.0f
                midPeak.dbGain = 4.5f
                highShelf.dbGain = 6.0f
            }
            "Clear Voice" -> {
                // High low cut, maximum clarity in vocal ranges
                lowShelf.dbGain = -6.0f
                midPeak.dbGain = 9.0f
                highShelf.dbGain = 5.0f
            }
            "Podcast Pro" -> {
                // Broadcast proximity effect, warm speaking presence
                lowShelf.dbGain = 6.0f
                midPeak.dbGain = 3.5f
                highShelf.dbGain = 3.0f
            }
            "Deep Bass" -> {
                // Deep, rich baritone boost
                lowShelf.dbGain = 9.5f
                midPeak.dbGain = -1.0f
                highShelf.dbGain = -2.0f
            }
            "Radio Broadcast" -> {
                // Lo-fi telephone-style mid-focus
                lowShelf.dbGain = -12.0f
                midPeak.dbGain = 8.5f
                highShelf.dbGain = -6.0f
            }
            "Ambient Space" -> {
                // Studio EQ + Reverb enabled during processing
                lowShelf.dbGain = 2.0f
                midPeak.dbGain = 3.5f
                highShelf.dbGain = 4.0f
            }
            "Crispy Studio" -> {
                // Dynamic high frequency boost and crisp presence for vocal air and maximum professional sizzle
                lowShelf.dbGain = -2.0f
                midPeak.dbGain = 7.0f
                highShelf.dbGain = 11.5f
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
        // 1. De-Rumble (High Pass Filter)
        if (isDeRumbleEnabled) {
            highPassFilter.processInPlace(buffer)
        }

        // 2. Intelligent Adaptive Noise Cleaner
        if (isNoiseGateEnabled) {
            noiseGate.threshold = noiseThreshold
            noiseGate.processInPlace(buffer)
        }

        // 3. 3-Band Studio Equalizer (Low-Shelf, Mid-Peak, High-Shelf)
        for (i in buffer.indices) {
            var sample = buffer[i].toFloat()
            sample = lowShelf.process(sample)
            sample = midPeak.process(sample)
            sample = highShelf.process(sample)
            buffer[i] = sample.coerceIn(-32768f, 32767f).toInt().toShort()
        }

        // 4. Dynamic Range Vocal Compressor
        if (isCompressorEnabled) {
            compressor.processInPlace(buffer)
        }

        // 5. Spacious Reverb (For "Ambient Space" Preset)
        if (selectedPreset == "Ambient Space") {
            reverb.processInPlace(buffer)
        }

        return buffer
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

        fun processInPlace(buffer: ShortArray) {
            for (i in buffer.indices) {
                val x = buffer[i].toFloat()
                val y = alpha * (lastY + x - lastX)
                lastX = x
                lastY = y
                buffer[i] = y.coerceIn(-32768f, 32767f).toInt().toShort()
            }
        }

        fun reset() {
            lastX = 0f
            lastY = 0f
        }
    }

    class PowerfulNoiseCleaner {
        var threshold = 120f
        var noiseReductionAmount = 0.90f // 90% background static reduction
        private var noiseFloor = 100f
        private var currentGain = 1.0f

        fun processInPlace(buffer: ShortArray) {
            if (buffer.isEmpty()) return

            var sumSquare = 0.0
            for (i in buffer.indices) {
                val s = buffer[i].toDouble()
                sumSquare += s * s
            }
            val rms = sqrt(sumSquare / buffer.size).toFloat()

            // Adaptively track the background room noise floor
            if (rms < noiseFloor) {
                noiseFloor = noiseFloor * 0.92f + rms * 0.08f
            } else {
                noiseFloor = noiseFloor * 0.998f + rms * 0.002f
            }

            // Bound the tracker safely
            if (noiseFloor < 8f) noiseFloor = 8f

            // Calculate adaptive gain target based on soft expansion knee
            val targetGain = if (rms < threshold) {
                // If it is pure silence or very quiet background, apply high downward expansion
                val quietRatio = (rms / (threshold + 0.01f)).coerceIn(0.1f, 1.0f)
                (quietRatio * (1.0f - noiseReductionAmount)).coerceIn(0.01f, 0.15f)
            } else {
                // Speech is occurring: subtract noise floor fraction to clean voice and keep it crisp
                (1.0f - (noiseFloor / (rms + 0.1f)) * noiseReductionAmount).coerceIn(0.25f, 1.0f)
            }

            // Exponentially smoothed gain to prevent clicking artifacts or pumping sounds
            val smoothingFactor = 0.12f
            for (i in buffer.indices) {
                currentGain += (targetGain - currentGain) * smoothingFactor
                buffer[i] = (buffer[i].toFloat() * currentGain).coerceIn(-32768f, 32767f).toInt().toShort()
            }
        }
    }

    class DynamicCompressor {
        var ratio = 2.4f
        var threshold = 6800f // Balanced compression threshold for speech spikes
        private var currentGain = 1.0f

        fun processInPlace(buffer: ShortArray) {
            for (i in buffer.indices) {
                val x = buffer[i].toFloat()
                val absVal = abs(x)

                val targetGain = if (absVal > threshold) {
                    val excess = absVal - threshold
                    val compressedAbsVal = threshold + excess / ratio
                    compressedAbsVal / (absVal + 0.1f)
                } else {
                    1.0f
                }

                val factor = if (targetGain < currentGain) 0.15f else 0.03f
                currentGain += (targetGain - currentGain) * factor
                buffer[i] = (x * currentGain).coerceIn(-32768f, 32767f).toInt().toShort()
            }
        }
    }

    class ReverbEffect(val sampleRate: Int) {
        private val delayMs = 120
        private val decay = 0.35f
        private val wetMix = 0.20f
        private val delayBuffer = ShortArray((sampleRate * delayMs) / 1000)
        private var writeIndex = 0

        fun processInPlace(buffer: ShortArray) {
            if (delayBuffer.isEmpty()) return
            for (i in buffer.indices) {
                val x = buffer[i].toFloat()
                val delayedSample = delayBuffer[writeIndex].toFloat()

                // Save feedback back into the circular delay buffer
                delayBuffer[writeIndex] = (x + delayedSample * decay).coerceIn(-32768f, 32767f).toInt().toShort()
                writeIndex = (writeIndex + 1) % delayBuffer.size

                // Mix original Dry and wet Reverb signals
                val mixed = x * (1f - wetMix) + delayedSample * wetMix
                buffer[i] = mixed.coerceIn(-32768f, 32767f).toInt().toShort()
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
