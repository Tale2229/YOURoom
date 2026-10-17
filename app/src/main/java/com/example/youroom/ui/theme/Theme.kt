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
}

fun campusColorScheme(campus: Campus, darkTheme: Boolean,
    tone: CampusTone = CampusTone.default(campus)): ColorScheme {
    val p = tonePalette(tone, darkTheme)
    val light = tonePalette(tone, false)
    val night = tonePalette(tone, true)
    // Every Material role is owned by this palette, not inherited from Sand or M3 purple.
    // Errors deliberately remain red; fixed white controls remain neutral in every tone.
    return lightColorScheme(
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
        else palette(0xFF665637,0xFFFFFFFF,0xFFEFE8D9,0xFFF7F6F2,0xFFF7F6F2,0xFF252927,0xFF5D584F,
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
}

@Composable
fun YouRoomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    campus: Campus = Campus.XITUCHENG,
    appearance: AppearancePreferences = LocalCampusAppearance.current,
    content: @Composable () -> Unit
) {
    // Each campus surface has a stable palette. Animate its layer, not MaterialTheme:
    // interpolating colors would invalidate every consumer on every transition frame.
    val tone = appearance.tone(campus)
    val colors = remember(campus, darkTheme, tone) { campusColorScheme(campus, darkTheme, tone) }
    CompositionLocalProvider(LocalCampusAppearance provides appearance) {
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = YouRoomShapes,
        content = content
    )
    }
}
