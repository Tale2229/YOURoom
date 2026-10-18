package com.example.youroom

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.example.youroom.data.local.AppearancePreferenceKeys
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class PeachPlumThemeTest {
    private fun scheme(dark: Boolean)=campusColorScheme(Campus.XITUCHENG,dark,CampusTone.PEACH_PLUM)
    private fun roles(dark: Boolean)=campusThemeRoles(scheme(dark),CampusTone.PEACH_PLUM,dark)
    private fun contrast(a: Color,b: Color)=(maxOf(a.luminance(),b.luminance())+.05f)/(minOf(a.luminance(),b.luminance())+.05f)

    @Test fun approvedBrighterEditionOwnsPaperSmokeStructuresAndNeutralWheels() {
        val c=scheme(false);val r=roles(false)
        assertEquals(Color(0xFFFFF7F1),c.background);assertEquals(c.background,c.surface)
        assertEquals(Color(0xFFF3E7E0),c.surfaceContainer)
        assertEquals(Color(0xFF493F47),c.onSurface);assertEquals(Color(0xFF5D4F57),c.primary)
        assertEquals(Color(0xFF6B5B63),r.header);assertEquals(r.header,r.planningHeading)
        assertEquals(Color(0xFF574A52),r.navigation);assertEquals(r.navigation,r.roomActions)
        assertEquals(Color(0xFF935F56),r.settingsIcon)
        assertEquals(Color(0xFFEDE5DF),r.wheelCenter)
        assertEquals(Color(0xFF302A2E),scheme(true).surface)
        assertTrue(r.hideHeaderDivider)
    }

    @Test fun smokeLaunchAndWarmWhiteIconStayDistinctFromPeachLooseMode() {
        for(dark in listOf(false,true)) {
            val c=scheme(dark);val r=roles(dark)
            assertEquals(if(dark) Color(0xFF755C69) else Color(0xFF6B5B63),r.launch)
            assertEquals(Color(0xFFFFF8F1),r.onLaunch)
            assertEquals(c.tertiary,r.enabledOption);assertEquals(c.onTertiary,r.onEnabledOption)
            assertEquals(c.tertiary,r.confirmation);assertEquals(c.tertiary,r.navigationSelected)
            assertEquals(c.tertiary,r.selectedRoomAction)
            assertNotEquals(r.launch,r.enabledOption);assertNotEquals(r.wheelCenter,r.enabledOption)
        }
        assertEquals(Color(0xFFFAC7B7),roles(false).enabledOption)
        assertEquals(Color(0xFFF1C3B2),roles(true).enabledOption)
    }

    @Test fun peachSelectionsKeepDarkReadableTextInBothModesAndDoNotAlterOtherThemes() {
        for(dark in listOf(false,true)) {
            val r=roles(dark)
            assertEquals(r.enabledOption,r.selectionFill);assertEquals(Color(0xFF493F47),r.onSelection)
            assertFalse(r.outlineExpectedSelection)
            assertTrue(contrast(r.selectionFill!!,r.onSelection!!)>=4.5f)
        }
        for(tone in CampusTone.entries.take(6)) for(dark in listOf(false,true)) {
            val r=campusThemeRoles(campusColorScheme(Campus.XITUCHENG,dark,tone),tone,dark)
            assertNull(r.selectionFill);assertNull(r.onSelection)
        }
    }

    @Test fun functionalForegroundsRemainReadableInBothModes() {
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

    @Test fun stableIdPersistsPerCampusAndSevenExistingIdsAndDefaultsRemain() {
        val stored=mutablePreferencesOf()
        val value=AppearancePreferences(CampusTone.PEACH_PLUM,CampusTone.OLD_PORT,Campus.SHAHE)
        AppearancePreferenceKeys.write(stored,value)
        assertEquals("peach_plum",stored[AppearancePreferenceKeys.west]);assertEquals(value,AppearancePreferenceKeys.read(stored))
        assertEquals(listOf("sand","pine","slate","clay","matisse","dunhuang","old_port"),CampusTone.entries.take(7).map { it.id })
        assertEquals(CampusTone.BURGUNDY,CampusTone.PEACH_PLUM.step(1))
        assertEquals(CampusTone.MONDRIAN,CampusTone.SAND.step(-1))
        assertEquals(CampusTone.SAND,AppearancePreferences().westTone);assertEquals(CampusTone.PINE,AppearancePreferences().shaheTone)
        val other=campusColorScheme(Campus.SHAHE,false,CampusTone.PEACH_PLUM)
        assertEquals(scheme(false).background,other.background);assertEquals(scheme(false).tertiary,other.tertiary)
    }

    @Test fun previousHarborAndDunhuangRolesStayAsApproved() {
        val h=campusThemeRoles(campusColorScheme(Campus.XITUCHENG,false,CampusTone.OLD_PORT),CampusTone.OLD_PORT)
        val d=campusThemeRoles(campusColorScheme(Campus.XITUCHENG,false,CampusTone.DUNHUANG),CampusTone.DUNHUANG)
        assertEquals(Color(0xFF344D4B),h.launch);assertEquals(Color(0xFFDDC1A0),h.enabledOption)
        assertEquals(Color(0xFF3C5E52),d.launch);assertEquals(Color(0xFF8B5B3D),d.roomActionDivider)
    }
}
