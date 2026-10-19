package com.example.youroom

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.example.youroom.data.local.AppearancePreferenceKeys
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class AutumnCitrusThemeTest {
    private fun scheme(dark: Boolean)=campusColorScheme(Campus.XITUCHENG,dark,CampusTone.AUTUMN_CITRUS)
    private fun roles(dark: Boolean)=campusThemeRoles(scheme(dark),CampusTone.AUTUMN_CITRUS,dark)
    private fun contrast(a: Color,b: Color)=(maxOf(a.luminance(),b.luminance())+.05f)/(minOf(a.luminance(),b.luminance())+.05f)

    @Test fun thirdEditionKeepsTheFirstEditionStructureAndOnlyUsesSandOnNavigation() {
        val c=scheme(false);val r=roles(false)
        assertEquals(Color(0xFFF8F5EC),c.surface);assertEquals(c.surface,c.background)
        assertEquals(Color(0xFFDDE0C9),r.header);assertEquals(Color(0xFF4C5237),r.navigation)
        assertEquals(Color(0xFFE1E4CE),r.planningHeading);assertEquals(Color(0xFFE5E6D6),r.wheelCenter)
        assertEquals(Color(0xFF4C5639),c.primary);assertEquals(Color(0xFFDAD5C3),r.periodIdle)
        assertEquals(Color(0xFFE4DDC8),r.navigationSelected);assertEquals(Color(0xFF3D492D),r.onNavigationSelected)
        assertEquals(Color(0xFFF2EEDC),r.navigationIdle);assertNotEquals(c.tertiary,r.navigationSelected)
        assertEquals(Color(0xFFD1CDBB),r.divider);assertNull(r.listDivider)
        assertEquals(Color(0xFF806340),r.settingsIcon);assertEquals(Color(0xFFD8CEB6),r.impressionFrame)
        val n=roles(true)
        assertEquals(Color(0xFF292D22),scheme(true).surface)
        assertEquals(Color(0xFF272F20),n.navigation);assertEquals(Color(0xFFDCD4BA),n.navigationSelected)
        assertEquals(Color(0xFF354128),n.onNavigationSelected);assertEquals(Color(0xFF59604A),n.periodIdle)
        assertTrue(r.hideHeaderDivider)
    }

    @Test fun mimosaSelectionsPaleLooseModeAndOrangeLaunchRemainIndependent() {
        for(dark in listOf(false,true)) {
            val c=scheme(dark);val r=roles(dark)
            assertEquals(c.tertiary,r.selectionFill);assertEquals(c.onTertiary,r.onSelection)
            assertEquals(c.tertiary,r.selectedSlot);assertEquals(c.onTertiary,r.onSelectedSlot)
            assertEquals(c.tertiaryContainer,r.enabledOption);assertEquals(c.tertiaryContainer,r.confirmation)
            assertEquals(c.onTertiaryContainer,r.onEnabledOption);assertFalse(r.outlineExpectedSelection)
            assertNotEquals(r.enabledOption,r.launch);assertNotEquals(r.selectionFill,r.navigationSelected)
            assertTrue(contrast(r.navigationSelected,r.navigation)>=3f)
        }
        val r=roles(false)
        assertEquals(Color(0xFF9AA066),r.selectionFill);assertEquals(Color(0xFFD9DDB9),r.enabledOption)
        assertEquals(Color(0xFF3F4B2E),r.onEnabledOption);assertEquals(Color(0xFF83552B),r.launch)
        assertEquals(Color(0xFFA05C27),r.favorite);assertEquals(Color(0xFF8A5426),r.selectedRoomAction)
        assertEquals(Color(0xFFB8C18C),roles(true).selectionFill)
    }

    @Test fun everyFunctionalPairRemainsReadableInBothModes() {
        for(dark in listOf(false,true)) {
            val c=scheme(dark);val r=roles(dark)
            listOf(r.header to r.onHeader,r.header to r.headerMuted,r.navigation to r.navigationIdle,
                r.navigationSelected to r.onNavigationSelected,r.planningHeading to r.onPlanningHeading,
                r.planningHeading to r.planningHeadingMuted,r.planningHeading to r.planningHeadingEntry,
                r.roomActions to r.onRoomActions,r.roomActions to r.selectedRoomAction,r.filter to r.onFilter,
                r.launch to r.onLaunch,r.enabledOption to r.onEnabledOption,r.confirmation to r.onConfirmation,
                r.wheelCenter to c.primary,c.surface to c.onSurface,c.surface to c.onSurfaceVariant,
                c.surface to r.settingsIcon,c.surface to r.favorite,c.surface to r.note,
                c.secondary to c.onSecondary,c.secondaryContainer to c.onSecondaryContainer,
                c.tertiary to c.onTertiary,c.tertiaryContainer to c.onTertiaryContainer
            ).forEach { (bg,fg)->assertTrue("dark=$dark $bg / $fg",contrast(bg,fg)>=4.5f) }
            assertTrue(contrast(c.primary,r.periodIdle!!)>=3f)
            assertTrue(contrast(r.switchThumb!!,r.switchTrackOn!!)>=3f)
            assertTrue(contrast(r.switchThumb,r.switchTrackOff!!)>=3f)
        }
    }

    @Test fun priorThemeLocalAccentsAndOriginalDefaultsAreUnaffected() {
        for(tone in CampusTone.entries.take(7)) for(dark in listOf(false,true)) {
            val c=campusColorScheme(Campus.XITUCHENG,dark,tone);val r=campusThemeRoles(c,tone,dark)
            assertNull(r.periodIdle);assertNull(r.listDivider);assertNull(r.switchTrackOn)
            assertNull(r.selectedSlot);assertNull(r.onSelectedSlot)
        }
        val old=campusThemeRoles(campusColorScheme(Campus.XITUCHENG,false,CampusTone.MORANDI),CampusTone.MORANDI)
        assertEquals(Color(0xFFCDC5B2),old.periodIdle);assertEquals(Color(0xFFCDC5B2),old.listDivider)
        assertEquals(CampusTone.SAND,AppearancePreferences().westTone);assertEquals(CampusTone.PINE,AppearancePreferences().shaheTone)
    }

    @Test fun stableIdPersistsIndependentlyAndCyclesAfterRedBean() {
        val stored=mutablePreferencesOf()
        val value=AppearancePreferences(CampusTone.AUTUMN_CITRUS,CampusTone.RED_BEAN,Campus.SHAHE)
        AppearancePreferenceKeys.write(stored,value)
        assertEquals("autumn_citrus",stored[AppearancePreferenceKeys.west]);assertEquals(value,AppearancePreferenceKeys.read(stored))
        assertEquals(listOf("sand","pine","matisse","dunhuang","old_port","peach_plum","burgundy","morandi","red_bean"),
            CampusTone.entries.take(9).map { it.id })
        assertEquals(CampusTone.AUTUMN_CITRUS,CampusTone.RED_BEAN.step(1))
        assertEquals(CampusTone.MORNING_BELL,CampusTone.AUTUMN_CITRUS.step(1))
        assertEquals(CampusTone.MONDRIAN,CampusTone.SAND.step(-1))
        assertEquals(scheme(false).surface,campusColorScheme(Campus.SHAHE,false,CampusTone.AUTUMN_CITRUS).surface)
        val red=campusThemeRoles(campusColorScheme(Campus.XITUCHENG,false,CampusTone.RED_BEAN),CampusTone.RED_BEAN)
        assertEquals(Color(0xFFB3392F),red.favorite);assertEquals(Color(0xFFE4DFD2),
            campusThemeRoles(campusColorScheme(Campus.XITUCHENG,false,CampusTone.MORANDI),CampusTone.MORANDI).wheelCenter)
    }
}

