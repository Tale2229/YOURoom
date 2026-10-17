package com.example.youroom.domain

import kotlin.math.PI
import kotlin.math.sin

/** One right-to-left pulse. Indices are physical left-to-right, not lesson numbers. */
object PeriodWave {
    const val startDelayMillis = 250L
    const val durationMillis = 600 // Previous 450ms pulse at 0.75x speed.
    const val maximumExtensionDp = 3f

    fun amplitude(progress: Float, visualIndex: Int, count: Int): Float {
        if (!progress.isFinite() || progress <= 0f || progress >= 1f ||
            count <= 0 || visualIndex !in 0 until count) return 0f
        // About three adjacent bars overlap, including for sparse responses.
        val pulse = 3f / (count + 2f)
        val delay = if (count == 1) 0f else (1f - pulse) * (count - 1 - visualIndex) / (count - 1)
        return pulseValue(progress,delay,pulse)
    }

    /** Normalized physical center, 0 = leftmost bar, 1 = rightmost bar. */
    fun spatialAmplitude(progress: Float, position: Float, count: Int): Float {
        if(!progress.isFinite() || progress<=0 || progress>=1 || !position.isFinite() || count<=0) return 0f
        val pulse=3f/(count+2f)
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
