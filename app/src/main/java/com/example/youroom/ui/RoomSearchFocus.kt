package com.example.youroom.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

// Geometry is event-only, not Compose state: scrolling must not recompose the page.
internal class RoomSearchFocus {
    private var owner: Any? = null
    private var bounds: Rect? = null
    private val fields = mutableMapOf<Any, Rect>()
    fun position(token: Any, rect: Rect) { fields[token] = rect; if (owner === token) bounds = rect }
    fun focus(token: Any, focused: Boolean) {
        if (focused) { owner = token; bounds = fields[token] }
        else if (owner === token) { owner = null; bounds = null }
    }
    fun remove(token: Any) { fields.remove(token); focus(token, false) }
    fun isOutside(position: Offset): Boolean = owner != null && bounds?.contains(position) == false
}

internal val LocalRoomSearchFocus = staticCompositionLocalOf<RoomSearchFocus?> { null }

/** Observes downs before children consume them; never takes over taps or scrolling.
 * The Android IME is a separate window, so keyboard touches never reach this boundary. */
@Composable
internal fun RoomSearchFocusBoundary(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val ownSearch = remember { RoomSearchFocus() }
    val search = LocalRoomSearchFocus.current ?: ownSearch
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val origin = remember { arrayOf(Offset.Zero) }
    CompositionLocalProvider(LocalRoomSearchFocus provides search) {
        Box(modifier.onGloballyPositioned { origin[0] = it.positionInRoot() }
            .pointerInput(search, focus, keyboard) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (search.isOutside(down.position + origin[0])) {
                        focus.clearFocus(force = true)
                        keyboard?.hide()
                    }
                }
            }) { content() }
    }
}
