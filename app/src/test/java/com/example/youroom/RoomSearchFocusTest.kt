package com.example.youroom

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.youroom.ui.RoomSearchFocus
import org.junit.Assert.*
import org.junit.Test

class RoomSearchFocusTest {
    @Test fun onlyOutsideTheFocusedInputNeedsClearing() {
        val focus = RoomSearchFocus(); val token = Any()
        focus.position(token, Rect(10f, 20f, 110f, 60f))
        assertFalse(focus.isOutside(Offset.Zero))
        focus.focus(token, true)
        assertFalse(focus.isOutside(Offset(50f, 40f)))
        assertTrue(focus.isOutside(Offset(50f, 80f)))
        focus.focus(token, false)
        assertFalse(focus.isOutside(Offset.Zero))
    }

    @Test fun scrollingUpdatesTheFocusedInputsBoundsWithoutStateRecomposition() {
        val focus = RoomSearchFocus(); val token = Any()
        focus.position(token, Rect(10f, 20f, 110f, 60f)); focus.focus(token, true)
        focus.position(token, Rect(10f, 80f, 110f, 120f))
        assertTrue(focus.isOutside(Offset(50f, 40f)))
        assertFalse(focus.isOutside(Offset(50f, 100f)))
    }

    @Test fun hiddenCampusCannotOverwriteOrClearTheOtherInputsFocus() {
        val focus = RoomSearchFocus(); val west = Any(); val shahe = Any()
        focus.position(west, Rect(10f, 20f, 110f, 60f)); focus.focus(west, true)
        focus.position(shahe, Rect(0f, 200f, 100f, 250f)); focus.focus(shahe, false)
        assertFalse(focus.isOutside(Offset(50f, 40f)))
        focus.remove(shahe)
        assertTrue(focus.isOutside(Offset(50f, 225f)))
    }

    @Test fun removingTheFocusedInputForgetsIt() {
        val focus = RoomSearchFocus(); val token = Any()
        focus.position(token, Rect(10f, 20f, 110f, 60f)); focus.focus(token, true)
        focus.remove(token)
        assertFalse(focus.isOutside(Offset.Zero))
    }
}
