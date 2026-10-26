package com.example.youroom.ui

import kotlin.math.roundToInt

/** Match Compose's nested integer-pixel centering: frame first, artwork second.
 * Centering directly in a wider Canvas can differ by half a pixel. Keeping this
 * square identical to the frame opening prevents page-colored right-edge seams.
 */
internal data class ThemeAvatarPixelSquare(val left: Int, val top: Int, val edge: Int)

internal fun themeAvatarPixelSquare(
    canvasWidth: Int, frameWidth: Int, frameHeight: Int, artworkEdge: Int,
): ThemeAvatarPixelSquare = ThemeAvatarPixelSquare(
    left = ((canvasWidth-frameWidth)/2f).roundToInt() + ((frameWidth-artworkEdge)/2f).roundToInt(),
    top = ((frameHeight-artworkEdge)/2f).roundToInt(),
    edge = artworkEdge,
)
