package com.example.youroom

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.example.youroom.data.local.AppearancePreferenceKeys
import com.example.youroom.domain.*
import com.example.youroom.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class BurgundyThemeTest {
    private fun scheme(dark: Boolean)=campusColorScheme(Campus.XITUCHENG,dark,CampusTone.BURGUNDY)
    private fun roles(dark: Boolean)=campusThemeRoles(scheme(dark),CampusTone.BURGUNDY,dark)
    private fun contrast(a: Color,b: Color)=(maxOf(a.luminance(),b.luminance())+.05f)/(minOf(a.luminance(),b.luminance())+.05f)

    @Test fun acceptedThirdEditionOwnsIvoryPaperOakStructuresAndNeutralWheel() {
        val c=scheme(false);val r=roles(false)
        assertEquals(Color(0xFFF6F0E6),c.background);assertEquals(c.background,c.surface)
        assertEquals(Color(0xFFE9DED1),c.surfaceContainer)
        assertEquals(Color(0xFF45332C),c.onSurface);assertEquals(Color(0xFF64433B),c.primary)
        assertEquals(Color(0xFF713F3D),r.header);assertEquals(Color(0xFF6B4439),r.planningHeading)
        assertEquals(Color(0xFF49312C),r.navigation);assertEquals(r.navigation,r.roomActions)
        assertEquals(Color(0xFFE8DFD4),r.wheelCenter);assertTrue(r.hideHeaderDivider)
        assertEquals(Color(0xFF2C2521),scheme(true).background)
        assertEquals(Color(0xFF563A32),roles(true).header)
        assertEquals(Color(0xFF533B2F),roles(true).planningHeading)
    }

    @Test fun rubyFavoriteHasItsOwnRoleInsteadOfReusingWoodBrown() {
        for(dark in listOf(false,true)) {
            val c=scheme(dark);val r=roles(dark)
            assertEquals(if(dark) Color(0xFFD89999) else Color(0xFF8B3441),r.favorite)
            assertEquals(if(dark) Color(0xFFCDB08A) else Color(0xFF7D614C),r.settingsIcon)
            assertEquals(r.settingsIcon,r.note);assertNotEquals(r.favorite,r.settingsIcon)
            assertTrue(contrast(c.surface,r.favorite)>=4.5f)
            assertEquals(Color(0xFFDCD2C6),r.selectedRoomAction)
        }
    }

    @Test fun launchUsesOriginalWineWhileAllSmallSelectionsUseReadableCream() {
        for(dark in listOf(false,true)) {
            val r=roles(dark)
            assertEquals(if(dark) Color(0xFF86413F) else Color(0xFF800020),r.launch)
            assertEquals(Color(0xFFFFF4EA),r.onLaunch)
            assertEquals(Color(0xFFDCD2C6),r.enabledOption)
            assertEquals(Color(0xFF49332C),r.onEnabledOption)
            assertEquals(r.enabledOption,r.selectionFill);assertEquals(r.onEnabledOption,r.onSelection)
            assertEquals(r.enabledOption,r.confirmation);assertEquals(r.enabledOption,r.navigationSelected)
            assertFalse(r.outlineExpectedSelection);assertNotEquals(r.launch,r.enabledOption)
        }
    }

    @Test fun allFunctionalPairsAreReadableInBothModes() {
        for(dark in listOf(false,true)) {
            val c=scheme(dark);val r=roles(dark)
            listOf(r.header to r.onHeader,r.header to r.headerMuted,r.navigation to r.navigationIdle,
                r.navigationSelected to r.onNavigationSelected,r.planningHeading to r.onPlanningHeading,
                r.planningHeading to r.planningHeadingMuted,r.planningHeading to r.planningHeadingEntry,
                r.roomActions to r.onRoomActions,r.roomActions to r.selectedRoomAction,r.filter to r.onFilter,
                r.launch to r.onLaunch,r.enabledOption to r.onEnabledOption,r.confirmation to r.onConfirmation,
                r.wheelCenter to c.primary,c.surface to c.onSurface,c.surface to c.onSurfaceVariant,
                c.surface to r.settingsIcon,c.surface to r.themeArrow,c.surface to r.favorite,
                c.secondary to c.onSecondary,c.secondaryContainer to c.onSecondaryContainer,
                c.tertiary to c.onTertiary,c.tertiaryContainer to c.onTertiaryContainer
            ).forEach { (bg,fg)->assertTrue("dark=$dark $bg / $fg",contrast(bg,fg)>=4.5f) }
        }
    }

    @Test fun stableIdRoundTripsPerCampusWithoutChangingDefaultsOrTheEightOldIds() {
        val stored=mutablePreferencesOf()
        val value=AppearancePreferences(CampusTone.BURGUNDY,CampusTone.PEACH_PLUM,Campus.SHAHE)
        AppearancePreferenceKeys.write(stored,value)
        assertEquals("burgundy",stored[AppearancePreferenceKeys.west]);assertEquals(value,AppearancePreferenceKeys.read(stored))
        assertEquals(listOf("sand","pine","matisse","dunhuang","old_port","peach_plum"),CampusTone.entries.take(6).map { it.id })
        assertEquals(CampusTone.BURGUNDY,CampusTone.PEACH_PLUM.step(1))
        assertEquals(CampusTone.MORANDI,CampusTone.BURGUNDY.step(1));assertEquals(CampusTone.MONDRIAN,CampusTone.SAND.step(-1))
        assertEquals(CampusTone.SAND,AppearancePreferences().westTone);assertEquals(CampusTone.PINE,AppearancePreferences().shaheTone)
        assertEquals(scheme(false).surface,campusColorScheme(Campus.SHAHE,false,CampusTone.BURGUNDY).surface)
    }
}
