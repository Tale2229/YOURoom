package com.example.youroom

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import com.example.youroom.ui.theme.YOURoomShapes
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeTooltipShapeTest {
    @Test fun theSharedTooltipShapeUsesFourDpSmallCornersAtEveryDensity() {
        val shape = YOURoomShapes.small as CornerBasedShape
        for (scale in listOf(1f, 2.625f, 3f)) {
            val density = Density(scale)
            for (corner in listOf(shape.topStart, shape.topEnd, shape.bottomStart, shape.bottomEnd)) {
                assertEquals(4f * scale, corner.toPx(Size(200f * scale, 36f * scale), density), .01f)
            }
        }
    }
}
