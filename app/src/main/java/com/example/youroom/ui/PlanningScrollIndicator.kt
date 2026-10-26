package com.example.youroom.ui

internal data class PlanningScrollIndicator(val sizeFraction: Float, val positionFraction: Float)

/** Equal-width summary cards: include both list paddings and the inter-card gaps.
 * Position is logical (start -> end); Compose mirrors the thumb in RTL. */
internal fun planningScrollIndicator(count: Int, itemWidth: Int, spacing: Int, padding: Int,
    viewport: Int, firstIndex: Int, firstOffset: Int): PlanningScrollIndicator? {
    if(count<=0 || itemWidth<=0 || viewport<=0) return null
    val stride=itemWidth.toLong()+spacing
    val content=count*itemWidth.toLong()+(count-1L)*spacing+padding
    if(content<=viewport) return null
    val offset=firstIndex*stride+firstOffset
    return PlanningScrollIndicator((viewport.toDouble()/content).toFloat().coerceIn(0f,1f),
        (offset.toDouble()/(content-viewport)).toFloat().coerceIn(0f,1f))
}
