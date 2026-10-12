package com.example.youroom.ui

import kotlin.math.roundToInt

/** Match the centered slot face's pixel-rounded top and bottom insets. */
internal fun timeMarkerLineRange(start: Boolean, bandHeight: Float, targetHeight: Int,
    faceHeight: Int, capInset: Float): ClosedFloatingPointRange<Float> {
    val topInset = ((targetHeight - faceHeight) / 2f).roundToInt()
    val bottomInset = targetHeight - faceHeight - topInset
    return if (start) capInset..(bandHeight + topInset) else -bottomInset.toFloat()..(bandHeight - capInset)
}
