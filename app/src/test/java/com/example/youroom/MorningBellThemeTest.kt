package com.example.youroom

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.example.youroom.data.local.AppearancePreferenceKeys
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class MorningBellThemeTest {
    private fun scheme(dark: Boolean)=campusColorScheme(Campus.XITUCHENG,dark,CampusTone.MORNING_BELL)
    private fun roles(dark: Boolean)=campusThemeRoles(scheme(dark),CampusTone.MORNING_BELL,dark)
    private fun contrast(a: Color,b: Color)=(maxOf(a.luminance(),b.luminance())+.05f)/(minOf(a.luminance(),b.luminance())+.05f)

    @Test fun approvedFirstEditionRetainsItsSkyPaperWheelAndNeutralLines() {
        val c=scheme(false);val r=roles(false)
        assertEquals(Color(0xFFF5F6FA),c.surface);assertEquals(c.surface,c.background)
        assertEquals(Color(0xFF8EA4CA),r.header);assertEquals(Color(0xFFC5D2E7),r.navigation)
        assertEquals(Color(0xFFD8E2F1),r.planningHeading);assertEquals(Color(0xFFE5E8EE),r.wheelCenter)
        assertEquals(Color(0xFF455E88),c.primary);assertEquals(Color(0xFFD0DAEA),r.periodIdle)
        assertEquals(Color(0xFFCDD5E2),r.divider);assertNull(r.listDivider)
        assertEquals(Color(0xFF222936),scheme(true).surface)
        assertEquals(Color(0xFF3F5578),roles(true).header)
        assertEquals(Color(0xFFB2C7EB),scheme(true).primary)
        assertEquals(Color(0xFF4D5C75),roles(true).periodIdle)
        assertNotEquals(Color(0xFFA8C6EA),r.header)
        assertNotEquals(Color(0xFF77608F),c.primary)
        assertNotEquals(Color(0xFFE8BAB4),r.divider)
    }

    @Test fun vermilionActionsAndDuskCornerAreIndependentFromPlanStars() {
        val r=roles(false);val n=roles(true)
        assertEquals(Color(0xFF9F463B),r.selectedRoomAction)
        assertEquals(Color(0xFF625466),r.favoriteCorner)
        assertEquals(Color(0xFFE59C8E),n.selectedRoomAction)
        assertEquals(Color(0xFFE2CDDF),n.favoriteCorner)
        assertEquals(r.favorite,r.selectedRoomAction);assertEquals(n.favorite,n.selectedRoomAction)
        assertNotEquals(r.favorite,r.favoriteCorner);assertNotEquals(n.favorite,n.favoriteCorner)
        assertEquals(Color(0xFFAEBBCF),r.roomActionDivider)
        assertEquals(Color(0xFF7D90AD),n.roomActionDivider)
    }

    @Test fun blueSelectionsAndLaunchRemainDistinctFromPaleDuskLooseMode() {
        for(dark in listOf(false,true)) {
            val c=scheme(dark);val r=roles(dark)
            assertEquals(r.navigationSelected,r.selectionFill);assertEquals(r.navigationSelected,r.selectedSlot)
            assertEquals(c.onPrimary,r.onSelection);assertEquals(c.onPrimary,r.onSelectedSlot)
            assertEquals(c.tertiaryContainer,r.enabledOption);assertEquals(c.onTertiaryContainer,r.onEnabledOption)
            assertEquals(r.enabledOption,r.confirmation);assertEquals(r.onEnabledOption,r.onConfirmation)
            assertNotEquals(r.enabledOption,r.launch);assertFalse(r.outlineExpectedSelection)
            assertTrue(r.hideHeaderDivider)
        }
        assertEquals(Color(0xFFE1DDE7),roles(false).enabledOption)
        assertEquals(Color(0xFF455E88),roles(false).launch)
        assertEquals(Color(0xFF696D83),roles(false).settingsIcon)
    }

    @Test fun textAndInformationStayReadableInBothModes() {
        for(dark in listOf(false,true)) {
            val c=scheme(dark);val r=roles(dark)
            listOf(r.header to r.onHeader,r.header to r.headerMuted,r.navigation to r.navigationIdle,
                r.navigationSelected to r.onNavigationSelected,r.planningHeading to r.onPlanningHeading,
                r.planningHeading to r.planningHeadingMuted,r.planningHeading to r.planningHeadingEntry,
                r.roomActions to r.onRoomActions,r.filter to r.onFilter,r.launch to r.onLaunch,
                r.enabledOption to r.onEnabledOption,r.wheelCenter to c.primary,c.surface to c.onSurface,
                c.surface to c.onSurfaceVariant,c.surface to r.settingsIcon,c.surface to r.favorite,
                c.surface to r.favoriteCorner!!,c.secondary to c.onSecondary,
                c.secondaryContainer to c.onSecondaryContainer,c.tertiary to c.onTertiary,
                c.tertiaryContainer to c.onTertiaryContainer
            ).forEach { (bg,fg)->assertTrue("dark=$dark $bg / $fg",contrast(bg,fg)>=4.5f) }
            // These are icons, not small text; keep the approved first-edition dark rail.
            assertTrue(contrast(r.roomActions,r.selectedRoomAction)>=3f)
            assertTrue(contrast(c.primary,r.periodIdle!!)>=3f)
            assertTrue(contrast(r.switchThumb!!,r.switchTrackOn!!)>=3f)
            assertTrue(contrast(r.switchThumb,r.switchTrackOff!!)>=3f)
        }
    }

    @Test fun originalCornerFallbackAndDefaultsRemainUnchanged() {
        for(tone in CampusTone.entries.take(12)) for(dark in listOf(false,true)) {
            val c=campusColorScheme(Campus.XITUCHENG,dark,tone)
            val r=campusThemeRoles(c,tone,dark)
            assertNull("$tone must retain its original favorite corner",r.favoriteCorner)
        }
        assertEquals(CampusTone.SAND,AppearancePreferences().westTone)
        assertEquals(CampusTone.PINE,AppearancePreferences().shaheTone)
        val r=campusThemeRoles(campusColorScheme(Campus.XITUCHENG,false,CampusTone.RED_BEAN),CampusTone.RED_BEAN)
        assertEquals(Color(0xFFB3392F),r.favorite)
    }

    @Test fun stableIdPersistsAndNewThemeAppendsWithoutRenumberingPreviousChoices() {
        val stored=mutablePreferencesOf();val value=AppearancePreferences(CampusTone.MORNING_BELL,CampusTone.AUTUMN_CITRUS,Campus.SHAHE)
        AppearancePreferenceKeys.write(stored,value)
        assertEquals("morning_bell",stored[AppearancePreferenceKeys.west])
        assertEquals(value,AppearancePreferenceKeys.read(stored));assertEquals("晨钟暮鼓",CampusTone.MORNING_BELL.title)
        assertEquals(listOf("sand","pine","slate","clay","matisse","dunhuang","old_port","peach_plum","burgundy","morandi","red_bean","autumn_citrus"),CampusTone.entries.take(12).map { it.id })
        assertEquals(CampusTone.MORNING_BELL,CampusTone.AUTUMN_CITRUS.step(1))
        assertEquals(CampusTone.MONDRIAN,CampusTone.MORNING_BELL.step(1))
        assertEquals(CampusTone.MONDRIAN,CampusTone.SAND.step(-1))
        assertEquals(scheme(false).surface,campusColorScheme(Campus.SHAHE,false,CampusTone.MORNING_BELL).surface)
    }
}
