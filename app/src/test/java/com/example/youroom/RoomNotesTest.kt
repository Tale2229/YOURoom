package com.example.youroom

import com.example.youroom.domain.RoomNotes
import org.junit.Assert.*
import org.junit.Test

class RoomNotesTest {
    @Test fun emptyAndShortNotesAreUnchanged() {
        for (value in listOf("", "窗边有插座", "  保留空格  ")) assertEquals(value, RoomNotes.limit(value))
    }
    @Test fun twentyCharactersFitButTheTwentyFirstDoesNot() {
        assertEquals(20, RoomNotes.maximumLength)
        assertEquals("a".repeat(20), RoomNotes.limit("a".repeat(20)))
        assertEquals("a".repeat(20), RoomNotes.limit("a".repeat(520)))
    }
    @Test fun chineseAndAsciiUseTheSameCharacterLimit() {
        assertEquals("中".repeat(20), RoomNotes.limit("中".repeat(25)))
        assertEquals("中a".repeat(10), RoomNotes.limit("中a".repeat(11)))
    }
    @Test fun supplementaryCharactersCountOnceAndNeverLeaveAHalfSurrogate() {
        val emoji = "\uD83D\uDE00"
        val limited = RoomNotes.limit(emoji.repeat(21))
        assertEquals(emoji.repeat(20), limited)
        assertEquals(20, RoomNotes.length(limited))
        assertEquals(40, limited.length)
    }
    @Test fun mixedTextMayEndAtASupplementaryCharacterWithoutSplittingIt() {
        assertEquals("a".repeat(19) + "\uD83D\uDE00", RoomNotes.limit("a".repeat(19) + "\uD83D\uDE00z"))
    }
    @Test fun limitsPreserveSpacesAndLineBreaksAndAreIdempotent() {
        val value = "  窗边\n" + "a".repeat(30)
        val limited = RoomNotes.limit(value)
        assertTrue(limited.startsWith("  窗边\n"))
        assertEquals(20, RoomNotes.length(limited))
        assertEquals(limited, RoomNotes.limit(limited))
    }
}
