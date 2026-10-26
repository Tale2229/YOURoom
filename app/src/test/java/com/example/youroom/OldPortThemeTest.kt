package com.example.youroom

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.example.youroom.data.local.AppearancePreferenceKeys
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class OldPortThemeTest {
    private fun scheme(dark: Boolean)=campusColorScheme(Campus.XITUCHENG,dark,CampusTone.OLD_PORT)
    private fun roles(dark: Boolean)=campusThemeRoles(scheme(dark),CampusTone.OLD_PORT,dark)
    private fun contrast(a: Color,b: Color)=(maxOf(a.luminance(),b.luminance())+.05f)/(minOf(a.luminance(),b.luminance())+.05f)

    @Test fun approvedSecondEditionOwnsBothPaperAndStructure() {
        val c=scheme(false);val r=roles(false)
        assertEquals(Color(0xFFF3ECE0),c.background);assertEquals(c.background,c.surface)
        assertEquals(Color(0xFFE5DDCE),c.surfaceContainer)
        assertEquals(Color(0xFF354747),c.onSurface)
        assertEquals(Color(0xFF516D6C),r.header);assertEquals(r.header,r.planningHeading)
        assertEquals(Color(0xFF344D4B),r.navigation);assertEquals(r.navigation,r.roomActions)
        assertEquals(Color(0xFF885F48),r.settingsIcon);assertEquals(r.settingsIcon,r.favorite)
        assertEquals(Color(0xFF292F2A),scheme(true).surface)
        assertTrue(r.hideHeaderDivider)
    }

    @Test fun launchUsesDeepHarborAndWarmPaperNotTheAlmondLooseModeFill() {
        for(dark in listOf(false,true)) {
            val c=scheme(dark);val r=roles(dark)
            assertEquals(if(dark) Color(0xFF3D5955) else Color(0xFF344D4B),r.launch)
            assertEquals(Color(0xFFF8F0E1),r.onLaunch)
            assertEquals(c.tertiary,r.enabledOption);assertEquals(c.onTertiary,r.onEnabledOption)
            assertEquals(c.tertiary,r.confirmation);assertEquals(c.onTertiary,r.onConfirmation)
            assertNotEquals(r.launch,r.enabledOption)
            assertEquals(c.tertiary,r.navigationSelected);assertEquals(c.tertiary,r.selectedRoomAction)
            assertNotEquals(r.wheelCenter,r.enabledOption)
        }
        assertEquals(Color(0xFFDDC1A0),roles(false).enabledOption)
        assertEquals(Color(0xFFDCC09B),roles(true).enabledOption)
    }

    @Test fun selectedChipsAndExpectedRoomsCanKeepAlmondWithDarkInkAtNight() {
        for(dark in listOf(false,true)) {
            val r=roles(dark)
            assertEquals(r.enabledOption,r.selectionFill)
            assertEquals(Color(0xFF344D4B),r.onSelection)
            assertFalse(r.outlineExpectedSelection)
            assertTrue(contrast(r.selectionFill!!,r.onSelection!!)>=4.5f)
        }
        for(tone in CampusTone.entries.take(4)) for(dark in listOf(false,true)) {
            val r=campusThemeRoles(campusColorScheme(Campus.XITUCHENG,dark,tone),tone,dark)
            assertNull(r.selectionFill);assertNull(r.onSelection)
        }
    }

    @Test fun functionalForegroundsStayReadableInBothModes() {
        for(dark in listOf(false,true)) {
            val c=scheme(dark);val r=roles(dark)
            listOf(r.header to r.onHeader,r.header to r.headerMuted,r.navigation to r.navigationIdle,
                r.navigationSelected to r.onNavigationSelected,r.planningHeading to r.onPlanningHeading,
                r.planningHeading to r.planningHeadingMuted,r.planningHeading to r.planningHeadingEntry,
                r.roomActions to r.onRoomActions,r.roomActions to r.selectedRoomAction,r.filter to r.onFilter,
                r.launch to r.onLaunch,r.enabledOption to r.onEnabledOption,r.confirmation to r.onConfirmation,
                r.wheelCenter to c.primary,c.surface to c.onSurface,c.surface to c.onSurfaceVariant,
                c.surface to r.settingsIcon,c.surface to r.themeArrow,c.secondary to c.onSecondary,
                c.secondaryContainer to c.onSecondaryContainer,c.tertiary to c.onTertiary,
                c.tertiaryContainer to c.onTertiaryContainer
            ).forEach { (bg,fg)->assertTrue("dark=$dark $bg / $fg",contrast(bg,fg)>=4.5f) }
        }
    }

    @Test fun stableIdPersistsPerCampusAndTheOtherSixIdsRemainUntouched() {
        val stored=mutablePreferencesOf()
        val value=AppearancePreferences(CampusTone.OLD_PORT,CampusTone.DUNHUANG,Campus.SHAHE)
        AppearancePreferenceKeys.write(stored,value)
        assertEquals("old_port",stored[AppearancePreferenceKeys.west])
        assertEquals(value,AppearancePreferenceKeys.read(stored))
        assertEquals(listOf("sand","pine","matisse","dunhuang"),CampusTone.entries.take(4).map { it.id })
        assertEquals(CampusTone.OLD_PORT,CampusTone.DUNHUANG.step(1))
        assertEquals(CampusTone.DUNHUANG,CampusTone.OLD_PORT.step(-1))
        assertEquals(CampusTone.SAND,AppearancePreferences().westTone)
        assertEquals(CampusTone.PINE,AppearancePreferences().shaheTone)
        val other=campusColorScheme(Campus.SHAHE,false,CampusTone.OLD_PORT)
        assertEquals(scheme(false).background,other.background)
        assertEquals(scheme(false).primary,other.primary)
        assertEquals(scheme(false).tertiary,other.tertiary)
    }

    @Test fun existingMultiHueThemesKeepTheirRecentlyApprovedRoles() {
        val m=campusThemeRoles(campusColorScheme(Campus.XITUCHENG,false,CampusTone.MATISSE),CampusTone.MATISSE)
        val d=campusThemeRoles(campusColorScheme(Campus.XITUCHENG,false,CampusTone.DUNHUANG),CampusTone.DUNHUANG)
        assertEquals(Color(0xFFAA3D36),m.launch);assertEquals(Color(0xFFE3EAAB),m.selectedRoomAction)
        assertEquals(Color(0xFF3C5E52),d.launch);assertEquals(d.launch,d.selectedRoomAction)
        assertEquals(Color(0xFF8B5B3D),d.roomActionDivider)
        assertEquals(Color(0xFF894437),d.planningHeading)
    }
}
