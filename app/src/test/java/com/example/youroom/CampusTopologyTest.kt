package com.example.youroom

import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test

class CampusTopologyTest {
    private fun room(name: String) = Classroom("01:$name", name, name.substringBefore('-'), null)
    @Test fun mainBuildingUsesTheConfirmedLearningBuildingAlias() {
        assertEquals("main", CampusTopology.building("未来学习大楼"))
        assertEquals("main", CampusTopology.building("主楼"))
        assertEquals("1", CampusTopology.building("1"))
    }
    @Test fun combinedRoomNamesAndWingsHaveExplicitFloors() {
        assertEquals(2, CampusTopology.location(room("未来学习大楼-202-203"), Campus.XITUCHENG).floor)
        assertEquals(3, CampusTopology.location(room("教一-A301"), Campus.SHAHE).floor)
        assertNull(CampusTopology.location(room("主楼-报告厅"), Campus.XITUCHENG).floor)
    }
    @Test fun localMovesAreCheaperThanStairsAndCrossBuildingMoves() {
        val a = CampusTopology.location(room("2-201"), Campus.XITUCHENG)
        val nearby = CampusTopology.location(room("2-202"), Campus.XITUCHENG)
        val far = CampusTopology.location(room("2-240"), Campus.XITUCHENG)
        val upstairs = CampusTopology.location(room("2-601"), Campus.XITUCHENG)
        val outside = CampusTopology.location(room("4-601"), Campus.XITUCHENG)
        assertTrue(CampusTopology.transferMinutes(Campus.XITUCHENG, a, nearby) < CampusTopology.transferMinutes(Campus.XITUCHENG, a, far))
        assertTrue(CampusTopology.transferMinutes(Campus.XITUCHENG, a, nearby) < CampusTopology.transferMinutes(Campus.XITUCHENG, a, upstairs))
        assertTrue(CampusTopology.transferMinutes(Campus.XITUCHENG, a, upstairs) < CampusTopology.transferMinutes(Campus.XITUCHENG, a, outside))
    }
    @Test fun lowerFloorsReduceCrossBuildingCostsAndUnknownsStayConservative() {
        val first = RoomLocation("2", 1, 101)
        assertTrue(CampusTopology.transferMinutes(Campus.XITUCHENG, first, RoomLocation("main", 1, 101)) <
            CampusTopology.transferMinutes(Campus.XITUCHENG, first, RoomLocation("main", 6, 601)))
        assertEquals(8, CampusTopology.doorMinutes(Campus.XITUCHENG, "未知楼", "2"))
    }
    @Test fun shaheDemoWingsAreSeparateBuildingsAndUseAFixedCrossBuildingEstimate() {
        val first = CampusTopology.location(room("演示楼-A101"), Campus.SHAHE)
        val second = CampusTopology.location(room("演示楼-B101"), Campus.SHAHE)
        assertNotEquals(first.building, second.building)
        assertEquals(4, CampusTopology.doorMinutes(Campus.SHAHE, first.building, second.building))
    }
}
