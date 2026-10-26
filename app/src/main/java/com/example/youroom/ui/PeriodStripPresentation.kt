package com.example.youroom.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.*
import com.example.youroom.domain.*
import kotlin.math.roundToInt

internal val LocalPeriodStripPreferences=staticCompositionLocalOf { PeriodStripPreferences() }

@Composable
internal fun PeriodBarsRow(slots: List<LessonSlot>, mode: PeriodStripLayout,
    modifier: Modifier=Modifier, content: @Composable () -> Unit) {
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    Layout(content,modifier) { children,constraints ->
        val bars=PeriodStripGeometry.bars(slots,constraints.maxWidth.toFloat(),3.dp.roundToPx().toFloat(),mode)
        val placed=children.mapIndexed { i,child ->
            val bar=bars.getOrNull(i)
            child.measure(Constraints.fixedWidth(if(bar==null) 0 else (bar.end.roundToInt()-bar.start.roundToInt()).coerceAtLeast(0)))
        }
        layout(constraints.maxWidth,placed.maxOfOrNull { it.height } ?: 0) {
            placed.forEachIndexed { i,p ->
                val bar=bars.getOrNull(i)
                val x=if(bar==null) 0 else if(rtl) constraints.maxWidth-bar.end.roundToInt() else bar.start.roundToInt()
                p.place(x,0)
            }
        }
    }
}

