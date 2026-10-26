package com.example.youroom

import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test

class PlanningRequestIdentityTest {
    private val snapshot=DemoData.snapshot()
    private val goal=PlanningGoal(480,600,expectedRooms=setOf("01:2-201","01:3-305"))
    private fun key(data: ClassroomSnapshot=snapshot,profiles: Map<String,RoomProfile> = emptyMap(),
        target: PlanningGoal=goal,campus: Campus=Campus.XITUCHENG) =
        PlanningRequestIdentity.of(data,campus,data.queryDate,profiles,target)

    @Test fun effectiveInputsNotCollectionOrderingFetchTimesOrUnrelatedMarksControlIdentity() {
        val room=snapshot.classrooms.first()
        val normal=key()
        val reordered=snapshot.copy(fetchedAt=snapshot.fetchedAt.plusSeconds(60),slots=snapshot.slots.reversed().map {
            it.copy(classrooms=it.classrooms.reversed()) })
        assertEquals(normal,key(reordered,target=goal.copy(expectedRooms=goal.expectedRooms.reversed().toSet())))
        assertEquals(normal,key(profiles=mapOf(room.id to room.emptyProfile().copy(note="private",tags=setOf("tag"),outlets=true))))
        assertNotEquals(normal,key(profiles=mapOf(room.id to room.emptyProfile().copy(favorite=true))))
        assertNotEquals(normal,key(profiles=mapOf(room.id to room.emptyProfile().copy(excluded=true))))
        assertNotEquals(normal,key(target=goal.copy(expectedRooms=emptySet())))
        assertNotEquals(normal,key(target=goal.copy(start=481)))
        assertNotEquals(normal,key(target=goal.copy(flexible=!goal.flexible)))
        assertNotEquals(normal,key(campus=Campus.SHAHE))
        assertNotEquals(normal,key(snapshot.copy(queryDate=snapshot.queryDate.plusDays(1))))
        val changed=snapshot.copy(slots=snapshot.slots.mapIndexed { index,slot ->
            if(index==0) slot.copy(classrooms=emptyList()) else slot })
        assertNotEquals(normal,key(changed))
    }
}
