package com.example.youroom.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.Campus
import com.example.youroom.domain.CampusTone
import com.example.youroom.ui.theme.campusColorScheme
import com.example.youroom.ui.theme.campusThemeRoles
import kotlin.math.roundToInt

/** Restore the original single-color frame, including its original light/dark alpha.
 * Resolve from the PREVIEW tone, not the still-applied page palette.
 */
internal fun themeAvatarFrameColor(campus: Campus, tone: CampusTone, dark: Boolean): Color =
    campusThemeRoles(campusColorScheme(campus, dark, tone), tone, dark).impressionFrame

@Composable
internal fun ThemeAvatarFrame(campus: Campus, tone: CampusTone, dark: Boolean, modifier: Modifier) {
    val target = remember(campus, tone, dark) {
        themeAvatarFrameColor(campus, tone, dark)
    }
    // Retarget from the current color during rapid arrows or interrupted capture.
    val color by animateColorAsState(target, tween(180, easing = FastOutSlowInEasing), label = "avatar frame color")
    Canvas(modifier) {
        val ring = Path().apply {
            fillType = PathFillType.EvenOdd
            addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height), CornerRadius(14.dp.toPx())))
            val opening = themeAvatarPixelSquare(size.width.roundToInt(), size.width.roundToInt(),
                size.height.roundToInt(), 75.dp.roundToPx())
            addRoundRect(RoundRect(Rect(opening.left.toFloat(), opening.top.toFloat(),
                (opening.left+opening.edge).toFloat(), (opening.top+opening.edge).toFloat()), CornerRadius(4.dp.toPx())))
        }
        drawPath(ring, color)
    }
}
