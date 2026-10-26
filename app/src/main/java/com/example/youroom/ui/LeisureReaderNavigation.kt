package com.example.youroom.ui

import kotlin.math.abs
import kotlin.math.hypot

/** One deliberate horizontal drag changes one item; short taps and vertical scroll do not. */
internal fun leisureSwipeDirection(distance: Float,width: Float,minimum: Float): Int {
    return poetrySwipeDirection(distance,0f,width,minimum)
}

/** Diagonal thumb arcs count; near-vertical motion remains a reading scroll. */
internal fun poetrySwipeDirection(dx: Float,dy: Float,width: Float,minimum: Float): Int {
    if(!dx.isFinite() || !dy.isFinite() || !width.isFinite() || !minimum.isFinite() || width<=0f) return 0
    val threshold=maxOf(minimum.coerceAtLeast(1f),width*.16f)
    if(abs(dx)<threshold*.65f || abs(dy)>abs(dx)*2f || hypot(dx,dy*.55f)<threshold) return 0
    return if(dx<0f) 1 else -1
}

internal enum class PoetryDragIntent { UNDECIDED, PAGE, SCROLL }
internal fun poetryDragIntent(dx: Float,dy: Float,slop: Float): PoetryDragIntent {
    if(!dx.isFinite() || !dy.isFinite()) return PoetryDragIntent.SCROLL
    if(abs(dy)>slop && abs(dy)>abs(dx)*2f) return PoetryDragIntent.SCROLL
    if(abs(dx)>slop*.7f && abs(dx)>=abs(dy)*.5f) return PoetryDragIntent.PAGE
    return PoetryDragIntent.UNDECIDED
}
