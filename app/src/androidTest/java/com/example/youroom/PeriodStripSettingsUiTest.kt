package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YouRoomTheme
import com.example.youroom.data.local.*
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PeriodStripSettingsUiTest {
    @get:Rule val compose=createComposeRule()
    private val data=DemoData.snapshot()
    private val room=data.classrooms.first()
    private var preference by mutableStateOf(PeriodStripPreferences())
    private var rtl by mutableStateOf(false)
    private var dark by mutableStateOf(false)
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var fontScale by mutableFloatStateOf(1f)
    private var settingsInteractive by mutableStateOf(true)
    private val windows=listOf(StudyWindow(720,810),StudyWindow(1038,1098),StudyWindow(1278,1338))
    private val clock=Clock.fixed(Instant.parse("2026-10-16T04:37:00Z"),SchoolZone)
    private val wave=RoomPeriodWave()
    private fun render(settings: Boolean=false)=compose.setContent {
        val density=LocalDensity.current
        CompositionLocalProvider(LocalPeriodStripPreferences provides preference,
            LocalLayoutDirection provides if(rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            LocalDensity provides Density(density.density,fontScale)) {
            YouRoomTheme(campus=campus,darkTheme=dark) { Surface {
                if(settings) SettingsScreen(YouRoomState(initializing=false,campus=campus,
                    snapshot=data.copy(campusId=campus.dataKey),periodStrip=preference),{},{},{},
                    onPeriodStrip={preference=it},interactive=settingsInteractive)
                else Column(Modifier.fillMaxWidth().padding(top=44.dp)) {
                    wave.Run()
                    PlanningTargetStrip(data.slots,windows,clock)
                    Spacer(Modifier.height(26.dp))
                    PlanningRoomStrip(PlanningRoomUse(room,true,windows),data.slots,setOf(1,2,5,6,7,11,12,14),true,1)
                    Spacer(Modifier.height(24.dp))
                    RoomAvailabilityStrip("inspect",setOf(1,2,5,6),data.slots,true,wave=wave)
                    TextButton(onClick={wave.play()},modifier=Modifier.testTag("wave_play")) { Text("播放波浪") }
                }
            } }
        }
    }
    private fun target(n: Int)=compose.onNodeWithTag("planner_target_period_$n",true).fetchSemanticsNode().boundsInRoot
    private fun bar(n: Int)=compose.onNodeWithTag("room_period_inspect_$n",true).fetchSemanticsNode().boundsInRoot
    private fun px(dp: Float)=with(compose.density) { dp.dp.toPx() }
    private fun shot(name: String) {
        compose.waitForIdle()
        val i=InstrumentationRegistry.getInstrumentation();i.waitForIdleSync()
        android.os.SystemClock.sleep(100)
        File(i.targetContext.getExternalFilesDir(null),"$name.png").outputStream().use {
            i.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }

    @Test fun defaultKeepsEqualGapsAndNoNumbersAndBothStylesUseTheSameStripWidth() {
        render()
        for(n in 1..13) assertEquals(px(3f),bar(n+1).left-bar(n).right,1f)
        compose.onNodeWithTag("room_period_number_inspect_1",true).assertDoesNotExist()
        val width=bar(14).right-bar(1).left
        val before=bar(1).width
        compose.runOnIdle { preference=PeriodStripPreferences(PeriodStripLayout.TIME_PROPORTIONAL) }
        assertEquals(width,bar(14).right-bar(1).left,1f)
        assertTrue(bar(1).width<before)
        assertEquals(bar(5).width,bar(6).left-bar(5).right,1f)
        assertTrue(bar(6).left-bar(5).right>bar(2).left-bar(1).right)
    }

    @Test fun targetAndPlannedRoomAndSearchShareProportionalCoordinatesInBothDirections() {
        preference=PeriodStripPreferences(PeriodStripLayout.TIME_PROPORTIONAL,true)
        render()
        for(reverse in listOf(false,true)) {
            compose.runOnIdle { rtl=reverse }
            for(n in 1..14) {
                val target=target(n);val search=bar(n)
                val planned=compose.onNodeWithTag("room_period_${room.id}_$n",true).fetchSemanticsNode().boundsInRoot
                assertEquals(target.left,search.left,1f);assertEquals(target.right,search.right,1f)
                assertEquals(target.left,planned.left,1f);assertEquals(target.right,planned.right,1f)
                val number=compose.onNodeWithTag("room_period_number_inspect_$n",true).fetchSemanticsNode().boundsInRoot
                assertEquals(search.center.x,number.center.x,1f);assertTrue(number.top>search.bottom)
                compose.onNodeWithTag("planner_target_number_$n",true).assertExists()
            }
        }
    }

    @Test fun targetNumbersAndCaptionsDoNotOverlapAtLargeFontsAndBothCampusThemes() {
        preference=PeriodStripPreferences(PeriodStripLayout.TIME_PROPORTIONAL,true);render()
        for(scale in listOf(1f,1.5f)) for(place in Campus.entries) for(night in listOf(false,true)) {
            compose.runOnIdle { fontScale=scale;campus=place;dark=night }
            val numbers=(1..14).map { compose.onNodeWithTag("planner_target_number_$it",true).fetchSemanticsNode().boundsInRoot }
            val barBottom=compose.onNodeWithTag("planner_target_bar",true).fetchSemanticsNode().boundsInRoot.bottom
            assertTrue("number row clears even the upward bracket caps",numbers.maxOf { it.bottom }<=barBottom-px(12f))
            val captions=windows.indices.map { compose.onNodeWithTag("planner_target_range_$it",true).fetchSemanticsNode().boundsInRoot }
            numbers.forEach { n -> captions.forEach { c -> assertFalse(n.overlaps(c)) } }
            for(n in 1..14) compose.onNodeWithTag("room_period_number_inspect_$n",true).assertIsDisplayed()
            if(scale==1f && !night) shot("period-settings-${place.dataKey}-1.17")
        }
    }

    @Test fun holdingAndMovingAcrossLunchInspectsTheActualProportionalBarInRtlToo() {
        preference=PeriodStripPreferences(PeriodStripLayout.TIME_PROPORTIONAL,true);render()
        for(reverse in listOf(false,true)) {
            compose.runOnIdle { rtl=reverse }
            val strip=compose.onNodeWithTag("room_period_strip_inspect")
            val origin=strip.fetchSemanticsNode().boundsInRoot.topLeft
            strip.performTouchInput { down(bar(5).center-origin) }
            compose.waitUntil(4000) { compose.onAllNodesWithTag("room_time_inspect_label_5",true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("room_time_inspect_label_5",true).assertTextEquals("5 · 11:30–12:15")
            strip.performTouchInput { moveTo(bar(6).center-origin+Offset(0f,80f)) }
            compose.onNodeWithTag("room_time_inspect_label_6",true).assertTextEquals("6 · 13:00–13:45")
            compose.onNodeWithTag("room_period_inspect_6",true).assertIsSelected()
            shot("period-tooltip-${if(reverse) "rtl" else "ltr"}-1.17")
            strip.performTouchInput { up() }
        }
    }

    @Test fun settingsExposeOriginalDefaultBothStylesAndIndependentNumberToggle() {
        render(settings=true)
        compose.onNodeWithTag("settings_strip_layout").performScrollTo()
        compose.onNodeWithTag("settings_strip_EQUAL").assertIsSelected()
        compose.onNodeWithTag("settings_strip_TIME_PROPORTIONAL").performClick()
        assertEquals(PeriodStripLayout.TIME_PROPORTIONAL,preference.layout)
        compose.onNodeWithTag("settings_strip_numbers").performScrollTo().performClick()
        compose.onNodeWithTag("settings_strip_numbers_switch",true).assertIsOn()
        assertTrue(preference.showNumbers)
        shot("settings-compact-switch-on-1.17.3")
        compose.onNodeWithTag("settings_strip_layout").performScrollTo()
        compose.onNodeWithTag("settings_strip_EQUAL").performClick()
        assertEquals(PeriodStripLayout.EQUAL,preference.layout);assertTrue(preference.showNumbers)
        compose.onNodeWithTag("settings_strip_numbers").performScrollTo().performClick()
        assertFalse(preference.showNumbers)
        shot("period-settings-page-1.17")
    }

    @Test fun proportionalWaveChangesPixelsNotGeometryAndFinishesOnceWithNumbersStillVisible() {
        preference=PeriodStripPreferences(PeriodStripLayout.TIME_PROPORTIONAL,true);render()
        val before=bar(6)
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("wave_play").performClick()
        compose.mainClock.advanceTimeBy(250+300)
        assertTrue(compose.runOnIdle { wave.isRunning })
        assertEquals(before,bar(6))
        compose.onNodeWithTag("room_period_number_inspect_6",true).assertExists()
        compose.mainClock.advanceTimeBy(700)
        assertFalse(compose.runOnIdle { wave.isRunning })
        assertEquals(before,bar(6))
        compose.mainClock.autoAdvance=true
    }

    @Test fun switchShowsTheSavedStateAtFullContrastBeforePageInteractionIsEnabled() {
        preference=preference.copy(showNumbers=true);settingsInteractive=false
        render(settings=true)
        val toggle=compose.onNodeWithTag("settings_strip_numbers_switch",true)
        for(place in Campus.entries) for(night in listOf(false,true)) for(checked in listOf(false,true)) {
            compose.runOnIdle { settingsInteractive=false;campus=place;dark=night;preference=preference.copy(showNumbers=checked) }
            toggle.assertIsNotEnabled()
            if(checked) toggle.assertIsOn() else toggle.assertIsOff()
            val entering=toggle.captureToImage().toPixelMap()
            toggle.performSemanticsAction(SemanticsActions.OnClick) { it() }
            assertEquals(checked,preference.showNumbers)
            compose.runOnIdle { settingsInteractive=true }
            toggle.assertIsEnabled()
            val settled=toggle.captureToImage().toPixelMap()
            assertEquals(entering.width,settled.width);assertEquals(entering.height,settled.height)
            for(x in 0 until entering.width) for(y in 0 until entering.height) {
                val a=entering[x,y];val b=settled[x,y]
                assertTrue("the first visible switch already uses the settled colors and thumb",
                    kotlin.math.abs(a.red-b.red)+kotlin.math.abs(a.green-b.green)+kotlin.math.abs(a.blue-b.blue)<.015f)
            }
        }
        toggle.performClick()
        assertFalse(preference.showNumbers)
    }

    @Test fun actualAndroidPreferencesFileRetainsDisplayOptionsAndOldFiltersAfterReopening() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val path=File(context.cacheDir,"period-strip-test-${System.nanoTime()}.preferences_pb")
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try {
            val raw=PreferenceDataStoreFactory.create(scope=scope,produceFile={path})
            val first=SettingsStore(raw)
            assertEquals(PeriodStripPreferences(),first.periodStrip.first())
            raw.edit { it[SharedFilterPreferences.favorites]=true }
            val pref=PeriodStripPreferences(PeriodStripLayout.TIME_PROPORTIONAL,true)
            first.savePeriodStrip(pref)
            assertEquals(pref,first.periodStrip.first())
            scope.coroutineContext[Job]!!.cancelAndJoin()
            val reopenedScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
            try {
                val reopened=SettingsStore(PreferenceDataStoreFactory.create(scope=reopenedScope,produceFile={path}))
                assertEquals(pref,reopened.periodStrip.first())
                assertTrue(reopened.filters.first().favoritesOnly)
            } finally { reopenedScope.coroutineContext[Job]!!.cancelAndJoin() }
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin();path.delete() }
    }
}
