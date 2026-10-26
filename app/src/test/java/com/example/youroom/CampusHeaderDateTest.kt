package com.example.youroom

import com.example.youroom.ui.campusHeaderDate
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CampusHeaderDateTest {
    @Test fun usesUnpaddedMonthAndDayAndEnglishWeekdayWithDot() {
        assertEquals("10月13日 Tue.", campusHeaderDate(LocalDate.of(2026, 10, 13)))
        assertEquals("1月2日 Sat.", campusHeaderDate(LocalDate.of(2027, 1, 2)))
    }

    @Test fun allSevenWeekdaysHaveTheRequestedAbbreviations() {
        val monday = LocalDate.of(2026, 10, 12)
        listOf("Mon.", "Tue.", "Wed.", "Thu.", "Fri.", "Sat.", "Sun.").forEachIndexed { index, day ->
            assertEquals("10月${12 + index}日 $day", campusHeaderDate(monday.plusDays(index.toLong())))
        }
    }

    @Test fun weekdayLanguageDoesNotDependOnTheDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            for (locale in listOf(Locale.CHINA, Locale.JAPAN, Locale.FRANCE)) {
                Locale.setDefault(locale)
                assertEquals("10月14日 Wed.", campusHeaderDate(LocalDate.of(2026, 10, 14)))
            }
        } finally { Locale.setDefault(previous) }
    }

    @Test fun dateChangesCorrectlyAcrossMonthAndYearBoundaries() {
        val lastDay = LocalDate.of(2026, 12, 31)
        assertEquals("12月31日 Thu.", campusHeaderDate(lastDay))
        assertEquals("1月1日 Fri.", campusHeaderDate(lastDay.plusDays(1)))
    }
}
