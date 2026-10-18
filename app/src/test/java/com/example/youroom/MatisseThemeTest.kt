package com.example.youroom

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.youroom.data.local.AppearancePreferenceKeys
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.*
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.*
import org.junit.Test

class MatisseThemeTest {
    private fun scheme(dark: Boolean) = campusColorScheme(Campus.XITUCHENG,dark,CampusTone.MATISSE)
    private fun roles(dark: Boolean) = campusThemeRoles(scheme(dark),CampusTone.MATISSE,dark)
    private fun contrast(a: Color,b: Color): Float =
        (maxOf(a.luminance(),b.luminance())+.05f)/(minOf(a.luminance(),b.luminance())+.05f)

    @Test fun greenIsTheMainSurfaceAndBlueGreyActuallyFormsTheSecondaryPlanes() {
        val c=scheme(false); val r=roles(false)
        assertEquals(Color(0xFFE9EFE4),c.background)
        assertEquals(Color(0xFFD2DECD),c.surfaceContainer)
        assertEquals(Color(0xFF343E47),r.header)
        assertEquals(r.header,r.navigation);assertEquals(r.header,r.planningHeading)
        assertNotEquals(c.background,r.header)
        assertEquals(Color(0xFF8F603D),r.favorite)
        assertEquals(Color(0xFFAA3D36),r.launch)
        assertEquals(Color(0xFFE3EAAB),r.enabledOption)
    }
    @Test fun wheelCenterIsGreyGreenNotTheYellowGreenAccent() {
        for(dark in listOf(false,true)) {
            val c=scheme(dark);val r=roles(dark)
            assertNotEquals(r.enabledOption,r.wheelCenter)
            assertTrue(kotlin.math.abs(r.wheelCenter.green-r.wheelCenter.blue)<.08f)
            assertTrue(contrast(c.primary,r.wheelCenter)>=4.5f)
        }
        assertEquals(Color(0xFFD8E0D6),roles(false).wheelCenter)
    }
    @Test fun matisseHidesTheHeaderRuleAndSingleHueThemesKeepTheirOriginalDividers() {
        for(dark in listOf(false,true)) {
            val c=scheme(dark);val r=roles(dark)
            assertTrue(r.hideHeaderDivider)
            assertFalse(r.outlineExpectedSelection)
            assertEquals(c.tertiary,r.selectedRoomAction)
            assertNull(r.roomActionDivider)
            assertEquals(c.outlineVariant,r.divider)
            for(tone in listOf(CampusTone.SAND,CampusTone.PINE,CampusTone.SLATE,CampusTone.CLAY)) {
                val old=campusColorScheme(Campus.XITUCHENG,dark,tone)
                val role=campusThemeRoles(old,tone,dark)
                assertFalse(role.hideHeaderDivider);assertEquals(old.outlineVariant,role.divider)
                assertTrue(role.outlineExpectedSelection);assertNull(role.roomActionDivider)
            }
        }
    }
    @Test fun everyFunctionalForegroundHasReadableContrastInBothModes() {
        for(dark in listOf(false,true)) {
            val c=scheme(dark);val r=roles(dark)
            listOf(r.header to r.onHeader,r.header to r.headerMuted,
                r.navigationSelected to r.onNavigationSelected,r.navigation to r.navigationIdle,
                r.planningHeading to r.onPlanningHeading,r.planningHeading to r.planningHeadingMuted,
                r.roomActions to r.onRoomActions,r.roomActions to r.selectedRoomAction,
                r.launch to r.onLaunch,r.enabledOption to r.onEnabledOption,
                r.filter to r.onFilter,r.wheelCenter to c.primary,
                c.surface to r.favorite,c.surface to r.note,c.surface to r.settingsIcon,
                c.secondary to c.onSecondary,c.secondaryContainer to c.onSecondaryContainer,
                c.tertiary to c.onTertiary,c.tertiaryContainer to c.onTertiaryContainer
            ).forEach { (bg,fg)-> assertTrue("$dark $bg / $fg",contrast(bg,fg)>=4.5f) }
        }
    }
    @Test fun theFourExistingThemesKeepTheirOriginalFunctionalRoles() {
        for(tone in listOf(CampusTone.SAND,CampusTone.PINE,CampusTone.SLATE,CampusTone.CLAY)) for(dark in listOf(false,true)) {
            val c=campusColorScheme(Campus.XITUCHENG,dark,tone)
            val r=campusThemeRoles(c,tone,dark)
            assertEquals(c.surface,r.header);assertEquals(c.onSurface,r.onHeader)
            assertEquals(c.primary,r.launch);assertEquals(c.onPrimary,r.onLaunch)
            assertEquals(c.primary,r.settingsIcon);assertEquals(c.primary,r.favorite)
            assertEquals(c.surfaceContainer,r.roomActions)
            assertEquals(c.primary.copy(alpha=.045f),r.wheelCenter)
        }
    }
    @Test fun matissePersistsPerCampusWithoutChangingExistingIdsOrDefaults() {
        val stored=mutablePreferencesOf()
        val selected=AppearancePreferences().withTone(Campus.XITUCHENG,CampusTone.MATISSE)
        AppearancePreferenceKeys.write(stored,selected)
        assertEquals("matisse",stored[AppearancePreferenceKeys.west])
        assertEquals(selected,AppearancePreferenceKeys.read(stored))
        assertEquals(CampusTone.PINE,selected.shaheTone)
        assertEquals(CampusTone.SAND,AppearancePreferences().westTone)
        assertEquals(CampusTone.MATISSE,CampusTone.CLAY.step(1))
    }
}
