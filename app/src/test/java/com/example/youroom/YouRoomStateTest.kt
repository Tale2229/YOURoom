package com.example.youroom

import com.example.youroom.data.SchoolJson
import com.example.youroom.domain.SearchFilters
import com.example.youroom.ui.YouRoomState
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class YouRoomStateTest {
    private val date = LocalDate.of(2026, 10, 10)
    private val snapshot = SchoolJson.snapshot("""{"code":"1","data":[{"NODENAME":"1","NODETIME":"08:00-08:45","CLASSROOMS":"2-201(180)"}]}""",
        "01", date, Instant.parse("2026-10-10T01:00:00Z"))

    @Test fun sameDayCacheCanBeFilteredButIsClearlyMarkedAsCache() {
        val state = YouRoomState(initializing = false, snapshot = snapshot,
            today = date, fromCache = true, viewingCache = true, filters = SearchFilters(1, 1))
        assertFalse(state.stale)
        assertTrue(state.showWorkspace)
        assertEquals(1, state.result.rooms.size)
    }

    @Test fun staleCacheIsNeverExposedAsTodayAvailability() {
        val state = YouRoomState(snapshot = snapshot, today = date.plusDays(1),
            viewingCache = true, selectedRoomId = "01:2-201", filters = SearchFilters(1, 1))
        assertTrue(state.stale)
        assertTrue(state.result.rooms.isEmpty())
        assertTrue(state.selectedRoom!!.freeSlots.isEmpty())
    }

    @Test fun afterLogoutCacheDoesNotAutomaticallySkipTheLoginScreen() {
        val state = YouRoomState(initializing = false, snapshot = snapshot, loggedIn = false, today = date)
        assertFalse(state.showWorkspace)
    }
}
