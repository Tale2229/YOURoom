package com.example.youroom.ui

import androidx.compose.ui.geometry.Offset

/** A page reflected around a diagonal crease. All values are viewport pixels.
 * The stationary front, folded back and revealed page share exactly one crease;
 * there are no independently moving text blocks or a white intermediate frame. */
internal data class PoetryPageFold(
    val front: List<Offset>, val back: List<Offset>, val crease: List<Offset>,
    val normal: Offset, val distance: Float,
) {
    fun reflect(point: Offset): Offset {
        val amount=2f*(normal.x*point.x+normal.y*point.y-distance)/normal.getDistanceSquared()
        return point-normal*amount
    }
}

internal fun poetryPageFold(width: Float,height: Float,progress: Float,slope: Float,direction: Int): PoetryPageFold {
    require(width>0f && height>0f && width.isFinite() && height.isFinite())
    require(progress.isFinite() && slope.isFinite() && direction in listOf(-1,1))
    val s=slope.coerceIn(-.65f,.65f)
    val normal=Offset(if(direction==1) 1f else -1f,s)
    val corners=listOf(Offset.Zero,Offset(width,0f),Offset(width,height),Offset(0f,height))
    fun projection(p: Offset)=normal.x*p.x+normal.y*p.y
    val min=corners.minOf(::projection);val max=corners.maxOf(::projection)
    val distance=max-(max-min+2f)*progress.coerceIn(0f,1f)
    fun clip(keepFront: Boolean): List<Offset> {
        val result=mutableListOf<Offset>()
        for(i in corners.indices) {
            val a=corners[i];val b=corners[(i+1)%corners.size]
            val av=projection(a)-distance;val bv=projection(b)-distance
            val insideA=if(keepFront) av<=0f else av>=0f
            val insideB=if(keepFront) bv<=0f else bv>=0f
            if(insideA) result+=a
            if(insideA!=insideB) result+=a+(b-a)*(av/(av-bv))
        }
        return result.distinct()
    }
    val front=clip(true)
    val crease=front.filter { kotlin.math.abs(projection(it)-distance)<.05f }.distinct()
    val initial=PoetryPageFold(front,emptyList(),crease,normal,distance)
    return initial.copy(back=clip(false).map(initial::reflect))
}
