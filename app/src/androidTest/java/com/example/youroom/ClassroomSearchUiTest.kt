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

class ClassroomSearchUiTest {
    @get:Rule val compose = createComposeRule()

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
