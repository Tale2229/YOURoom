package com.example.youroom

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.example.youroom.data.local.AppearancePreferenceKeys
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class MondrianThemeTest {
    private fun scheme(dark: Boolean)=campusColorScheme(Campus.XITUCHENG,dark,CampusTone.MONDRIAN)
    private fun roles(dark: Boolean)=campusThemeRoles(scheme(dark),CampusTone.MONDRIAN,dark)
    private fun contrast(a: Color,b: Color)=(maxOf(a.luminance(),b.luminance())+.05f)/(minOf(a.luminance(),b.luminance())+.05f)

    @Test fun fourSampledColorsOwnTheirApprovedRolesWithoutTeal() {
        for(dark in listOf(false,true)) {
            val c=scheme(dark);val r=roles(dark)
            assertEquals(Color(0xFF274997),r.header)
            assertEquals(Color(0xFF060102),r.navigation)
            assertEquals(Color(0xFFCC3828),r.launch)
            assertEquals(Color(0xFFEEE95D),r.navigationSelected)
            assertEquals(Color(0xFFEEE95D),r.enabledOption)
            assertEquals(Color(0xFF060102),r.onEnabledOption)
            assertNull(r.favoriteCorner);assertNull(r.listDivider)
            listOf(r.header,r.navigation,r.launch,r.enabledOption,r.roomActions,r.selectedRoomAction,
                r.favorite,r.settingsIcon,c.primary,c.surface,c.secondary,c.tertiary).forEach {
                assertNotEquals(Color(0xFF6CB9BC),it);assertNotEquals(Color(0xFF25D4D0),it)
            }
        }
    }

    @Test fun neutralPaperTimeBarsAndWheelKeepTheFirstEditionValues() {
        val c=scheme(false);val r=roles(false);val n=roles(true)
        assertEquals(Color(0xFFFAFAF6),c.surface);assertEquals(c.surface,c.background)
        assertEquals(Color(0xFF060102),c.primary);assertEquals(Color(0xFFD4D3CA),r.periodIdle)
        assertEquals(Color(0xFFE8E8E3),r.wheelCenter);assertEquals(Color(0xFFD4D3CA),r.divider)
        assertEquals(Color(0xFF222022),scheme(true).surface)
        assertEquals(Color(0xFFEEEDE7),scheme(true).primary)
        assertEquals(Color(0xFF605C58),n.periodIdle);assertEquals(Color(0xFF3A3738),n.wheelCenter)
        assertEquals(Color(0xFF55514E),n.divider)
    }

    @Test fun coloredSelectionsStayIndependentFromNeutralTimeInformation() {
        val r=roles(false);val n=roles(true)
        assertEquals(Color(0xFF274997),r.selectionFill);assertEquals(r.selectionFill,r.selectedSlot)
        assertEquals(Color.White,r.onSelection)
        assertEquals(Color(0xFFEEE95D),n.selectionFill);assertEquals(n.selectionFill,n.selectedSlot)
        assertEquals(Color(0xFF060102),n.onSelection)
        assertEquals(Color(0xFFCC3828),r.favorite);assertEquals(r.favorite,r.selectedRoomAction)
        assertEquals(Color(0xFFF19A8F),n.favorite);assertEquals(Color(0xFFEEE95D),n.selectedRoomAction)
        assertEquals(Color(0xFF274997),r.settingsIcon);assertEquals(Color(0xFFA5BAE8),n.settingsIcon)
        assertNotEquals(r.launch,r.enabledOption);assertNotEquals(n.launch,n.enabledOption)
        assertTrue(r.hideHeaderDivider);assertFalse(r.outlineExpectedSelection)
    }

    @Test fun textAndStatusPairsStayReadableInBothModes() {
        for(dark in listOf(false,true)) {
            val c=scheme(dark);val r=roles(dark)
            listOf(r.header to r.onHeader,r.header to r.headerMuted,r.navigation to r.navigationIdle,
                r.navigationSelected to r.onNavigationSelected,r.planningHeading to r.onPlanningHeading,
                r.planningHeading to r.planningHeadingMuted,r.planningHeading to r.planningHeadingEntry,
                r.roomActions to r.onRoomActions,r.filter to r.onFilter,
                r.launch to r.onLaunch,r.enabledOption to r.onEnabledOption,r.wheelCenter to c.primary,
                c.surface to c.onSurface,c.surface to c.onSurfaceVariant,c.surface to r.settingsIcon,
                c.surface to r.favorite,c.secondary to c.onSecondary,c.secondaryContainer to c.onSecondaryContainer,
                c.tertiary to c.onTertiary,c.tertiaryContainer to c.onTertiaryContainer,
                r.selectionFill!! to r.onSelection!!,r.selectedSlot!! to r.onSelectedSlot!!
            ).forEach { (bg,fg)->assertTrue("dark=$dark $bg / $fg",contrast(bg,fg)>=4.5f) }
            assertTrue(contrast(c.primary,r.periodIdle!!)>=3f)
            // The action rail contains icons, not body text; preserve the approved sampled red.
            assertTrue(contrast(r.roomActions,r.selectedRoomAction)>=3f)
            assertTrue(contrast(r.switchThumb!!,r.switchTrackOn!!)>=3f)
            assertTrue(contrast(r.switchThumb,r.switchTrackOff!!)>=3f)
        }
    }

    @Test fun previousThemeAndCampusDefaultsAreNotReplaced() {
        val c=campusColorScheme(Campus.XITUCHENG,false,CampusTone.MORNING_BELL)
        val r=campusThemeRoles(c,CampusTone.MORNING_BELL)
        assertEquals(Color(0xFFF5F6FA),c.surface);assertEquals(Color(0xFF8EA4CA),r.header)
        assertEquals(Color(0xFF625466),r.favoriteCorner);assertEquals(Color(0xFF9F463B),r.selectedRoomAction)
        assertEquals(CampusTone.SAND,AppearancePreferences().westTone)
        assertEquals(CampusTone.PINE,AppearancePreferences().shaheTone)
    }

    @Test fun stableIdAppendsAfterMorningBellAndPersistsPerCampus() {
        val stored=mutablePreferencesOf();val value=AppearancePreferences(CampusTone.MONDRIAN,CampusTone.MORNING_BELL,Campus.SHAHE)
        AppearancePreferenceKeys.write(stored,value)
        assertEquals("mondrian",stored[AppearancePreferenceKeys.west])
        assertEquals(value,AppearancePreferenceKeys.read(stored));assertEquals("蒙德里安",CampusTone.MONDRIAN.title)
        assertEquals(listOf("sand","pine","slate","clay","matisse","dunhuang","old_port","peach_plum","burgundy","morandi","red_bean","autumn_citrus","morning_bell"),CampusTone.entries.take(13).map { it.id })
        assertEquals(CampusTone.MONDRIAN,CampusTone.MORNING_BELL.step(1))
        assertEquals(CampusTone.SAND,CampusTone.MONDRIAN.step(1))
        assertEquals(CampusTone.MONDRIAN,CampusTone.SAND.step(-1))
        assertEquals(scheme(false).surface,campusColorScheme(Campus.SHAHE,false,CampusTone.MONDRIAN).surface)
    }
}
