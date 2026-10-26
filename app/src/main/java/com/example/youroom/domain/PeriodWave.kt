package com.example.youroom.domain

import kotlin.math.PI
import kotlin.math.sin

/** One right-to-left pulse. Indices are physical left-to-right, not lesson numbers. */
object PeriodWave {
    const val startDelayMillis = 250L
    const val durationMillis = 480
    const val maximumExtensionDp = 3f // Only above the unchanged 5dp bar; bottom stays fixed.

    /** Broader overlapping crest for dense strips; sparse strips still get a full pulse. */
    fun pulseFraction(count: Int): Float = maxOf(.32f, 3f / (count.coerceAtLeast(1) + 2f))

    fun amplitude(progress: Float, visualIndex: Int, count: Int): Float {
        if (!progress.isFinite() || progress <= 0f || progress >= 1f ||
            count <= 0 || visualIndex !in 0 until count) return 0f
        val pulse = pulseFraction(count)
        val delay = if (count == 1) 0f else (1f - pulse) * (count - 1 - visualIndex) / (count - 1)
        return pulseValue(progress,delay,pulse)
    }

    /** Normalized physical coordinate: callers may sample the entire strip, not just bar centers. */
    fun spatialAmplitude(progress: Float, position: Float, count: Int): Float {
        if(!progress.isFinite() || progress<=0 || progress>=1 || !position.isFinite() || count<=0) return 0f
        val pulse=pulseFraction(count)
        val delay=if(count==1) 0f else (1-pulse)*(1-position.coerceIn(0f,1f))
        return pulseValue(progress,delay,pulse)
    }

    private fun pulseValue(progress: Float, delay: Float, pulse: Float): Float {
        val phase = (progress - delay) / pulse
        if (phase <= 0f || phase >= 1f) return 0f
        val value = sin(PI * phase).toFloat()
        return value * value
    }

}
