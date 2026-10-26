package com.example.youroom

import com.example.youroom.domain.Campus
import com.example.youroom.domain.CampusTone
import com.example.youroom.ui.themeAvatarFrameColor
import com.example.youroom.ui.theme.campusColorScheme
import com.example.youroom.ui.theme.campusThemeRoles
import org.junit.Assert.*
import org.junit.Test

class ThemeAvatarFrameColorsTest {
    @Test fun everyThemeRestoresItsOriginalFrameRoleInBothCampusesAndModes() {
        for(campus in Campus.entries) for(tone in CampusTone.entries) for(dark in listOf(false,true)) {
            val palette=campusColorScheme(campus,dark,tone)
            assertEquals(campusThemeRoles(palette,tone,dark).impressionFrame,themeAvatarFrameColor(campus,tone,dark))
        }
    }
    @Test fun originalSingleHueFramesKeepTheirOriginalTransparency() {
        for(tone in listOf(CampusTone.SAND,CampusTone.PINE)) for(dark in listOf(false,true))
            assertEquals(.3f,themeAvatarFrameColor(Campus.XITUCHENG,tone,dark).alpha,.002f)
    }
    @Test fun advancedFramesAreNotArtificiallyFadedInDarkMode() {
        for(tone in CampusTone.entries.drop(2)) for(dark in listOf(false,true))
            assertEquals(1f,themeAvatarFrameColor(Campus.XITUCHENG,tone,dark).alpha,0f)
    }
    @Test fun previewSelectionCanUseANewFrameWithoutChangingTheAppliedPalette() {
        val original=campusColorScheme(Campus.XITUCHENG,false,CampusTone.SAND)
        val preview=themeAvatarFrameColor(Campus.XITUCHENG,CampusTone.MATISSE,false)
        assertNotEquals(themeAvatarFrameColor(Campus.XITUCHENG,CampusTone.SAND,false),preview)
        val unchanged=campusColorScheme(Campus.XITUCHENG,false,CampusTone.SAND)
        assertEquals(original.surface,unchanged.surface)
        assertEquals(original.primary,unchanged.primary)
    }
}
