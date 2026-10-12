package com.example.youroom

import com.example.youroom.ui.timeMarkerLineRange
import org.junit.Assert.*
import org.junit.Test

class TimeMarkerGeometryTest {
    @Test fun guidesExtendThroughTheTransparentTouchInsetToTheFace() {
        val start = timeMarkerLineRange(true, 12f, 48, 36, 2f)
        val end = timeMarkerLineRange(false, 12f, 48, 36, 2f)
        assertEquals(2f, start.start, 0f)
        assertEquals(18f, start.endInclusive, 0f)
        assertEquals(-6f, end.start, 0f)
        assertEquals(10f, end.endInclusive, 0f)
    }

    @Test fun fractionalDensityDoesNotIntroduceAGapAtEitherEndpoint() {
        // At 420dpi: 48dp -> 126px, 36dp -> 95px. Centering rounds the
        // upper inset to 16px and leaves a 15px lower inset.
        val start = timeMarkerLineRange(true, 32f, 126, 95, 5.25f)
        val end = timeMarkerLineRange(false, 32f, 126, 95, 5.25f)
        assertEquals(48f, start.endInclusive, 0f)
        assertEquals(-15f, end.start, 0f)
    }

    @Test fun largerTextBandsStillReachTheSameCenteredRectangle() {
        assertEquals(30f, timeMarkerLineRange(true, 24f, 48, 36, 2f).endInclusive, 0f)
        assertEquals(-6f, timeMarkerLineRange(false, 24f, 48, 36, 2f).start, 0f)
    }
}
