package com.example.youroom.domain

/** Physical left-to-right rail geometry, shared by reveal detection and its drawing. */
object RoomActionFlip {
    const val railWidthDp = 184f
    const val buttonWidthDp = 40f
    const val paddingDp = 12f
    const val batchDelayMillis = 18L
    const val dampingRatio = .58f

    fun threshold(index: Int): Float = railWidthDp - paddingDp - index * buttonWidthDp

    fun crossings(before: Float, after: Float): List<Int> =
        if (!before.isFinite() || !after.isFinite() || after <= before) emptyList()
        else (3 downTo 0).filter { before < threshold(it) && after >= threshold(it) }

    // Bounded kinetic kick: a slow pull still flips; a fast pull has more initial
    // angular velocity and spring force, without spinning indefinitely.
    fun strength(speedDpPerSecond: Float): Float =
        (if (speedDpPerSecond.isFinite()) speedDpPerSecond else 0f).coerceIn(0f, 2600f) / 2600f
    fun initialVelocity(speedDpPerSecond: Float): Float = 150f + 450f * strength(speedDpPerSecond)
    // Halving angular velocity alone would let the old spring immediately speed
    // it up again. A quarter stiffness gives the whole same trajectory at 1/2 speed.
    fun stiffness(speedDpPerSecond: Float): Float = (75f + 120f * strength(speedDpPerSecond)) * .25f
}
