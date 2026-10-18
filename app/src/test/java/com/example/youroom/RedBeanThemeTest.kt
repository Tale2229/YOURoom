package com.example.youroom

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.example.youroom.data.local.AppearancePreferenceKeys
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class RedBeanThemeTest {
    private fun scheme(dark: Boolean)=campusColorScheme(Campus.XITUCHENG,dark,CampusTone.RED_BEAN)
    private fun roles(dark: Boolean)=campusThemeRoles(scheme(dark),CampusTone.RED_BEAN,dark)
    private fun contrast(a: Color,b: Color)=(maxOf(a.luminance(),b.luminance())+.05f)/(minOf(a.luminance(),b.luminance())+.05f)

    @Test fun secondEditionSilverAndPearlMatchTheAcceptedPreview() {
        val c=scheme(false);val r=roles(false)
        assertEquals(Color(0xFFFAF9F6),c.surface);assertEquals(c.surface,c.background)
        assertEquals(Color(0xFFE3E4E6),r.header);assertEquals(Color(0xFFD4D7DB),r.navigation)
        assertEquals(Color(0xFFECEEF0),r.planningHeading);assertEquals(Color(0xFFE8EAEB),r.wheelCenter)
        assertEquals(Color(0xFF4E5B63),c.primary);assertEquals(Color(0xFFD2D4D6),r.periodIdle)
        assertEquals(Color(0xFF647582),r.settingsIcon);assertEquals(Color(0xFFD3D9DF),r.impressionFrame)
        assertEquals(Color(0xFFD1D4D6),r.divider);assertNull(r.listDivider)
        assertEquals(Color(0xFF25282D),scheme(true).surface)
        assertEquals(Color(0xFF3C434D),roles(true).header);assertEquals(Color(0xFF313943),roles(true).navigation)
        assertEquals(Color(0xFF555E69),roles(true).periodIdle)
        assertTrue(r.hideHeaderDivider)
    }

    @Test fun redBeanSelectionsStaySeparateFromPaleLooseModeAndDeepLaunch() {
        for(dark in listOf(false,true)) {
            val c=scheme(dark);val r=roles(dark)
            assertEquals(c.tertiary,r.selectionFill);assertEquals(c.onTertiary,r.onSelection)
            assertEquals(c.tertiary,r.favorite);assertEquals(c.tertiaryContainer,r.confirmation)
            assertEquals(c.tertiary,r.selectedSlot);assertEquals(c.onTertiary,r.onSelectedSlot)
            assertEquals(c.tertiaryContainer,r.enabledOption);assertEquals(c.onTertiaryContainer,r.onEnabledOption)
            assertNotEquals(r.enabledOption,r.launch);assertNotEquals(r.selectionFill,r.enabledOption)
            assertFalse(r.outlineExpectedSelection)
        }
        val r=roles(false)
        assertEquals(Color(0xFFB3392F),r.favorite);assertEquals(Color(0xFFEAD4CC),r.enabledOption)
        assertEquals(Color(0xFF773D36),r.onEnabledOption);assertEquals(Color(0xFF96372F),r.launch)
        assertEquals(Color(0xFFB3392F),r.navigationSelected)
        assertEquals(Color(0xFFA94D42),roles(true).navigationSelected)
        assertEquals(Color(0xFFE49A88),roles(true).selectionFill)
        assertEquals(Color(0xFF9B362E),r.selectedRoomAction)
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
        for(tone in CampusTone.entries.take(9)) for(dark in listOf(false,true)) {
            val c=campusColorScheme(Campus.XITUCHENG,dark,tone);val r=campusThemeRoles(c,tone,dark)
            assertNull(r.periodIdle);assertNull(r.listDivider);assertNull(r.switchTrackOn)
            assertNull(r.selectedSlot);assertNull(r.onSelectedSlot)
        }
        val old=campusThemeRoles(campusColorScheme(Campus.XITUCHENG,false,CampusTone.MORANDI),CampusTone.MORANDI)
        assertEquals(Color(0xFFCDC5B2),old.periodIdle);assertEquals(Color(0xFFCDC5B2),old.listDivider)
        assertEquals(CampusTone.SAND,AppearancePreferences().westTone);assertEquals(CampusTone.PINE,AppearancePreferences().shaheTone)
    }

    @Test fun stableIdPersistsIndependentlyAndCyclesAfterMorandi() {
        assertEquals("入骨相思",CampusTone.RED_BEAN.title)
        val stored=mutablePreferencesOf()
        val value=AppearancePreferences(CampusTone.RED_BEAN,CampusTone.MORANDI,Campus.SHAHE)
        AppearancePreferenceKeys.write(stored,value)
        assertEquals("red_bean",stored[AppearancePreferenceKeys.west]);assertEquals(value,AppearancePreferenceKeys.read(stored))
        assertEquals(listOf("sand","pine","slate","clay","matisse","dunhuang","old_port","peach_plum","burgundy","morandi"),
            CampusTone.entries.take(10).map { it.id })
        assertEquals(CampusTone.RED_BEAN,CampusTone.MORANDI.step(1));assertEquals(CampusTone.AUTUMN_CITRUS,CampusTone.RED_BEAN.step(1))
        assertEquals(CampusTone.MONDRIAN,CampusTone.SAND.step(-1))
        assertEquals(scheme(false).surface,campusColorScheme(Campus.SHAHE,false,CampusTone.RED_BEAN).surface)
    }
}

