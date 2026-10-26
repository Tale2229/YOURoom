package com.example.youroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.youroom.data.DemoData
import com.example.youroom.domain.*
import com.example.youroom.ui.*
import com.example.youroom.ui.theme.YOURoomTheme
import com.example.youroom.ui.theme.campusColorScheme
import org.junit.Rule
import org.junit.Test
import java.io.File

class ThemeAvatarUiTest {
    @get:Rule val compose = createComposeRule()
    private var campus by mutableStateOf(Campus.XITUCHENG)
    private var appearance by mutableStateOf(AppearancePreferences(darkMode = false))

    @Test fun allApprovedAvatarsRenderUntintedAt75dpInBothModesAndCampuses() {
        compose.setContent { YOURoomTheme(campus = campus, appearance = appearance) { Surface { Column {
            Box(Modifier.weight(1f)) { SettingsScreen(YOURoomState(initializing = false,
                campus = campus, snapshot = DemoData.snapshot(campus), appearance = appearance), {}, {}, {}) }
            WorkspaceNavigation(2, {})
        } } } }
        for (dark in listOf(false, true)) for (c in Campus.entries) for (tone in CampusTone.entries) {
            compose.runOnIdle { campus = c; appearance = appearance.withTone(c, tone).copy(darkMode = dark) }
            compose.waitForIdle()
            compose.onNodeWithTag("settings_theme_name").assertTextEquals(tone.title)
            val node = compose.onNodeWithTag("settings_theme_impression", true)
                .assertWidthIsEqualTo(75.dp).assertHeightIsEqualTo(75.dp)
            assertThemeAvatar(node, tone)
            assertThemeFrame(compose.onNodeWithTag("settings_theme_frame"), tone,
                campusColorScheme(c, dark, tone).surface, dark)
            if (c == Campus.XITUCHENG) {
                val i = InstrumentationRegistry.getInstrumentation()
                File(i.targetContext.getExternalFilesDir(null), "avatar-${tone.id}-${if (dark) "dark" else "light"}.png")
                    .outputStream().use { i.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
