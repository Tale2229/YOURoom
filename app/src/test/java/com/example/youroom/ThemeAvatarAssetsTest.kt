package com.example.youroom

import com.example.youroom.domain.CampusTone
import com.example.youroom.ui.themeAvatarResource
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeAvatarAssetsTest {
    @Test fun everyThemeHasItsOwnApprovedResource() {
        val expected = listOf(R.drawable.theme_avatar_sand, R.drawable.theme_avatar_pine,
            R.drawable.theme_avatar_matisse, R.drawable.theme_avatar_dunhuang,
            R.drawable.theme_avatar_old_port, R.drawable.theme_avatar_peach_plum,
            R.drawable.theme_avatar_burgundy, R.drawable.theme_avatar_morandi,
            R.drawable.theme_avatar_red_bean, R.drawable.theme_avatar_autumn_citrus,
            R.drawable.theme_avatar_morning_bell, R.drawable.theme_avatar_mondrian)
        assertEquals(expected, CampusTone.entries.map(::themeAvatarResource))
        assertEquals(12, expected.toSet().size)
    }
}
