package com.example.youroom

import com.example.youroom.domain.CacheClearPhase
import com.example.youroom.domain.Campus
import com.example.youroom.domain.CampusTone
import androidx.compose.ui.graphics.luminance
import com.example.youroom.ui.DialogDesign
import com.example.youroom.ui.cacheDialogDesign
import com.example.youroom.ui.dialogColorScheme
import com.example.youroom.ui.theme.campusColorScheme
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DialogDesignTest {
    @Test fun captionInkIsReadableOnPaperAndBothBandsAcross24Palettes() {
        for (tone in CampusTone.entries) for (dark in listOf(false, true)) {
            val colors = dialogColorScheme(campusColorScheme(Campus.XITUCHENG, dark, tone))
            for (background in listOf(colors.surface, colors.surfaceContainer, colors.surfaceContainerLow)) {
                val ink = colors.onSurfaceVariant.luminance()
                val paper = background.luminance()
                assertTrue("$tone/$dark $background", (maxOf(ink, paper)+.05f)/(minOf(ink, paper)+.05f) >= 4.5f)
            }
        }
    }
    private fun repoFile(path: String) = File(path).takeIf { it.exists() } ?: File("../$path")
    private val selections get() = Json.parseToJsonElement(repoFile("docs/dialogs-selected-20261023.json").readText())
        .jsonObject.getValue("selections").jsonArray.associate {
            it.jsonObject.getValue("id").jsonPrimitive.content to it.jsonObject.getValue("variant").jsonPrimitive.content
        }

    @Test fun all39ApprovedChoicesArePreservedWithNoExcludedWindows() {
        val choices = selections
        assertEquals(39, choices.size)
        assertEquals(setOf("Q05", "R01", "R02", "R03", "R04", "R05", "R08", "P01", "P07",
            "S08", "A01", "A02", "A03", "A04", "A05", "A09", "A10"), choices.filterValues { it == "A" }.keys)
        assertEquals(22, choices.count { it.value == "B" })
        assertTrue(listOf("S09", "G01", "G02").none { it in choices })
    }

    @Test fun cachePhasesUseTheIndividuallyApprovedDesigns() {
        val phaseIds = mapOf(CacheClearPhase.INFORMATION to "A04", CacheClearPhase.CONFIRM to "A05",
            CacheClearPhase.CREDENTIALS to "A06", CacheClearPhase.VERIFYING to "A08",
            CacheClearPhase.CLEARING to "A09", CacheClearPhase.REQUESTED to "A10")
        phaseIds.forEach { (phase, id) -> assertEquals(id, selections[id], cacheDialogDesign(phase).name) }
        assertEquals(DialogDesign.B.name, selections["A07"])
    }

    @Test fun theExistingAboutAndGuideDirectoryDoNotOptIntoDialogRestyling() {
        listOf("AboutContent.kt", "GuideDirectory.kt").forEach { name ->
            val source = repoFile("app/src/main/java/com/example/youroom/ui/$name").readText()
            assertFalse(name, source.contains("DialogPanel("))
            assertFalse(name, source.contains("DialogDesignTheme("))
        }
    }
}
