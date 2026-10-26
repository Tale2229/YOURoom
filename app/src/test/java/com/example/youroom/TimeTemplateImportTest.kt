package com.example.youroom

import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test

class TimeTemplateImportTest {
    private val periods = listOf(StudyWindow(480,720), StudyWindow(840,1020), StudyWindow(1200,1320))
    @Test fun strictImportIsAtomicWhenOnePeriodHasPassed() {
        val result = TimeTemplates.import(periods, false, 840)
        assertFalse(result.accepted); assertTrue(result.windows.isEmpty()); assertEquals(1,result.skipped)
        assertTrue(result.message!!.contains("组合未导入"))
    }
    @Test fun skippingPastPeriodsRetainsTheRemainingSortedWindows() {
        val result = TimeTemplates.import(periods.reversed(),true,840)
        assertEquals(periods.drop(1),result.windows); assertEquals(1,result.skipped)
    }
    @Test fun allInvalidNeverLooksLikeASuccessfulClear() {
        val result = TimeTemplates.import(periods,true,1400)
        assertFalse(result.accepted); assertTrue(result.windows.isEmpty()); assertEquals(3,result.skipped)
    }
    @Test fun fiveMinuteGraceIsSharedWithManualConfirmation() {
        assertTrue(TimeTemplates.import(listOf(StudyWindow(840,900)),false,845).accepted)
        assertFalse(TimeTemplates.import(listOf(StudyWindow(840,900)),false,846).accepted)
    }
    @Test fun invalidRangesOverlapsAndTheThreeWindowLimitAreCheckedDuringImportToo() {
        val invalid = listOf(StudyWindow(480,510), StudyWindow(500,530), StudyWindow(700,710), StudyWindow(1320,60))
        val result = TimeTemplates.import(invalid,true,null)
        assertEquals(listOf(StudyWindow(480,510)),result.windows); assertEquals(3,result.skipped)
        val four = List(4) { StudyWindow(480+it*60,510+it*60) }
        assertFalse(TimeTemplates.import(four,false,null).accepted)
        assertEquals(3,TimeTemplates.import(four,true,null).windows.size)
    }
    @Test fun adjacencyIsAllowedAndSourceListsAreNotMutated() {
        val original = listOf(StudyWindow(600,660),StudyWindow(540,600))
        assertEquals(original.reversed(),TimeTemplates.import(original,false,null).windows)
        assertEquals(600,original.first().start)
        assertFalse(TimeTemplates.import(emptyList(),true,null).accepted)
    }
}
