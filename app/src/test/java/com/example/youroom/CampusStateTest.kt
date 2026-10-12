package com.example.youroom

import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.YouRoomState
import com.example.youroom.ui.snapshotLabel
import org.junit.Assert.*
import org.junit.Test

class CampusStateTest {
    @Test fun westCampusRemainsConnectedWithoutGuessingAShaheApiCode() {
        assertEquals("01", Campus.XITUCHENG.verifiedApiId)
        assertTrue(Campus.XITUCHENG.connected)
        assertNull(Campus.SHAHE.verifiedApiId)
        assertFalse(Campus.SHAHE.connected)
    }

    @Test fun shaheNeverExposesWestCampusRoomsOrFreshnessLabels() {
        val snapshot = DemoData.snapshot().copy(isDemo = false)
        val state = YouRoomState(campus = Campus.SHAHE, snapshot = snapshot,
            loggedIn = true, selectedRoomId = "01:2-201")
        assertTrue(state.campusUnavailable)
        assertNull(state.campusSnapshot)
        assertTrue(state.result.rooms.isEmpty())
        assertNull(state.selectedRoom)
        assertFalse(state.stale)
        assertTrue(snapshotLabel(state).contains("待接入"))
    }

    @Test fun demoSnapshotsHaveDisjointRoomIdentitiesAndExplicitDemoFlags() {
        val west = DemoData.snapshot()
        val shahe = DemoData.snapshot(Campus.SHAHE)
        assertTrue(west.isDemo && shahe.isDemo)
        assertEquals(Campus.SHAHE.dataKey, shahe.campusId)
        assertTrue(west.classrooms.map { it.id }.toSet().intersect(shahe.classrooms.map { it.id }.toSet()).isEmpty())
        assertTrue(shahe.classrooms.all { it.name.startsWith("演示楼-") })
    }

    @Test fun shaheThemeDemoIsFilterableWithoutClaimingRealDataAccess() {
        val state = YouRoomState(campus = Campus.SHAHE, demo = true,
            snapshot = DemoData.snapshot(Campus.SHAHE), filters = SearchFilters(1, 2))
        assertFalse(state.campusUnavailable)
        assertTrue(state.result.rooms.isNotEmpty())
        assertTrue(state.result.rooms.all { it.classroom.id.startsWith("shahe:") })
        assertTrue(snapshotLabel(state).contains("演示数据"))
    }

    @Test fun personalProfilesAreScopedToTheVisibleCampus() {
        val west = RoomProfile("01:2-201", "2-201", "2", favorite = true)
        val shahe = RoomProfile("shahe:演示楼-A101", "演示楼-A101", "演示楼", favorite = true)
        val state = YouRoomState(profiles = mapOf(west.roomId to west, shahe.roomId to shahe))
        assertEquals(listOf(west), state.campusProfiles)
        assertEquals(listOf(shahe), state.copy(campus = Campus.SHAHE).campusProfiles)
    }

    @Test fun aShaheDemoSnapshotCannotBeAccidentallyPresentedOutsideDemoMode() {
        val snapshot = DemoData.snapshot(Campus.SHAHE)
        val state = YouRoomState(campus = Campus.SHAHE, demo = false, snapshot = snapshot,
            selectedRoomId = snapshot.classrooms.first().id)
        assertTrue(state.campusUnavailable)
        assertNull(state.campusSnapshot)
        assertNull(state.selectedRoom)
        assertTrue(state.result.rooms.isEmpty())
    }
}
