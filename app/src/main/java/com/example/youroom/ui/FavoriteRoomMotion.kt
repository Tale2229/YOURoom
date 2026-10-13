package com.example.youroom.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import com.example.youroom.domain.Classroom
import com.example.youroom.domain.RoomMatch
import com.example.youroom.domain.RoomProfile
import kotlinx.coroutines.flow.first

internal object FavoriteMotionTiming {
    const val Close = 260
    const val Exit = 220
    const val Move = 280
    const val Enter = 220
}
internal enum class FavoriteMotionPhase { IDLE, CLOSE, EXIT, WAIT, MOVE, ENTER }
private data class FavoriteMove(val room: Classroom, val oldRooms: List<RoomMatch>, val favorite: Boolean)

/** Keep the original ordering until the tapped room is invisible, then let only
 * its neighbours move. The room jumps to its new position at zero alpha. */
internal class FavoriteRoomMotion {
    internal var phase by mutableStateOf(FavoriteMotionPhase.IDLE)
        private set
    private var move by mutableStateOf<FavoriteMove?>(null)
    val alpha = Animatable(1f)
    val busy: Boolean get() = move != null
    val roomId: String? get() = move?.room?.id
    fun rooms(current: List<RoomMatch>): List<RoomMatch> =
        move?.takeIf { phase in listOf(FavoriteMotionPhase.CLOSE, FavoriteMotionPhase.EXIT, FavoriteMotionPhase.WAIT) }?.oldRooms ?: current
    fun start(room: Classroom, rooms: List<RoomMatch>, profiles: Map<String, RoomProfile>): Boolean {
        if (busy) return false
        move = FavoriteMove(room, rooms, !(profiles[room.id]?.favorite ?: false))
        phase = FavoriteMotionPhase.CLOSE
        return true
    }

    @Composable
    fun Run(pending: Boolean, profiles: Map<String, RoomProfile>, active: Boolean, context: Any?) {
        val latestPending by rememberUpdatedState(pending)
        val latestProfiles by rememberUpdatedState(profiles)
        val request = move
        val movementClock = remember { Animatable(0f) }
        LaunchedEffect(request, active, context) {
            if (request == null) return@LaunchedEffect
            try {
                if (!active) return@LaunchedEffect
                alpha.snapTo(1f)
                // Keep the row visible while its rail returns smoothly, instead
                // of treating a busy favorite operation as an inactive campus.
                movementClock.snapTo(0f)
                movementClock.animateTo(1f, tween(FavoriteMotionTiming.Close))
                phase = FavoriteMotionPhase.EXIT
                alpha.animateTo(0f, tween(FavoriteMotionTiming.Exit))
                phase = FavoriteMotionPhase.WAIT
                // Wait for the current background search, not an old retained list.
                // Do not cut off a legitimate slow search and expose the old reorder.
                snapshotFlow { !latestPending }.first { it }
                // Profile writes are optimistic/synchronous; a rejected or reverted
                // update restores the latest list rather than leaving an invisible row.
                if ((latestProfiles[request.room.id]?.favorite ?: false) != request.favorite) return@LaunchedEffect
                phase = FavoriteMotionPhase.MOVE
                // Give LazyColumn a frame to apply the new order before timing
                // its neighbours' placement, so the fade-in cannot lead them.
                withFrameNanos { }
                movementClock.snapTo(0f)
                movementClock.animateTo(1f, tween(FavoriteMotionTiming.Move))
                phase = FavoriteMotionPhase.ENTER
                alpha.animateTo(1f, tween(FavoriteMotionTiming.Enter))
            } finally {
                // No deferred data write: switching page/date or cancelling an animation
                // cannot lose a favorite operation or apply it to a different campus.
                if (move === request) { move = null; phase = FavoriteMotionPhase.IDLE }
                alpha.snapTo(1f)
            }
        }
    }
}
