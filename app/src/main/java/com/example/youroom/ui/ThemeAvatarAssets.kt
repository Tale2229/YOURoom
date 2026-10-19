package com.example.youroom.ui

import androidx.annotation.DrawableRes
import com.example.youroom.R
import com.example.youroom.domain.CampusTone

/** Approved image assets are shared by both campuses and light/dark modes.
 * nodpi prevents Android density buckets from rescaling the originals before the
 * picker renders them at its existing 75dp size. No palette IDs or ordering change.
 */
@DrawableRes
internal fun themeAvatarResource(tone: CampusTone): Int = when (tone) {
    CampusTone.SAND -> R.drawable.theme_avatar_sand
    CampusTone.PINE -> R.drawable.theme_avatar_pine
    CampusTone.MATISSE -> R.drawable.theme_avatar_matisse
    CampusTone.DUNHUANG -> R.drawable.theme_avatar_dunhuang
    CampusTone.OLD_PORT -> R.drawable.theme_avatar_old_port
    CampusTone.PEACH_PLUM -> R.drawable.theme_avatar_peach_plum
    CampusTone.BURGUNDY -> R.drawable.theme_avatar_burgundy
    CampusTone.MORANDI -> R.drawable.theme_avatar_morandi
    CampusTone.RED_BEAN -> R.drawable.theme_avatar_red_bean
    CampusTone.AUTUMN_CITRUS -> R.drawable.theme_avatar_autumn_citrus
    CampusTone.MORNING_BELL -> R.drawable.theme_avatar_morning_bell
    CampusTone.MONDRIAN -> R.drawable.theme_avatar_mondrian
}
