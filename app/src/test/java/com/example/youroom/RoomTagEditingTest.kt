package com.example.youroom

import com.example.youroom.domain.*
import org.junit.Assert.*
import org.junit.Test

class RoomTagEditingTest {
    private val profile = RoomProfile("01:2-201", "2-201", "2", favorite = true, excluded = true,
        quiet = true, outlets = true, note = "  保留备注  ", tags = setOf("靠窗"))

    @Test fun tagSelectionDoesNotBecomeASearchOrFavoriteCondition() {
        val selection = profile.tagSelection()
        assertTrue(selection.outletsOnly)
        assertEquals(setOf("安静", "靠窗"), selection.tags)
        assertFalse(selection.favoritesOnly)
        assertTrue(selection.buildings.isEmpty() && selection.selectedSlots.isEmpty())
        assertEquals("", selection.query)
    }

    @Test fun editingTagsPreservesOtherMarksAndNormalizesLegacyQuietWithoutLosingIt() {
        val next = profile.withTagSelection(profile.tagSelection().copy(tags = profile.allTags + "人少"))
        assertEquals(profile.copy(quiet = false, tags = setOf("安静", "靠窗", "人少")), next)
        assertTrue(next.hasTerm("安静"))
    }

    @Test fun addingAnOutletTermUsesTheOriginalBooleanAndDoesNotDestroyLegacyTags() {
        assertEquals(profile, profile.copy(outlets = false).withPersonalTag(PersonalTags.outlets))
        assertEquals(profile.copy(quiet = false, tags = profile.allTags + "新词条"), profile.withPersonalTag("新词条"))
    }

    @Test fun resetClearsOnlyTagsNotFavoriteExclusionOrWhitespaceInNotes() {
        assertEquals(profile.copy(quiet = false, outlets = false, tags = emptySet()), profile.withoutPersonalTags())
    }
}
