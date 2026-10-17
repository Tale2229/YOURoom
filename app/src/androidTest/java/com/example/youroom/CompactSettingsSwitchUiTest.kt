package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.youroom.domain.Campus
import com.example.youroom.ui.CompactSettingsSwitch
import com.example.youroom.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CompactSettingsSwitchUiTest {
    @get:Rule val compose=createComposeRule()
    private var checked by mutableStateOf(false)
    private var enabled by mutableStateOf(true)
    private var rtl by mutableStateOf(false)
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var dark by mutableStateOf(false)
    private var changes=0
    private fun render()=compose.setContent {
        CompositionLocalProvider(LocalLayoutDirection provides if(rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            YouRoomTheme(campus=campus,darkTheme=dark) { Surface {
                Box(Modifier.padding(32.dp)) {
                    CompactSettingsSwitch(checked,enabled,{checked=it;changes++},Modifier.testTag("switch"))
                }
            } }
        }
    }
    private fun thumb()=compose.onNodeWithTag("settings_strip_numbers_switch_thumb",true).fetchSemanticsNode().boundsInRoot

    @Test fun compactCapsuleAndWhiteOverhangingThumbKeepAFullAccessibleTarget() {
        render()
        compose.onNodeWithTag("switch").assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Switch)).assertIsOff()
        compose.onNodeWithTag("settings_strip_numbers_switch_face",true).assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(26.dp)
        val track=compose.onNodeWithTag("settings_strip_numbers_switch_track",true)
            .assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(20.dp).fetchSemanticsNode().boundsInRoot
        assertTrue(thumb().top<track.top && thumb().bottom>track.bottom)
        compose.onNodeWithTag("switch").performClick().assertIsOn()
        assertEquals(1,changes)
    }

    @Test fun bothCampusPalettesHaveAWhiteThumbAndCorrectRtlEndpointsWithoutDisabledFading() {
        render()
        for(place in Campus.entries) for(night in listOf(false,true)) for(reverse in listOf(false,true)) for(on in listOf(false,true)) {
            compose.runOnIdle { campus=place;dark=night;rtl=reverse;checked=on;enabled=true }
            val face=compose.onNodeWithTag("settings_strip_numbers_switch_face",true)
            val bounds=face.fetchSemanticsNode().boundsInRoot
            val pixels=face.captureToImage().toPixelMap()
            val circle=thumb()
            val right=on!=reverse
            if(right) assertEquals(bounds.right,circle.right,.5f) else assertEquals(bounds.left,circle.left,.5f)
            val thumbPixel=pixels[(circle.center.x-bounds.left).toInt(),(circle.center.y-bounds.top).toInt()]
            assertEquals(Color.White,thumbPixel)
            val trackColor=if(on) campusColorScheme(place,night).primary else campusColorScheme(place,night).outlineVariant
            assertEquals(trackColor,pixels[with(compose.density) { (if(right) 6.dp else 38.dp).roundToPx() },pixels.height/2])
            compose.runOnIdle { enabled=false }
            compose.onNodeWithTag("switch").assertIsNotEnabled().performClick()
            assertEquals(on,checked)
            val disabled=face.captureToImage().toPixelMap()
            for(x in 0 until pixels.width) for(y in 0 until pixels.height) assertEquals(pixels[x,y],disabled[x,y])
        }
        assertEquals(0,changes)
    }

    @Test fun thumbMovesSmoothlyAndAQuickReverseReturnsToTheOriginalEndpoint() {
        render()
        val initial=thumb().center.x
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("switch").performClick()
        compose.mainClock.advanceTimeBy(96)
        val middle=thumb().center.x
        assertTrue(middle>initial && middle<initial+with(compose.density) { 18.dp.toPx() })
        compose.onNodeWithTag("switch").performClick()
        compose.mainClock.advanceTimeBy(350)
        assertEquals(initial,thumb().center.x,.5f)
        compose.onNodeWithTag("switch").assertIsOff()
        assertEquals(2,changes)
        compose.mainClock.autoAdvance=true
    }
}
