package com.example.youroom.domain

import kotlin.math.abs

object CampusSwipe {
    const val minimumDistanceDp = 56f
    const val horizontalDominance = 1.5f

    fun target(campus: Campus, deltaXDp: Float, deltaYDp: Float): Campus? {
        if (!deltaXDp.isFinite() || !deltaYDp.isFinite()) return null
        if (abs(deltaXDp) < minimumDistanceDp || abs(deltaXDp) <= abs(deltaYDp) * horizontalDominance) return null
        return when {
            campus == Campus.XITUCHENG && deltaXDp < 0 -> Campus.SHAHE
            campus == Campus.SHAHE && deltaXDp > 0 -> Campus.XITUCHENG
            else -> null
        }
    }
}
