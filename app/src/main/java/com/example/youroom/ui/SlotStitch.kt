package com.example.youroom.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.floor

internal data class StitchSpan(val start: Float, val end: Float)
internal data class StitchCorner(val x: Float, val y: Float, val dx: Float, val dy: Float, val arm: Float)
internal data class SlotStitchGeometry(val inset: Float, val width: Float, val height: Float,
    val corners: List<StitchCorner>, val horizontal: List<StitchSpan>, val vertical: List<StitchSpan>)

/** Complete equal-arm L corners and an independent symmetric stitch on each edge. */
internal fun slotStitchGeometry(width: Float, height: Float, stroke: Float, outerGap: Float,
    armLength: Float, dashLength: Float, minimumGap: Float): SlotStitchGeometry {
    val inset=outerGap+stroke/2
    val w=(width-2*inset).coerceAtLeast(0f)
    val h=(height-2*inset).coerceAtLeast(0f)
    if (w<=0f || h<=0f) return SlotStitchGeometry(inset,w,h,emptyList(),emptyList(),emptyList())
    val arm=minOf(armLength,w/3,h/3)
    fun dashes(length: Float): List<StitchSpan> {
        val middle=length-2*arm
        val count=floor(((middle-minimumGap)/(dashLength+minimumGap)).toDouble()).toInt().coerceAtLeast(0)
        val gap=(middle-count*dashLength)/(count+1)
        return List(count) { index ->
            val start=arm+gap+index*(dashLength+gap)
            StitchSpan(start,start+dashLength)
        }
    }
    return SlotStitchGeometry(inset,w,h,listOf(
        StitchCorner(inset,inset,1f,1f,arm),StitchCorner(inset+w,inset,-1f,1f,arm),
        StitchCorner(inset+w,inset+h,-1f,-1f,arm),StitchCorner(inset,inset+h,1f,-1f,arm)),dashes(w),dashes(h))
}

internal fun DrawScope.drawSlotStitches(color: Color) {
    val stroke=.75.dp.toPx()
    val geometry=slotStitchGeometry(size.width,size.height,stroke,3.dp.toPx(),6.dp.toPx(),7.dp.toPx(),4.dp.toPx())
    geometry.corners.forEach { corner ->
        val path=Path().apply {
            moveTo(corner.x+corner.dx*corner.arm,corner.y)
            lineTo(corner.x,corner.y)
            lineTo(corner.x,corner.y+corner.dy*corner.arm)
        }
        drawPath(path,color,style=Stroke(stroke))
    }
    geometry.horizontal.forEach { dash ->
        for(y in listOf(geometry.inset,geometry.inset+geometry.height))
            drawLine(color,Offset(geometry.inset+dash.start,y),Offset(geometry.inset+dash.end,y),stroke)
    }
    geometry.vertical.forEach { dash ->
        for(x in listOf(geometry.inset,geometry.inset+geometry.width))
            drawLine(color,Offset(x,geometry.inset+dash.start),Offset(x,geometry.inset+dash.end),stroke)
    }
}
