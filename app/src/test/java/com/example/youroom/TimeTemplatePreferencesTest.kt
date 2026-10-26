package com.example.youroom

import androidx.datastore.preferences.core.*
import com.example.youroom.data.local.*
import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test

class TimeTemplatePreferencesTest {
    private val template = TimeTemplate("stable-id", "全天", listOf(StudyWindow(480, 720), StudyWindow(840, 1020), StudyWindow(1200, 1320)))

    @Test fun defaultsAreBalancedFlexibleAndCampusIndependent() {
        val values = PlanningPreferenceKeys.read(emptyPreferences())
        Campus.entries.forEach { assertEquals(CampusPlanningPreferences(), values.forCampus(it)) }
        val west = values.withCampus(Campus.XITUCHENG, CampusPlanningPreferences(PlanningDirection.FAVORITES, false, listOf(template)))
        assertEquals(CampusPlanningPreferences(), west.forCampus(Campus.SHAHE))
        val raw=mutablePreferencesOf()
        val firstUpdate=PlanningPreferences().withCampus(Campus.XITUCHENG,west.forCampus(Campus.XITUCHENG))
        PlanningPreferenceKeys.write(raw,firstUpdate)
        assertEquals(firstUpdate,PlanningPreferenceKeys.read(raw))
    }
    @Test fun roundTripPreservesStableIdsAndAllOtherPreferences() {
        val raw = mutablePreferencesOf(SharedFilterPreferences.favorites to true, AppearancePreferenceKeys.west to "clay")
        val preferences = PlanningPreferences().withCampus(Campus.XITUCHENG,
            CampusPlanningPreferences(PlanningDirection.BUILDINGS, false, listOf(template)))
            .withCampus(Campus.SHAHE, CampusPlanningPreferences(PlanningDirection.EXPECTED))
        PlanningPreferenceKeys.write(raw, preferences)
        assertEquals(preferences, PlanningPreferenceKeys.read(raw))
        assertEquals(true, raw[SharedFilterPreferences.favorites]); assertEquals("clay", raw[AppearancePreferenceKeys.west])
    }
    @Test fun unknownDirectionOrDamagedTemplateCannotResetOtherCampusOrMode() {
        val raw = mutablePreferencesOf(PlanningPreferenceKeys.direction(Campus.XITUCHENG) to "unknown",
            PlanningPreferenceKeys.flexible(Campus.XITUCHENG) to false,
            PlanningPreferenceKeys.templates(Campus.XITUCHENG) to "not json",
            PlanningPreferenceKeys.direction(Campus.SHAHE) to "CHANGES")
        val preferences = PlanningPreferenceKeys.read(raw)
        assertEquals(CampusPlanningPreferences(flexible = false), preferences.forCampus(Campus.XITUCHENG))
        assertEquals(PlanningDirection.CHANGES, preferences.forCampus(Campus.SHAHE).direction)
    }
    @Test fun oneDamagedEntryDoesNotHideValidEntriesAndDuplicateIdsAreDeduplicated() {
        val raw = mutablePreferencesOf()
        PlanningPreferenceKeys.write(raw, PlanningPreferences().withCampus(Campus.XITUCHENG,
            CampusPlanningPreferences(templates = listOf(template))))
        val valid = raw[PlanningPreferenceKeys.templates(Campus.XITUCHENG)]!!.removePrefix("[").removeSuffix("]")
        raw[PlanningPreferenceKeys.templates(Campus.XITUCHENG)] = "[{},$valid,$valid]"
        assertEquals(listOf(template), PlanningPreferenceKeys.read(raw).forCampus(Campus.XITUCHENG).templates)
    }
    @Test fun templatesAllowPastTimesAndAdjacencyButRejectOverlapsShortOrCrossDayWindows() {
        assertTrue(TimeTemplates.valid(listOf(StudyWindow(0,30), StudyWindow(30,60), StudyWindow(1380,1439))))
        listOf(emptyList(), listOf(StudyWindow(480,509)), listOf(StudyWindow(1380,1440)),
            listOf(StudyWindow(480,600), StudyWindow(590,700)), List(4) { StudyWindow(it*60,it*60+30) })
            .forEach { assertFalse(TimeTemplates.valid(it)) }
    }
    @Test fun removingATemplateDoesNotChangeOtherCampusOrDefaults() {
        val raw = mutablePreferencesOf()
        val west = CampusPlanningPreferences(PlanningDirection.FAVORITES, false, listOf(template))
        val prefs = PlanningPreferences().withCampus(Campus.XITUCHENG, west)
            .withCampus(Campus.SHAHE, CampusPlanningPreferences(templates = listOf(template.copy(id="shahe"))))
        PlanningPreferenceKeys.write(raw, prefs)
        PlanningPreferenceKeys.write(raw, prefs.withCampus(Campus.XITUCHENG, west.copy(templates=emptyList())))
        val restored = PlanningPreferenceKeys.read(raw)
        assertEquals(west.copy(templates=emptyList()), restored.forCampus(Campus.XITUCHENG))
        assertEquals(prefs.forCampus(Campus.SHAHE), restored.forCampus(Campus.SHAHE))
    }
}
