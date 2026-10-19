package com.example.youroom

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.example.youroom.data.local.AppearancePreferenceKeys
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class DunhuangThemeTest {
    private fun scheme(dark: Boolean)=campusColorScheme(Campus.XITUCHENG,dark,CampusTone.DUNHUANG)
    private fun roles(dark: Boolean)=campusThemeRoles(scheme(dark),CampusTone.DUNHUANG,dark)
    private fun contrast(a: Color,b: Color)=(maxOf(a.luminance(),b.luminance())+.05f)/(minOf(a.luminance(),b.luminance())+.05f)

    @Test fun fourthEditionKeepsSandPaperRustHeaderBrownNavigationAndWarmHeading() {
        val c=scheme(false);val r=roles(false)
        assertEquals(Color(0xFFF1EBE2),c.background);assertEquals(c.background,c.surface)
        assertEquals(Color(0xFF513B32),c.onSurface)
        assertEquals(Color(0xFFA64C3B),r.header)
        assertEquals(Color(0xFF6B3B32),r.navigation)
        assertEquals(Color(0xFF894437),r.planningHeading)
        assertEquals(Color(0xFFFFF4E8),r.onPlanningHeading)
        assertEquals(Color(0xFFEBD0B5),r.planningHeadingMuted)
        assertEquals(r.planningHeadingMuted,r.planningHeadingEntry)
        assertEquals(Color(0xFFB78F68),r.planningHeadingKey)
        assertEquals(Color(0xFFDCC6AC),r.roomActions)
    }

    @Test fun lightGreenStatesAreIndependentOfDeepGreenLaunchAndBriefFeedbackInBothModes() {
        for(dark in listOf(false,true)) {
            val r=roles(dark)
            assertEquals(Color(0xFF8FBFB0),r.navigationSelected)
            assertEquals(r.navigationSelected,r.enabledOption)
            assertEquals(Color(0xFF244C41),r.onEnabledOption)
            assertEquals(r.onEnabledOption,r.onNavigationSelected)
            assertEquals(if(dark) Color(0xFF456759) else Color(0xFF3C5E52),r.launch)
            assertEquals(r.launch,r.confirmation)
            assertEquals(r.launch,r.selectedRoomAction)
            assertEquals(r.themeArrow,r.roomActionDivider)
            assertTrue(r.outlineExpectedSelection)
            assertEquals(Color(0xFFF4EFE3),r.onLaunch);assertEquals(r.onLaunch,r.onConfirmation)
            assertNotEquals(r.enabledOption,r.confirmation)
        }
    }

    @Test fun themeArrowsStayWarmAndWheelCentersStayNeutral() {
        val day=roles(false);val night=roles(true)
        assertEquals(Color(0xFF8B5B3D),day.themeArrow)
        assertEquals(Color(0xFFD3B38D),night.themeArrow)
        assertNotEquals(day.settingsIcon,day.themeArrow)
        assertEquals(Color(0xFFE2DDD5),day.wheelCenter)
        assertEquals(Color(0xFF443E34),night.wheelCenter)
        assertTrue(day.hideHeaderDivider);assertTrue(night.hideHeaderDivider)
        assertEquals(scheme(false).outlineVariant,day.divider)
    }

    @Test fun everyForegroundRemainsReadableIncludingWarmAndCoolRoles() {
        for(dark in listOf(false,true)) {
            val c=scheme(dark);val r=roles(dark)
            listOf(r.header to r.onHeader,r.header to r.headerMuted,
                r.navigation to r.navigationIdle,r.navigationSelected to r.onNavigationSelected,
                r.planningHeading to r.onPlanningHeading,r.planningHeading to r.planningHeadingMuted,
                r.roomActions to r.onRoomActions,
                r.filter to r.onFilter,r.launch to r.onLaunch,r.enabledOption to r.onEnabledOption,
                r.confirmation to r.onConfirmation,c.surface to r.settingsIcon,c.surface to r.themeArrow,
                c.surface to r.favorite,c.surface to r.note,r.wheelCenter to c.primary,
                c.secondary to c.onSecondary,c.secondaryContainer to c.onSecondaryContainer,
                c.tertiary to c.onTertiary,c.tertiaryContainer to c.onTertiaryContainer
            ).forEach { (bg,fg)->assertTrue("dark=$dark $bg / $fg",contrast(bg,fg)>=4.5f) }
            // These are 22dp non-text symbols, rather than text foregrounds.
            assertTrue("Selected rail icons dark=$dark",contrast(r.roomActions,r.selectedRoomAction)>=3f)
        }
    }

    @Test fun dunhuangRoundTripsPerCampusWithoutChangingOldKeysDefaultsOrSelections() {
        val stored=mutablePreferencesOf()
        val selected=AppearancePreferences(westTone=CampusTone.DUNHUANG,shaheTone=CampusTone.MATISSE)
        AppearancePreferenceKeys.write(stored,selected)
        assertEquals("dunhuang",stored[AppearancePreferenceKeys.west])
        assertEquals("matisse",stored[AppearancePreferenceKeys.shahe])
        assertEquals(selected,AppearancePreferenceKeys.read(stored))
        assertEquals(CampusTone.SAND,AppearancePreferences().westTone)
        assertEquals(CampusTone.PINE,AppearancePreferences().shaheTone)
        for(campus in Campus.entries) {
            val other=campusColorScheme(campus,false,CampusTone.DUNHUANG)
            assertEquals(scheme(false).background,other.background)
            assertEquals(scheme(false).primary,other.primary)
            assertEquals(scheme(false).secondary,other.secondary)
        }
        assertEquals(CampusTone.OLD_PORT,CampusTone.DUNHUANG.step(1))
    }

    @Test fun addingRolesDoesNotChangeExistingConfirmationOrArrowColors() {
        for(tone in CampusTone.entries.takeWhile { it != CampusTone.DUNHUANG }) for(dark in listOf(false,true)) {
            val r=campusThemeRoles(campusColorScheme(Campus.XITUCHENG,dark,tone),tone,dark)
            assertEquals(r.settingsIcon,r.themeArrow)
            assertEquals(r.onPlanningHeading,r.planningHeadingEntry)
            assertEquals(r.enabledOption,r.confirmation);assertEquals(r.onEnabledOption,r.onConfirmation)
        }
    }
}
