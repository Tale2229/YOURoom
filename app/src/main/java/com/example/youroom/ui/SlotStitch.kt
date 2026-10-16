package com.example.youroom.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

internal data class StitchSpan(val start: Float, val end: Float)
internal data class StitchCorner(val x: Float, val y: Float, val dx: Float, val dy: Float, val arm: Float)
internal data class SlotStitchGeometry(val inset: Float, val width: Float, val height: Float,
    val corners: List<StitchCorner>, val horizontal: List<StitchSpan>, val vertical: List<StitchSpan>)

/** Four equal-arm corners. The stitch deliberately has no edge dashes: the four
 * open corners keep the period cells legible without turning them into framed cards. */
internal fun slotStitchGeometry(width: Float, height: Float, stroke: Float, outerGap: Float,
    armLength: Float, dashLength: Float, minimumGap: Float): SlotStitchGeometry {
    val inset=outerGap+stroke/2
    val w=(width-2*inset).coerceAtLeast(0f)
    val h=(height-2*inset).coerceAtLeast(0f)
    if (w<=0f || h<=0f) return SlotStitchGeometry(inset,w,h,emptyList(),emptyList(),emptyList())
    val arm=minOf(armLength,w/3,h/3)
    return SlotStitchGeometry(inset,w,h,listOf(
        StitchCorner(inset,inset,1f,1f,arm),StitchCorner(inset+w,inset,-1f,1f,arm),
        StitchCorner(inset+w,inset+h,-1f,-1f,arm),StitchCorner(inset,inset+h,1f,-1f,arm)),emptyList(),emptyList())
}

internal fun DrawScope.drawSlotStitches(color: Color) {
    val stroke=2.dp.toPx()
    val geometry=slotStitchGeometry(size.width,size.height,stroke,3.dp.toPx(),6.dp.toPx(),7.dp.toPx(),4.dp.toPx())
    geometry.corners.forEach { corner ->
        val path=Path().apply {
            moveTo(corner.x+corner.dx*corner.arm,corner.y)
            lineTo(corner.x,corner.y)
            lineTo(corner.x,corner.y+corner.dy*corner.arm)
        }
        drawPath(path,color,style=Stroke(stroke))
    }
}
