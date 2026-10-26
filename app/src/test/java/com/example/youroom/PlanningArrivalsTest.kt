package com.example.youroom

import com.example.youroom.domain.*
import kotlin.math.max
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class PlanningArrivalsTest {
    @Test fun primitiveMinutesDeduplicateSortAndClearWithoutLeakingPreviousCandidates() {
        val minutes = PlanningMinutes()
        val random = Random(2055)
        repeat(80) {
            minutes.clear()
            val expected = sortedSetOf<Int>()
            repeat(500) {
                val value = random.nextInt(1440)
                minutes.add(value); minutes.add(value); expected += value
            }
            minutes.sort()
            assertEquals(expected.toList(),(0 until minutes.size).map { minutes[it] })
        }
        minutes.clear(); minutes.add(0); minutes.add(1439); minutes.sort()
        assertEquals(listOf(0,1439),(0 until minutes.size).map { minutes[it] })
    }

    @Test fun cachedFinalCutsExactlyMatchThePreviousLoopWithAndWithoutExpectedRooms() {
        val rooms = listOf("2-201","3-305","3-408","未来学习大楼-215").map {
            Classroom("01:$it",it,it.substringBefore('-'),50)
        }
        val spans = listOf(StudyPlanner.Span(0,480,1100),StudyPlanner.Span(1,500,930),
            StudyPlanner.Span(2,875,940),StudyPlanner.Span(2,985,1440),StudyPlanner.Span(3,700,1440))
        val locations = rooms.map { CampusTopology.location(it,Campus.XITUCHENG) }
        val travel = Array(rooms.size) { a -> IntArray(rooms.size) { b ->
            if (a == b) 0 else CampusTopology.transferMinutes(Campus.XITUCHENG,locations[a],locations[b])
        } }
        for (expected in listOf(false,true)) for (end in listOf(930,1020,1320)) {
            val data = StudyPlanner.Prepared(rooms,BooleanArray(rooms.size),locations,travel,spans,
                BooleanArray(rooms.size) { expected && it == 1 })
            val finals = spans.filter { it.end >= end }
            val cuts = PlanningArrivalCuts(data,spans,finals,end)
            val minutes = PlanningMinutes()
            repeat(30) { iteration -> spans.forEachIndexed { index,target ->
                val first = 830+iteration
                val last = end-1
                val endPlusBudget = target.end+iteration
                val legacy = sortedSetOf<Int>()
                for (final in finals) {
                    val walk = travel[target.room][final.room]
                    legacy += (end-walk-2).coerceIn(first,last)
                    legacy += (final.start-walk-1).coerceIn(first,last)
                    if (expected) {
                        legacy += (final.start-walk-30).coerceIn(first,last)
                        legacy += (end-walk-60).coerceIn(first,last)
                        legacy += (end-walk-31).coerceIn(first,last)
                    }
                    legacy += (endPlusBudget-max(walk,final.start-target.end)).coerceIn(first,last)
                }
                minutes.clear(); cuts.addTo(minutes,index,endPlusBudget,first,last); minutes.sort()
                assertEquals(legacy.toList(),(0 until minutes.size).map { minutes[it] })
            } }
        }
    }
}
