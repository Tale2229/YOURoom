package com.example.youroom

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class ThemeContentUiTest {
    @get:Rule val compose=createComposeRule()
    private fun colorOf(tag: String): Color {
        val results=mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single().layoutInput.style.color
    }
    private fun assertInk(tag: String,expected: Color) {
        val pixels=compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        val count=(0 until pixels.width).sumOf { x -> (0 until pixels.height).count { y ->
            val p=pixels[x,y]
            p.alpha>.9f && abs(p.red-expected.red)+abs(p.green-expected.green)+abs(p.blue-expected.blue)<.025f
        } }
        assertTrue("$tag must be drawn in its theme color",count>10)
    }
    @Test fun defaultTextGenericActionsAndExitIconsFollowEveryToneInBothModes() {
        var tone by mutableStateOf(CampusTone.SAND)
        var dark by mutableStateOf(false)
        compose.setContent { YOURoomTheme(darkTheme=dark,appearance=AppearancePreferences(westTone=tone)) {
            Surface { Column {
                Text("主题正文",Modifier.testTag("theme_text"))
                IconAction({},Modifier.testTag("theme_action")) { Icon(Icons.Outlined.Add,null) }
                IconAction({},Modifier.testTag("theme_exit")) { DialogExitIcon() }
                IconAction({},Modifier.testTag("theme_disabled"),enabled=false) { DialogExitIcon(true) }
            } }
        } }
        for(night in listOf(false,true)) for(next in CampusTone.entries) {
            compose.runOnIdle { dark=night;tone=next }
            val colors=campusColorScheme(Campus.XITUCHENG,night,next)
            assertEquals(colors.onSurface,colorOf("theme_text"))
            assertInk("theme_action",colors.primary);assertInk("theme_exit",colors.primary)
            compose.onNodeWithTag("theme_disabled").assertIsNotEnabled()
        }
    }
    @Test fun aNestedCampusThemeDoesNotInheritTheOuterSurfacesInk() {
        compose.setContent { YOURoomTheme(campus=Campus.XITUCHENG,darkTheme=false) {
            Surface { YOURoomTheme(campus=Campus.SHAHE,darkTheme=false) {
                Text("沙河正文",Modifier.testTag("nested_text"))
            } }
        } }
        assertEquals(campusColorScheme(Campus.SHAHE,false).onSurface,colorOf("nested_text"))
        assertNotEquals(campusColorScheme(Campus.XITUCHENG,false).onSurface,colorOf("nested_text"))
    }
}
