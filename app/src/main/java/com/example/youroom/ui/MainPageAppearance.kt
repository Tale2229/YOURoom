package com.example.youroom.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** B's flat paper bands. Do not tint text, controls or the swipe action rail. */
internal fun mainPageRowColor(colors: ColorScheme, tinted: Boolean): Color =
    if (tinted) lerp(colors.surface, colors.primary, .037f) else colors.surface

/** Both auxiliary pages share a paper band; their headers use the next darker theme band. */
internal fun plannerUtilityBackground(colors: ColorScheme, panel: PlannerPanel): Color = when (panel) {
    PlannerPanel.RESULT -> colors.surface
    PlannerPanel.HISTORY, PlannerPanel.LEISURE -> colors.surfaceContainerLow
}

/** Fill only unused first-page space; never compress the actual settings rows. */
internal fun settingsFooterGapPx(viewport: Int, leadingInset: Int, prefixHeight: Int,
    handleHeight: Int): Int = (viewport - leadingInset - prefixHeight - handleHeight).coerceAtLeast(0)
