package com.example.youroom

import android.os.Looper
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.youroom.domain.*
import com.example.youroom.ui.YouRoomState
import com.example.youroom.ui.rememberClassroomSearch
import com.example.youroom.ui.theme.YouRoomTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ClassroomSearchUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun filteringRetainsTheCurrentListUntilTheLatestQueryCompletes() {
        val date = LocalDate.of(2026, 10, 12)
        val rooms = listOf(Classroom("01:2-201", "2-201", "2", 60), Classroom("01:2-301", "2-301", "2", 100))
        val snapshot = ClassroomSnapshot("01", date, Instant.EPOCH,
            listOf(LessonSlot(1, LocalTime.of(8, 0), LocalTime.of(8, 45), rooms)))
        val gate = CountDownLatch(1)
        val started = AtomicBoolean(false)
        val block = AtomicBoolean(false)
        val profiles = object : AbstractMap<String, RoomProfile>() {
            override val entries = emptySet<Map.Entry<String, RoomProfile>>()
            override fun get(key: String): RoomProfile? {
                if (block.get()) { started.set(true); check(gate.await(10, TimeUnit.SECONDS)) }
                return null
            }
        }
        var filters by mutableStateOf(SearchFilters())
        compose.setContent {
            val presentation = rememberClassroomSearch(YouRoomState(snapshot = snapshot, today = date,
                filters = filters, profiles = profiles), enabled = true)
            Text("${presentation.pending}|${presentation.result.rooms.joinToString { it.classroom.name }}",
                Modifier.testTag("search_state"))
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("false|2-201, 2-301")).fetchSemanticsNodes().isNotEmpty()
        }
        try {
            block.set(true)
            compose.runOnIdle { filters = filters.copy(query = "301") }
            compose.waitUntil(10_000) { started.get() }
            compose.onNodeWithTag("search_state").assertTextEquals("true|2-201, 2-301")
            compose.runOnIdle { filters = filters.copy(query = "201") }
            compose.onNodeWithTag("search_state").assertTextEquals("true|2-201, 2-301")
            gate.countDown()
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasText("false|2-201")).fetchSemanticsNodes().isNotEmpty()
            }
        } finally { gate.countDown() }
    }

    @Test fun retainedResultsNeverCrossSnapshotsCampusesOrStaleDates() {
        val date = LocalDate.of(2026, 10, 12)
        val room = Classroom("01:2-201", "2-201", "2", 60)
        val snapshot = ClassroomSnapshot("01", date, Instant.EPOCH,
            listOf(LessonSlot(1, LocalTime.of(8, 0), LocalTime.of(8, 45), listOf(room))))
        var state by mutableStateOf(YouRoomState(snapshot = snapshot, today = date))
        var enabled by mutableStateOf(true)
        compose.setContent {
            val presentation = rememberClassroomSearch(state, enabled)
            Text("${presentation.pending}|${presentation.result.rooms.size}", Modifier.testTag("search_state"))
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("false|1")).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { enabled = false; state = state.copy(filters = SearchFilters(query = "201")) }
        compose.onNodeWithTag("search_state").assertTextEquals("true|1")
        compose.runOnIdle { state = state.copy(today = date.plusDays(1)) }
        compose.onNodeWithTag("search_state").assertTextEquals("false|0")
        compose.runOnIdle { state = state.copy(today = date, snapshot = snapshot.copy(fetchedAt = Instant.ofEpochSecond(1))) }
        compose.onNodeWithTag("search_state").assertTextEquals("true|0")
        compose.runOnIdle { state = state.copy(campus = Campus.SHAHE, snapshot = snapshot) }
        compose.onNodeWithTag("search_state").assertTextEquals("false|0")
    }

    @Test fun emptySelectionRunsSearchAndStaleDataRemainsBlocked() {
        val date = LocalDate.of(2026, 10, 12)
        val first = Classroom("04:教一-101", "教一-101", "教一", 60)
        val second = Classroom("04:教二-201", "教二-201", "教二", 100)
        val snapshot = ClassroomSnapshot("04", date, Instant.EPOCH, listOf(
            LessonSlot(1, LocalTime.of(8, 0), LocalTime.of(8, 45), listOf(first)),
            LessonSlot(2, LocalTime.of(8, 50), LocalTime.of(9, 35), listOf(second))))
        var today by mutableStateOf(date)
        compose.setContent {
            YouRoomTheme(campus = Campus.SHAHE) {
                val presentation = rememberClassroomSearch(YouRoomState(campus = Campus.SHAHE,
                    snapshot = snapshot, today = today, filters = SearchFilters()), enabled = true)
                Text(if (presentation.pending) "等待筛选" else "${presentation.result.rooms.size}",
                    Modifier.testTag("search_state"))
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("2")).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { today = date.plusDays(1) }
        compose.onNodeWithTag("search_state").assertTextEquals("0")
    }

    @Test fun filteringWaitsForPageSettlementAndDoesNotScanRoomsOnTheMainThread() {
        val reads = AtomicInteger()
        val mainThreadRead = AtomicBoolean(false)
        val rooms = object : AbstractList<Classroom>() {
            override val size = 300
            override fun get(index: Int): Classroom {
                reads.incrementAndGet()
                if (Thread.currentThread() == Looper.getMainLooper().thread) mainThreadRead.set(true)
                return Classroom("01:2-$index", "2-$index", "2", 60)
            }
        }
        val date = LocalDate.of(2026, 10, 12)
        val snapshot = ClassroomSnapshot("01", date, Instant.EPOCH,
            listOf(LessonSlot(1, LocalTime.of(8, 0), LocalTime.of(8, 45), rooms)))
        var enabled by mutableStateOf(false)
        compose.setContent {
            YouRoomTheme {
                val presentation = rememberClassroomSearch(YouRoomState(snapshot = snapshot, today = date,
                    filters = SearchFilters(setOf(1))), enabled)
                Text(if (presentation.pending) "等待筛选" else "${presentation.result.rooms.size}",
                    Modifier.testTag("search_state"))
            }
        }
        compose.onNodeWithTag("search_state").assertTextEquals("等待筛选")
        assertEquals(0, reads.get())
        compose.runOnIdle { enabled = true }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("300")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(reads.get() > 0)
        assertFalse(mainThreadRead.get())
        compose.runOnIdle { enabled = false }
        compose.onNodeWithTag("search_state").assertTextEquals("300")
    }
}
