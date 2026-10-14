package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.*
import com.example.youroom.ui.FavoriteMotionPhase
import com.example.youroom.ui.FavoriteRoomMotion
import com.example.youroom.ui.FavoriteMotionTiming
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FavoriteRoomMotionUiTest {
    @get:Rule val compose = createComposeRule()
    private val classrooms = (1..3).map { Classroom("01:2-20$it", "2-20$it", "2", 180) }
    private var profiles by mutableStateOf<Map<String, RoomProfile>>(emptyMap())
    private var pending by mutableStateOf(false)
    private var active by mutableStateOf(true)
    private var context by mutableStateOf("today")
    private var onlyFavorites by mutableStateOf(false)
    private lateinit var motion: FavoriteRoomMotion
    private var displayed = emptyList<RoomMatch>()
    private fun rooms(): List<RoomMatch> = classrooms.map { RoomMatch(it, profiles[it.id] ?: it.emptyProfile(), setOf(1)) }
        .filter { !onlyFavorites || it.profile.favorite }
        .sortedWith(compareByDescending<RoomMatch> { it.profile.favorite }.thenBy { it.classroom.name })

    private fun render() {
        compose.setContent {
            motion = remember { FavoriteRoomMotion() }
            motion.Run(pending, profiles, active, context)
            val shown = motion.rooms(rooms())
            SideEffect { displayed = shown }
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.classroom.id }) { match ->
                    val target = match.classroom.id == motion.roomId
                    Text(match.classroom.name, Modifier.fillMaxWidth().height(64.dp)
                        .then(if (target) Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
                            else Modifier.animateItem(placementSpec = androidx.compose.animation.core.tween(FavoriteMotionTiming.Move)))
                        .graphicsLayer { alpha = if (target) motion.alpha.value else 1f }
                        .testTag(match.classroom.id))
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun begin(room: Classroom, acknowledge: Boolean = true) {
        compose.runOnIdle {
            assertTrue(motion.start(room, rooms(), profiles))
            if (acknowledge) profiles = profiles + (room.id to
                (profiles[room.id] ?: room.emptyProfile()).let { it.copy(favorite = !it.favorite) })
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun phase(expected: FavoriteMotionPhase, alpha: Float? = null) {
        compose.runOnIdle {
            assertEquals(expected, motion.phase)
            alpha?.let { assertEquals(it, motion.alpha.value, .001f) }
        }
    }

    @Test fun favoriteAndUnfavoriteExitInPlaceThenNeighboursMoveThenEnterAtTheNewPosition() {
        render()
        val target = classrooms.last()
        for (favorite in listOf(true, false)) {
            val old = displayed.map { it.classroom.id }
            val oldTop = compose.onNodeWithTag(target.id).fetchSemanticsNode().boundsInRoot.top
            compose.runOnIdle { pending = true }
            begin(target)
            compose.mainClock.advanceTimeBy(64)
            phase(FavoriteMotionPhase.CLOSE, 1f)
            assertEquals(oldTop, compose.onNodeWithTag(target.id).fetchSemanticsNode().boundsInRoot.top, 1f)
            assertEquals(old, displayed.map { it.classroom.id })
            compose.mainClock.advanceTimeBy(FavoriteMotionTiming.Close + 64L)
            phase(FavoriteMotionPhase.EXIT)
            assertEquals(oldTop, compose.onNodeWithTag(target.id).fetchSemanticsNode().boundsInRoot.top, 1f)
            compose.runOnIdle { assertTrue(motion.alpha.value in .01f.. .99f) }
            compose.mainClock.advanceTimeBy(FavoriteMotionTiming.Exit + 64L)
            phase(FavoriteMotionPhase.WAIT, 0f)
            assertEquals(old, displayed.map { it.classroom.id })
            compose.runOnIdle { pending = false }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            phase(FavoriteMotionPhase.MOVE, 0f)
            assertEquals(if (favorite) target.id else classrooms.first().id, displayed.first().classroom.id)
            compose.mainClock.advanceTimeBy(96)
            phase(FavoriteMotionPhase.MOVE, 0f)
            compose.mainClock.advanceTimeBy(FavoriteMotionTiming.Move + 32L)
            phase(FavoriteMotionPhase.ENTER)
            compose.runOnIdle { assertTrue(motion.alpha.value > 0f && motion.alpha.value < 1f) }
            compose.mainClock.advanceTimeBy(FavoriteMotionTiming.Enter + 64L)
            phase(FavoriteMotionPhase.IDLE, 1f)
            assertEquals(favorite, profiles.getValue(target.id).favorite)
        }
    }

    @Test fun changingContextOrLeavingThePageCancelsOnlyTheVisualsNotTheFavorite() {
        render()
        val target = classrooms.last()
        for (cancelAt in listOf(32L, FavoriteMotionTiming.Close + 64L,
            FavoriteMotionTiming.Close + FavoriteMotionTiming.Exit + 96L,
            FavoriteMotionTiming.Close + FavoriteMotionTiming.Exit + FavoriteMotionTiming.Move + 96L)) {
            begin(target)
            compose.mainClock.advanceTimeBy(cancelAt)
            compose.runOnIdle { context += "next" }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            phase(FavoriteMotionPhase.IDLE, 1f)
            assertEquals(rooms().map { it.classroom.id }, displayed.map { it.classroom.id })
        }
        begin(target)
        compose.mainClock.advanceTimeBy(64)
        val saved = profiles
        compose.runOnIdle { active = false }
        compose.mainClock.advanceTimeByFrame()
        phase(FavoriteMotionPhase.IDLE, 1f)
        assertEquals(saved, profiles)
    }

    @Test fun removingTheLastFavoriteByItsFilterDoesNotReinsertTheDisappearedRoom() {
        profiles = mapOf(classrooms.last().id to classrooms.last().emptyProfile().copy(favorite = true))
        onlyFavorites = true
        render()
        begin(classrooms.last())
        compose.mainClock.advanceTimeBy(FavoriteMotionTiming.Close + FavoriteMotionTiming.Exit + 64L)
        phase(FavoriteMotionPhase.MOVE, 0f)
        assertTrue(displayed.isEmpty())
        compose.mainClock.advanceTimeBy(FavoriteMotionTiming.Move + FavoriteMotionTiming.Enter + 64L)
        phase(FavoriteMotionPhase.IDLE, 1f)
        compose.onNodeWithTag(classrooms.last().id).assertDoesNotExist()
    }

    @Test fun unacknowledgedUpdateRestoresTheRowAndRepeatedClicksCannotOverwriteTheActiveTransition() {
        render()
        begin(classrooms.last(), acknowledge = false)
        compose.runOnIdle { assertFalse(motion.start(classrooms.first(), rooms(), profiles)) }
        compose.mainClock.autoAdvance = true
        compose.waitUntil(4_000) { !motion.busy }
        phase(FavoriteMotionPhase.IDLE, 1f)
        assertEquals(classrooms.map { it.id }, displayed.map { it.classroom.id })
        assertTrue(profiles.isEmpty())
    }

    @Test fun disappearanceUsesTheShorterBudgetWithoutChangingCloseMoveOrEnterTiming() {
        assertEquals(140, FavoriteMotionTiming.Exit)
        assertEquals(260, FavoriteMotionTiming.Close)
        assertEquals(280, FavoriteMotionTiming.Move)
        assertEquals(220, FavoriteMotionTiming.Enter)
        render()
        for (favorite in listOf(true, false)) {
            compose.runOnIdle { pending = true }
            begin(classrooms.last())
            compose.mainClock.advanceTimeBy(FavoriteMotionTiming.Close + FavoriteMotionTiming.Exit + 48L)
            phase(FavoriteMotionPhase.WAIT, 0f)
            assertEquals(favorite, profiles.getValue(classrooms.last().id).favorite)
            compose.runOnIdle { pending = false }
            compose.mainClock.advanceTimeBy(700)
            phase(FavoriteMotionPhase.IDLE, 1f)
        }
        compose.mainClock.autoAdvance = true
    }
}
