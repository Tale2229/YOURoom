package com.example.youroom.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.example.youroom.domain.Campus
import com.example.youroom.domain.CampusTone
import com.example.youroom.domain.AppearancePreferences

val LocalCampusAppearance = staticCompositionLocalOf { AppearancePreferences() }

fun campusBadgeColor(campus: Campus, tone: CampusTone = CampusTone.default(campus)) = when(tone) {
    CampusTone.SAND -> Color(0xFF493D27)
    CampusTone.PINE -> Color(0xFF203F34)
    CampusTone.SLATE -> Color(0xFF34474D)
    CampusTone.CLAY -> Color(0xFF5B3E34)
    CampusTone.MATISSE -> Color(0xFF343E47)
    CampusTone.DUNHUANG -> Color(0xFF6B3B32)
    CampusTone.OLD_PORT -> Color(0xFF344D4B)
    CampusTone.PEACH_PLUM -> Color(0xFF574A52)
    CampusTone.BURGUNDY -> Color(0xFF49312C)
    CampusTone.MORANDI -> Color(0xFF535D64)
    CampusTone.RED_BEAN -> Color(0xFF4F5E69)
    CampusTone.AUTUMN_CITRUS -> Color(0xFF4C5237)
    CampusTone.MORNING_BELL -> Color(0xFF455E88)
    CampusTone.MONDRIAN -> Color(0xFF274997)
}

fun campusColorScheme(campus: Campus, darkTheme: Boolean,
    tone: CampusTone = CampusTone.default(campus)): ColorScheme {
    val p = tonePalette(tone, darkTheme)
    val light = tonePalette(tone, false)
    val night = tonePalette(tone, true)
    // Every Material role is owned by this palette, not inherited from Sand or M3 purple.
    // Errors deliberately remain red; fixed white controls remain neutral in every tone.
    val colors = lightColorScheme(
        primary=p.primary, onPrimary=p.onPrimary, primaryContainer=p.container, onPrimaryContainer=p.primary,
        secondary=p.primary, onSecondary=p.onPrimary, secondaryContainer=p.container, onSecondaryContainer=p.primary,
        tertiary=p.primary, onTertiary=p.onPrimary, tertiaryContainer=p.container, onTertiaryContainer=p.primary,
        background=p.background, onBackground=p.ink, surface=p.paper, onSurface=p.ink,
        surfaceVariant=p.high, onSurfaceVariant=p.muted, surfaceTint=Color.Transparent,
        surfaceContainerLowest=p.lowest, surfaceContainerLow=p.low, surfaceContainer=p.control,
        surfaceContainerHigh=p.high, surfaceContainerHighest=p.highest,
        surfaceBright=if(darkTheme) p.high else p.lowest, surfaceDim=if(darkTheme) p.lowest else p.highest,
        outline=p.outline, outlineVariant=p.rule,
        inverseSurface=if(darkTheme) night.ink else light.ink,
        inverseOnSurface=if(darkTheme) night.paper else light.paper,
        inversePrimary=if(darkTheme) light.primary else night.primary,
        primaryFixed=light.container, primaryFixedDim=light.highest,
        onPrimaryFixed=light.ink, onPrimaryFixedVariant=light.primary,
        secondaryFixed=light.container, secondaryFixedDim=light.highest,
        onSecondaryFixed=light.ink, onSecondaryFixedVariant=light.primary,
        tertiaryFixed=light.container, tertiaryFixedDim=light.highest,
        onTertiaryFixed=light.ink, onTertiaryFixedVariant=light.primary,
        error=if(darkTheme) Color(0xFFFFB4AB) else Color(0xFF984337),
        onError=if(darkTheme) Color(0xFF541E17) else Color.White,
        errorContainer=if(darkTheme) Color(0xFF713028) else Color(0xFFFFDAD3),
        onErrorContainer=if(darkTheme) Color(0xFFFFDAD3) else Color(0xFF3B0A06),
        scrim=Color.Black,
    )
    return if (tone == CampusTone.MATISSE) colors.copy(
        secondary=if(darkTheme) Color(0xFFD4AB86) else Color(0xFF8F603D),
        onSecondary=if(darkTheme) Color(0xFF302015) else Color(0xFFFFF6EF),
        secondaryContainer=if(darkTheme) Color(0xFF4A382C) else Color(0xFFE3D8CC),
        onSecondaryContainer=if(darkTheme) Color(0xFFE9C9AC) else Color(0xFF5E492F),
        tertiary=if(darkTheme) Color(0xFFDDE6A6) else Color(0xFFE3EAAB),
        onTertiary=Color(0xFF243126),
        tertiaryContainer=if(darkTheme) Color(0xFF42482E) else Color(0xFFEDF0CE),
        onTertiaryContainer=if(darkTheme) Color(0xFFE3EAAB) else Color(0xFF343E47),
        secondaryFixed=Color(0xFFE3D8CC), secondaryFixedDim=Color(0xFFD4AB86),
        onSecondaryFixed=Color(0xFF302015), onSecondaryFixedVariant=Color(0xFF5E492F),
        tertiaryFixed=Color(0xFFE3EAAB), tertiaryFixedDim=Color(0xFFDDE6A6),
        onTertiaryFixed=Color(0xFF243126), onTertiaryFixedVariant=Color(0xFF343E47),
    ) else if (tone == CampusTone.DUNHUANG) colors.copy(
        secondary=if(darkTheme) Color(0xFFADC8B9) else Color(0xFF3C5E52),
        onSecondary=if(darkTheme) Color(0xFF263D32) else Color(0xFFF4EFE3),
        secondaryContainer=if(darkTheme) Color(0xFF385045) else Color(0xFFCDDED5),
        onSecondaryContainer=if(darkTheme) Color(0xFFE4EDE6) else Color(0xFF244C41),
        tertiary=if(darkTheme) Color(0xFFD3B38D) else Color(0xFFB78F68),
        onTertiary=if(darkTheme) Color(0xFF35291D) else Color(0xFF3C261E),
        tertiaryContainer=if(darkTheme) Color(0xFF554432) else Color(0xFFDCC6AC),
        onTertiaryContainer=if(darkTheme) Color(0xFFEFD7BE) else Color(0xFF6B3B32),
        secondaryFixed=Color(0xFFCDDED5), secondaryFixedDim=Color(0xFF8FBFB0),
        onSecondaryFixed=Color(0xFF244C41), onSecondaryFixedVariant=Color(0xFF315D50),
        tertiaryFixed=Color(0xFFDCC6AC), tertiaryFixedDim=Color(0xFFB78F68),
        onTertiaryFixed=Color(0xFF3C261E), onTertiaryFixedVariant=Color(0xFF6B3B32),
    ) else if (tone == CampusTone.OLD_PORT) colors.copy(
        secondary=if(darkTheme) Color(0xFFD8AF86) else Color(0xFF885F48),
        onSecondary=if(darkTheme) Color(0xFF35291F) else Color(0xFFFAF5E9),
        secondaryContainer=if(darkTheme) Color(0xFF685442) else Color(0xFFEADAC3),
        onSecondaryContainer=if(darkTheme) Color(0xFFF3E4CE) else Color(0xFF674730),
        tertiary=if(darkTheme) Color(0xFFDCC09B) else Color(0xFFDDC1A0),
        onTertiary=Color(0xFF344D4B),
        tertiaryContainer=if(darkTheme) Color(0xFF685442) else Color(0xFFEADAC3),
        onTertiaryContainer=if(darkTheme) Color(0xFFF3E4CE) else Color(0xFF344D4B),
        secondaryFixed=Color(0xFFEADAC3), secondaryFixedDim=Color(0xFFD8AF86),
        onSecondaryFixed=Color(0xFF35291F), onSecondaryFixedVariant=Color(0xFF674730),
        tertiaryFixed=Color(0xFFDDC1A0), tertiaryFixedDim=Color(0xFFDCC09B),
        onTertiaryFixed=Color(0xFF344D4B), onTertiaryFixedVariant=Color(0xFF38585A),
    ) else if (tone == CampusTone.PEACH_PLUM) colors.copy(
        secondary=if(darkTheme) Color(0xFFDEA99A) else Color(0xFF935F56),
        onSecondary=if(darkTheme) Color(0xFF3A2423) else Color(0xFFFFF8F1),
        secondaryContainer=if(darkTheme) Color(0xFF66463F) else Color(0xFFF7E1D8),
        onSecondaryContainer=if(darkTheme) Color(0xFFFFE4D7) else Color(0xFF6C4540),
        tertiary=if(darkTheme) Color(0xFFF1C3B2) else Color(0xFFFAC7B7), onTertiary=Color(0xFF493F47),
        tertiaryContainer=if(darkTheme) Color(0xFF66463F) else Color(0xFFF7E1D8),
        onTertiaryContainer=if(darkTheme) Color(0xFFFFE4D7) else Color(0xFF493F47),
        secondaryFixed=Color(0xFFF7E1D8), secondaryFixedDim=Color(0xFFDEA99A),
        onSecondaryFixed=Color(0xFF3A2423), onSecondaryFixedVariant=Color(0xFF6C4540),
        tertiaryFixed=Color(0xFFFAC7B7), tertiaryFixedDim=Color(0xFFF1C3B2),
        onTertiaryFixed=Color(0xFF493F47), onTertiaryFixedVariant=Color(0xFF5D4F57),
    ) else if (tone == CampusTone.BURGUNDY) colors.copy(
        secondary=if(darkTheme) Color(0xFFCDB08A) else Color(0xFF7D614C),
        onSecondary=if(darkTheme) Color(0xFF392B25) else Color(0xFFFFF4EA),
        secondaryContainer=if(darkTheme) Color(0xFF503A32) else Color(0xFFE7D6CF),
        onSecondaryContainer=if(darkTheme) Color(0xFFF1E7DB) else Color(0xFF49332C),
        tertiary=Color(0xFFDCD2C6), onTertiary=Color(0xFF49332C),
        tertiaryContainer=if(darkTheme) Color(0xFF58473E) else Color(0xFFE9DED1),
        onTertiaryContainer=if(darkTheme) Color(0xFFF1E7DB) else Color(0xFF49332C),
        secondaryFixed=Color(0xFFE7D6CF), secondaryFixedDim=Color(0xFFCDB08A),
        onSecondaryFixed=Color(0xFF392B25), onSecondaryFixedVariant=Color(0xFF49332C),
        tertiaryFixed=Color(0xFFDCD2C6), tertiaryFixedDim=Color(0xFFCEB28D),
        onTertiaryFixed=Color(0xFF49332C), onTertiaryFixedVariant=Color(0xFF64433B),
    ) else if (tone == CampusTone.MORANDI) colors.copy(
        secondary=if(darkTheme) Color(0xFFB1C4CA) else Color(0xFF5D717A),
        onSecondary=if(darkTheme) Color(0xFF283B46) else Color(0xFFFBFCF8),
        secondaryContainer=if(darkTheme) Color(0xFF3B4850) else Color(0xFFDCE5E7),
        onSecondaryContainer=if(darkTheme) Color(0xFFE2EAEA) else Color(0xFF394E59),
        tertiary=if(darkTheme) Color(0xFFD1B8BD) else Color(0xFFD7BFC2),
        onTertiary=if(darkTheme) Color(0xFF493A42) else Color(0xFF57424B),
        tertiaryContainer=if(darkTheme) Color(0xFF57454E) else Color(0xFFE7DADD),
        onTertiaryContainer=if(darkTheme) Color(0xFFE7ECEB) else Color(0xFF57424B),
        secondaryFixed=Color(0xFFDCE5E7), secondaryFixedDim=Color(0xFFB1C4CA),
        onSecondaryFixed=Color(0xFF283B46), onSecondaryFixedVariant=Color(0xFF394E59),
        tertiaryFixed=Color(0xFFD7BFC2), tertiaryFixedDim=Color(0xFFD1B8BD),
        onTertiaryFixed=Color(0xFF493A42), onTertiaryFixedVariant=Color(0xFF57424B),
    ) else if (tone == CampusTone.RED_BEAN) colors.copy(
        secondary=if(darkTheme) Color(0xFFBECBD9) else Color(0xFF647582),
        onSecondary=if(darkTheme) Color(0xFF2A3440) else Color(0xFFFFFFFF),
        secondaryContainer=if(darkTheme) Color(0xFF353E49) else Color(0xFFE1E5E8),
        onSecondaryContainer=if(darkTheme) Color(0xFFDDE6EF) else Color(0xFF4F5E69),
        tertiary=if(darkTheme) Color(0xFFE49A88) else Color(0xFFB3392F),
        onTertiary=if(darkTheme) Color(0xFF492823) else Color(0xFFFFF8F2),
        tertiaryContainer=if(darkTheme) Color(0xFF514039) else Color(0xFFEAD4CC),
        onTertiaryContainer=if(darkTheme) Color(0xFFF1C7BA) else Color(0xFF773D36),
        secondaryFixed=Color(0xFFE1E5E8), secondaryFixedDim=Color(0xFFBECBD9),
        onSecondaryFixed=Color(0xFF2A3440), onSecondaryFixedVariant=Color(0xFF4F5E69),
        tertiaryFixed=Color(0xFFEAD4CC), tertiaryFixedDim=Color(0xFFE49A88),
        onTertiaryFixed=Color(0xFF492823), onTertiaryFixedVariant=Color(0xFF773D36),
    ) else if (tone == CampusTone.AUTUMN_CITRUS) colors.copy(
        secondary=if(darkTheme) Color(0xFFD9BC8F) else Color(0xFF806340),
        onSecondary=if(darkTheme) Color(0xFF352D1E) else Color(0xFFFFFCF4),
        secondaryContainer=if(darkTheme) Color(0xFF514A36) else Color(0xFFEAE5D6),
        onSecondaryContainer=if(darkTheme) Color(0xFFE8D8B9) else Color(0xFF5A492E),
        tertiary=if(darkTheme) Color(0xFFB8C18C) else Color(0xFF9AA066),
        onTertiary=if(darkTheme) Color(0xFF2D3820) else Color(0xFF28321B),
        tertiaryContainer=if(darkTheme) Color(0xFF535C3E) else Color(0xFFD9DDB9),
        onTertiaryContainer=if(darkTheme) Color(0xFFE2E9BD) else Color(0xFF3F4B2E),
        secondaryFixed=Color(0xFFEAE5D6), secondaryFixedDim=Color(0xFFD9BC8F),
        onSecondaryFixed=Color(0xFF352D1E), onSecondaryFixedVariant=Color(0xFF5A492E),
        tertiaryFixed=Color(0xFFD9DDB9), tertiaryFixedDim=Color(0xFFB8C18C),
        onTertiaryFixed=Color(0xFF2D3820), onTertiaryFixedVariant=Color(0xFF3F4B2E),
    ) else if (tone == CampusTone.MORNING_BELL) colors.copy(
        secondary=if(darkTheme) Color(0xFFC4BBD1) else Color(0xFF696D83),
        onSecondary=if(darkTheme) Color(0xFF28364A) else Color(0xFFFCFDFF),
        secondaryContainer=if(darkTheme) Color(0xFF344762) else Color(0xFFD8E2F1),
        onSecondaryContainer=if(darkTheme) Color(0xFFD2DCEF) else Color(0xFF455E88),
        tertiary=if(darkTheme) Color(0xFFE2CDDF) else Color(0xFF625466),
        onTertiary=if(darkTheme) Color(0xFF3A2F40) else Color(0xFFFCFDFF),
        tertiaryContainer=if(darkTheme) Color(0xFF514859) else Color(0xFFE1DDE7),
        onTertiaryContainer=if(darkTheme) Color(0xFFF0DDEB) else Color(0xFF625466),
        secondaryFixed=Color(0xFFD8E2F1), secondaryFixedDim=Color(0xFFC4BBD1),
        onSecondaryFixed=Color(0xFF28364A), onSecondaryFixedVariant=Color(0xFF455E88),
        tertiaryFixed=Color(0xFFE1DDE7), tertiaryFixedDim=Color(0xFFE2CDDF),
        onTertiaryFixed=Color(0xFF3A2F40), onTertiaryFixedVariant=Color(0xFF625466),
    ) else if (tone == CampusTone.MONDRIAN) colors.copy(
        secondary=if(darkTheme) Color(0xFFA5BAE8) else Color(0xFF274997),
        onSecondary=if(darkTheme) Color(0xFF060102) else Color.White,
        secondaryContainer=if(darkTheme) Color(0xFF303035) else Color(0xFFDFE5F1),
        onSecondaryContainer=if(darkTheme) Color(0xFFA5BAE8) else Color(0xFF274997),
        tertiary=Color(0xFFEEE95D), onTertiary=Color(0xFF060102),
        tertiaryContainer=Color(0xFFEEE95D), onTertiaryContainer=Color(0xFF060102),
        secondaryFixed=Color(0xFFDFE5F1), secondaryFixedDim=Color(0xFFA5BAE8),
        onSecondaryFixed=Color(0xFF060102), onSecondaryFixedVariant=Color(0xFF274997),
        tertiaryFixed=Color(0xFFEEE95D), tertiaryFixedDim=Color(0xFFD8D34F),
        onTertiaryFixed=Color(0xFF060102), onTertiaryFixedVariant=Color(0xFF303035),
    ) else colors
}

private data class TonePalette(val primary: Color, val onPrimary: Color, val container: Color,
    val background: Color, val paper: Color, val ink: Color, val muted: Color, val rule: Color,
    val outline: Color, val lowest: Color, val low: Color, val control: Color, val high: Color, val highest: Color)

private fun palette(vararg values: Long): TonePalette = values.map { Color(it) }.let {
    TonePalette(it[0],it[1],it[2],it[3],it[4],it[5],it[6],it[7],it[8],it[9],it[10],it[11],it[12],it[13])
}

private fun tonePalette(tone: CampusTone, dark: Boolean): TonePalette = when(tone) {
    CampusTone.SAND -> if(dark) palette(0xFFD7C4A1,0xFF241F16,0xFF453B28,0xFF181714,0xFF1D1B17,0xFFEEE9DF,0xFFBCB3A3,
        0xFF494132,0xFF978B75,0xFF13120F,0xFF232019,0xFF28241E,0xFF322C22,0xFF3D3528)
        else palette(0xFF665637,0xFFFFFFFF,0xFFEFE8D9,0xFFF7F6F2,0xFFF7F6F2,0xFF352D20,0xFF5D584F,
            0xFFDED8CC,0xFF91856D,0xFFFFFEFA,0xFFF3EFE6,0xFFF4F0E7,0xFFE7DDCA,0xFFDED1B8)
    CampusTone.PINE -> if(dark) palette(0xFFA8D2BD,0xFF14211B,0xFF254639,0xFF121A16,0xFF17211B,0xFFE5EEE6,0xFFADBDAD,
        0xFF35473B,0xFF819780,0xFF0E1511,0xFF1A271F,0xFF203026,0xFF293C30,0xFF344A3B)
        else palette(0xFF315C4E,0xFFFFFFFF,0xFFDFEADF,0xFFF0F5EF,0xFFF0F5EF,0xFF233128,0xFF526555,
            0xFFD2DFD0,0xFF7D927B,0xFFFAFFF8,0xFFEBF2E8,0xFFE7EFE4,0xFFDCE7D9,0xFFCEDDCB)
    CampusTone.SLATE -> if(dark) palette(0xFFB7CED5,0xFF162227,0xFF2B414B,0xFF141A20,0xFF192129,0xFFE4ECF1,0xFFAABBC8,
        0xFF354550,0xFF8296A7,0xFF0F151A,0xFF1E2933,0xFF25323D,0xFF2F3F4B,0xFF3A4C59)
        else palette(0xFF4C6068,0xFFFFFFFF,0xFFE0E8ED,0xFFF0F3F6,0xFFF0F3F6,0xFF26313A,0xFF566575,
            0xFFD1DBE3,0xFF8093A2,0xFFFAFDFF,0xFFEAF0F4,0xFFE5EDF2,0xFFD9E3EB,0xFFCBD9E3)
    CampusTone.CLAY -> if(dark) palette(0xFFE6BBA9,0xFF281B16,0xFF51372E,0xFF211714,0xFF281D19,0xFFF3E6DE,0xFFC6AEA0,
        0xFF513C32,0xFFA38A79,0xFF19110E,0xFF30231D,0xFF392920,0xFF473227,0xFF553D30)
        else palette(0xFF785548,0xFFFFFFFF,0xFFF0DFD4,0xFFFAF1E9,0xFFFAF1E9,0xFF382C27,0xFF725B4E,
            0xFFE6D2C1,0xFFA38670,0xFFFFFAF5,0xFFF7EAE0,0xFFF4E6D9,0xFFEBD7C6,0xFFE0C7B2)
    CampusTone.MATISSE -> if(dark) palette(0xFFBDCCD2,0xFF1D272B,0xFF46575C,0xFF25342D,0xFF25342D,0xFFE9EFE2,0xFFB4C6B8,
        0xFF4E6256,0xFF91A797,0xFF1E2B25,0xFF2C3E32,0xFF34473A,0xFF3A4B40,0xFF465A49)
        else palette(0xFF343E47,0xFFF3F6EF,0xFFC6D1D0,0xFFE9EFE4,0xFFE9EFE4,0xFF343E47,0xFF52635C,
            0xFFBDCCBA,0xFF718677,0xFFF3F6EF,0xFFE0E8DA,0xFFD2DECD,0xFFC9D7C5,0xFFC0D0BC)
    CampusTone.DUNHUANG -> if(dark) palette(0xFFDBB99E,0xFF382820,0xFF544434,0xFF2B2A24,0xFF2B2A24,0xFFE8DED0,0xFFC0B5A5,
        0xFF565247,0xFFB39B80,0xFF24231E,0xFF333027,0xFF3C3930,0xFF494236,0xFF554B3B)
        else palette(0xFF6B3B32,0xFFFFF4E8,0xFFE6D5C2,0xFFF1EBE2,0xFFF1EBE2,0xFF513B32,0xFF706358,
            0xFFCEC2B3,0xFF92765E,0xFFFAF6EF,0xFFEEE4D7,0xFFE5DACB,0xFFDFD1BF,0xFFD7C7B1)
    CampusTone.OLD_PORT -> if(dark) palette(0xFFBACBBF,0xFF293E37,0xFF3D5047,0xFF292F2A,0xFF292F2A,0xFFEEE7D8,0xFFC2C6B6,
        0xFF626858,0xFFA0A68F,0xFF22271F,0xFF323A32,0xFF3A433B,0xFF454E43,0xFF535E52)
        else palette(0xFF38585A,0xFFF8F0E1,0xFFE3D7C3,0xFFF3ECE0,0xFFF3ECE0,0xFF354747,0xFF64685D,
            0xFFCCC5B5,0xFF8B8C7A,0xFFFAF5E9,0xFFEEE6D7,0xFFE5DDCE,0xFFE2D8C6,0xFFD6C9B4)
    CampusTone.PEACH_PLUM -> if(dark) palette(0xFFE9D5DB,0xFF392B33,0xFF57434E,0xFF302A2E,0xFF302A2E,0xFFF7ECEA,0xFFD3C1C6,
        0xFF6A5961,0xFFB59AA6,0xFF292327,0xFF3B3037,0xFF443940,0xFF51454C,0xFF61515A)
        else palette(0xFF5D4F57,0xFFFFF8F1,0xFFF5DFD5,0xFFFFF7F1,0xFFFFF7F1,0xFF493F47,0xFF76656D,
            0xFFDFD1CC,0xFF9B858E,0xFFFFFDF8,0xFFFBEEE7,0xFFF3E7E0,0xFFEADDD7,0xFFE3D6D1)
    CampusTone.BURGUNDY -> if(dark) palette(0xFFD8BFB0,0xFF392B25,0xFF503A32,0xFF2C2521,0xFF2C2521,0xFFF1E7DB,0xFFC9B9AA,
        0xFF665246,0xFFB99B7D,0xFF221C18,0xFF352B26,0xFF3E322C,0xFF4B3D33,0xFF58473E)
        else palette(0xFF64433B,0xFFFFF4EA,0xFFE7D6CF,0xFFF6F0E6,0xFFF6F0E6,0xFF45332C,0xFF75655B,
            0xFFD8C9BB,0xFF9B806B,0xFFFFFAF1,0xFFF0E7DC,0xFFE9DED1,0xFFE2D5C7,0xFFDDD0C4)
    CampusTone.MORANDI -> if(dark) palette(0xFFC1D1D8,0xFF283B46,0xFF3B4850,0xFF242C31,0xFF242C31,0xFFE7ECEB,0xFFB9C6C8,
        0xFF56646B,0xFF92A2A9,0xFF1B2328,0xFF29353C,0xFF303D44,0xFF3B494F,0xFF49575E)
        else palette(0xFF465963,0xFFF4F7F3,0xFFDCE5E7,0xFFF2F3EE,0xFFF2F3EE,0xFF374950,0xFF617077,
            0xFFCDD5D3,0xFF879195,0xFFFBFCF8,0xFFECEFE9,0xFFE3E7E4,0xFFDCE3E1,0xFFD3DCD9)
    CampusTone.RED_BEAN -> if(dark) palette(0xFFC5CED8,0xFF2A3440,0xFF353E49,0xFF25282D,0xFF25282D,0xFFE8ECF0,0xFFBBC3CC,
        0xFF555E69,0xFF8795A6,0xFF1A2027,0xFF2D3239,0xFF343940,0xFF3D454F,0xFF515D6C)
        else palette(0xFF4E5B63,0xFFFCFDF9,0xFFE1E5E8,0xFFFAF9F6,0xFFFAF9F6,0xFF30383C,0xFF626A70,
            0xFFD1D4D6,0xFF7F8890,0xFFFFFFFF,0xFFF1F2F1,0xFFECEDEB,0xFFE3E4E6,0xFFD4D7DB)
    CampusTone.AUTUMN_CITRUS -> if(dark) palette(0xFFCAD3B3,0xFF2E3822,0xFF3B452E,0xFF292D22,0xFF292D22,0xFFEAECDD,0xFFC0C7AB,
        0xFF62684F,0xFF8F9475,0xFF1E251A,0xFF30372A,0xFF3C4331,0xFF454C39,0xFF59604A)
        else palette(0xFF4C5639,0xFFFAF9EF,0xFFE1E4CE,0xFFF8F5EC,0xFFF8F5EC,0xFF39402E,0xFF646A55,
            0xFFD1CDBB,0xFFB7B69A,0xFFFFFCF4,0xFFF1EEE2,0xFFEAE5D6,0xFFE5E6D6,0xFFDAD5C3)
    CampusTone.MORNING_BELL -> if(dark) palette(0xFFB2C7EB,0xFF263852,0xFF344762,0xFF222936,0xFF222936,0xFFE6EBF6,0xFFB9C6DE,
        0xFF4B5A73,0xFF7D90AD,0xFF1A2230,0xFF2A3446,0xFF303B50,0xFF344762,0xFF4D5C75)
        else palette(0xFF455E88,0xFFFCFDFF,0xFFD8E2F1,0xFFF5F6FA,0xFFF5F6FA,0xFF303B52,0xFF59677F,
            0xFFCDD5E2,0xFF7F8AA0,0xFFFCFDFF,0xFFF0F2F7,0xFFEDEDF4,0xFFE1E5EF,0xFFD0DAEA)
    CampusTone.MONDRIAN -> if(dark) palette(0xFFEEEDE7,0xFF060102,0xFF303035,0xFF222022,0xFF222022,0xFFF4F3E9,0xFFC9C6C1,
        0xFF55514E,0xFF817E76,0xFF171517,0xFF2A282B,0xFF303035,0xFF3A3738,0xFF605C58)
        else palette(0xFF060102,0xFFFFFFFF,0xFFF0F0EA,0xFFFAFAF6,0xFFFAFAF6,0xFF060102,0xFF605B5B,
            0xFFD4D3CA,0xFF8C8784,0xFFFFFFFF,0xFFF4F4EE,0xFFECECE6,0xFFE8E8E3,0xFFD4D3CA)
}

@Composable
fun YOURoomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    campus: Campus = Campus.XITUCHENG,
    appearance: AppearancePreferences = LocalCampusAppearance.current,
    content: @Composable () -> Unit
) {
    // Each campus surface has a stable palette. Animate its layer, not MaterialTheme:
    // interpolating colors would invalidate every consumer on every transition frame.
    val tone = appearance.tone(campus)
    val colors = remember(campus, darkTheme, tone) { campusColorScheme(campus, darkTheme, tone) }
    val roles = remember(colors, tone, darkTheme) { campusThemeRoles(colors, tone, darkTheme) }
    CompositionLocalProvider(LocalCampusAppearance provides appearance, LocalThemeRoles provides roles) {
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = YOURoomShapes,
    ) {
        // Nested campus themes must not inherit a previous campus's content color
        // (or Compose's neutral black when a screen isn't wrapped in Surface).
        CompositionLocalProvider(LocalContentColor provides colors.onSurface, content = content)
    }
    }
}
