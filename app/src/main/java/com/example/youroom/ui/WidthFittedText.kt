package com.example.youroom.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit

/** Keep complete identifiers / time ranges inside their allotted column, without wrapping. */
@Composable
internal fun WidthFittedText(text: String, modifier: Modifier, style: TextStyle,
    maxLines: Int = 1, textAlign: TextAlign = TextAlign.Start) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val available = with(density) { maxWidth.toPx() }
        val fitted = remember(text, style, available, density) {
            val widest = text.lineSequence().maxOf { line ->
                measurer.measure(line, style, maxLines = 1, softWrap = false).size.width
            }
            val scale = (available / widest.coerceAtLeast(1)).coerceIn(.01f, 1f)
            style.copy(fontSize = style.fontSize * scale,
                lineHeight = if (style.lineHeight == TextUnit.Unspecified) style.lineHeight else style.lineHeight * scale,
                letterSpacing = if (style.letterSpacing == TextUnit.Unspecified) style.letterSpacing else style.letterSpacing * scale)
        }
        Text(text, Modifier.fillMaxWidth(), style = fitted, maxLines = maxLines,
            softWrap = false, textAlign = textAlign)
    }
}
