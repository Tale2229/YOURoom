package com.example.youroom

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.example.youroom.data.local.AppearancePreferenceKeys
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class MorandiThemeTest {
    private fun scheme(dark: Boolean)=campusColorScheme(Campus.XITUCHENG,dark,CampusTone.MORANDI)
    private fun roles(dark: Boolean)=campusThemeRoles(scheme(dark),CampusTone.MORANDI,dark)
    private fun contrast(a: Color,b: Color)=(maxOf(a.luminance(),b.luminance())+.05f)/(minOf(a.luminance(),b.luminance())+.05f)

    @Test fun fourthEditionSavesExactlyTheThreeApprovedAccents() {
        val c=scheme(false);val r=roles(false)
        assertEquals(Color(0xFFCDC5B2),r.listDivider);assertEquals(Color(0xFFCDC5B2),r.periodIdle)
        assertEquals(Color(0xFFE4DFD2),r.wheelCenter)
        assertEquals(Color(0xFF465963),c.primary);assertEquals(Color(0xFFCDD6D6),r.impressionFrame)
        assertEquals(Color(0xFFCDD5D3),c.outlineVariant);assertEquals(c.outlineVariant,r.divider)
        assertNotEquals(c.outlineVariant,r.listDivider);assertNotEquals(c.outlineVariant,r.periodIdle)
        assertEquals(Color(0xFF6E695F),roles(true).listDivider)
        assertEquals(Color(0xFF766F62),roles(true).periodIdle)
        assertEquals(Color(0xFF46453D),roles(true).wheelCenter)
    }

    @Test fun silverGraphiteStructureAndBothRosesRemainAsApproved() {
        val c=scheme(false);val r=roles(false)
        assertEquals(Color(0xFFF2F3EE),c.surface);assertEquals(c.surface,c.background)
        assertEquals(Color(0xFFD1D5D6),r.header);assertEquals(Color(0xFF535D64),r.navigation)
        assertEquals(Color(0xFFDCE0E0),r.planningHeading)
        assertEquals(Color(0xFFD7BFC2),r.enabledOption);assertEquals(Color(0xFF885F6B),r.favorite)
        assertEquals(Color(0xFF5D717A),r.settingsIcon);assertNotEquals(r.favorite,r.settingsIcon)
        assertEquals(Color(0xFF242C31),scheme(true).surface)
        assertEquals(Color(0xFFD1B8BD),roles(true).enabledOption);assertEquals(Color(0xFFD7B1BD),roles(true).favorite)
        assertTrue(r.hideHeaderDivider)
    }

    @Test fun launchAndSelectionsKeepSeparateReadableColorRoles() {
        for(dark in listOf(false,true)) {
            val c=scheme(dark);val r=roles(dark)
            assertEquals(c.tertiary,r.navigationSelected);assertEquals(c.onTertiary,r.onNavigationSelected)
            assertEquals(c.tertiary,r.confirmation);assertEquals(c.tertiary,r.selectionFill)
            assertEquals(c.onTertiary,r.onSelection);assertFalse(r.outlineExpectedSelection)
            assertNotEquals(r.enabledOption,r.launch);assertNotEquals(r.enabledOption,r.wheelCenter)
        }
        assertEquals(Color(0xFF526875),roles(false).launch);assertEquals(Color(0xFFB5C6CD),roles(true).launch)
        assertEquals(Color(0xFF7D555F),roles(false).selectedRoomAction)
    }

    @Test fun allTextPairsAndAvailabilityPairsKeepTheirContrast() {
        for(dark in listOf(false,true)) {
            val c=scheme(dark);val r=roles(dark)
            listOf(r.header to r.onHeader,r.header to r.headerMuted,r.navigation to r.navigationIdle,
                r.navigationSelected to r.onNavigationSelected,r.planningHeading to r.onPlanningHeading,
                r.planningHeading to r.planningHeadingMuted,r.planningHeading to r.planningHeadingEntry,
                r.roomActions to r.onRoomActions,r.roomActions to r.selectedRoomAction,r.filter to r.onFilter,
                r.launch to r.onLaunch,r.enabledOption to r.onEnabledOption,r.confirmation to r.onConfirmation,
                r.wheelCenter to c.primary,c.surface to c.onSurface,c.surface to c.onSurfaceVariant,
                c.surface to r.settingsIcon,c.surface to r.themeArrow,c.surface to r.favorite,c.surface to r.note,
                c.secondary to c.onSecondary,c.secondaryContainer to c.onSecondaryContainer,
                c.tertiary to c.onTertiary,c.tertiaryContainer to c.onTertiaryContainer
            ).forEach { (bg,fg)->assertTrue("dark=$dark $bg / $fg",contrast(bg,fg)>=4.5f) }
            assertTrue(contrast(c.primary,r.periodIdle!!)>=3f)
            assertTrue(contrast(r.switchThumb!!,r.switchTrackOn!!)>=3f)
            assertTrue(contrast(r.switchThumb,r.switchTrackOff!!)>=3f)
        }
    }

    @Test fun priorNineThemesDoNotAcquireLocalAccentOverrides() {
        for(tone in CampusTone.entries.take(7)) for(dark in listOf(false,true)) {
            val c=campusColorScheme(Campus.XITUCHENG,dark,tone);val r=campusThemeRoles(c,tone,dark)
            assertNull(r.periodIdle);assertNull(r.listDivider)
            assertNull(r.switchTrackOn);assertNull(r.switchTrackOff);assertNull(r.switchThumb)
        }
    }

    @Test fun stableIdPersistsIndependentlyAndWrapsAfterBurgundy() {
        val stored=mutablePreferencesOf()
        val value=AppearancePreferences(CampusTone.MORANDI,CampusTone.BURGUNDY,Campus.SHAHE)
        AppearancePreferenceKeys.write(stored,value)
        assertEquals("morandi",stored[AppearancePreferenceKeys.west]);assertEquals(value,AppearancePreferenceKeys.read(stored))
        assertEquals(listOf("sand","pine","matisse","dunhuang","old_port","peach_plum","burgundy"),CampusTone.entries.take(7).map { it.id })
        assertEquals(CampusTone.MORANDI,CampusTone.BURGUNDY.step(1));assertEquals(CampusTone.RED_BEAN,CampusTone.MORANDI.step(1))
        assertEquals(CampusTone.MONDRIAN,CampusTone.SAND.step(-1))
        assertEquals(CampusTone.SAND,AppearancePreferences().westTone);assertEquals(CampusTone.PINE,AppearancePreferences().shaheTone)
        assertEquals(scheme(false).surface,campusColorScheme(Campus.SHAHE,false,CampusTone.MORANDI).surface)
    }
}
