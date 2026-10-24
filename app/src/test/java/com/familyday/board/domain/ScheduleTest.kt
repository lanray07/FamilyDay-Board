package com.familyday.board.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ScheduleTest {
    private fun start(text: String) = ZonedDateTime.parse(text).toInstant().toEpochMilli()
    @Test fun dailyRecurrenceKeepsWallTimeAcrossBritishSummerTime() {
        val first = start("2026-03-28T09:00:00Z[Europe/London]")
        val second = Schedule.occurrenceStart(first, "Europe/London", LocalDate.of(2026, 3, 29))
        assertEquals("2026-03-29T08:00:00Z", Instant.ofEpochMilli(second).toString())
        assertTrue(Schedule.occurs(first, "Europe/London", Repeat.DAILY, LocalDate.of(2026, 3, 29)))
    }
    @Test fun monthlyRecurrenceClampsToMonthEndAndReturnsToOriginalDay() {
        val first = start("2026-01-31T09:00:00Z[Europe/London]")
        assertTrue(Schedule.occurs(first, "Europe/London", Repeat.MONTHLY, LocalDate.of(2026, 2, 28)))
        assertTrue(Schedule.occurs(first, "Europe/London", Repeat.MONTHLY, LocalDate.of(2026, 3, 31)))
        assertFalse(Schedule.occurs(first, "Europe/London", Repeat.MONTHLY, LocalDate.of(2026, 3, 28)))
    }
    @Test fun leapBirthdayClampsInNonLeapYears() {
        val first = start("2024-02-29T09:00:00Z[Europe/London]")
        assertTrue(Schedule.occurs(first, "Europe/London", Repeat.YEARLY, LocalDate.of(2025, 2, 28)))
        assertTrue(Schedule.occurs(first, "Europe/London", Repeat.YEARLY, LocalDate.of(2028, 2, 29)))
    }
    @Test fun exceptionsAndDatesBeforeCreationNeverOccur() {
        val first = start("2026-10-24T09:00:00Z[UTC]")
        assertFalse(Schedule.occurs(first, "UTC", Repeat.DAILY, LocalDate.of(2026, 10, 23)))
        assertFalse(Schedule.occurs(first, "UTC", Repeat.WEEKLY, LocalDate.of(2026, 10, 31), setOf("2026-10-31")))
        assertTrue(Schedule.occurs(first, "UTC", Repeat.WEEKLY, LocalDate.of(2026, 11, 7)))
    }
    @Test fun adjacentEventsAreNotConflicts() {
        assertFalse(Schedule.overlaps(10, 20, 20, 30))
        assertTrue(Schedule.overlaps(10, 21, 20, 30))
        assertTrue(Schedule.overlaps(10, 40, 20, 30))
    }
    @Test fun fortnightlyRecurrenceAndRotationKeepTheirOriginalAnchor() {
        val first = start("2026-10-24T09:00:00Z[UTC]")
        assertFalse(Schedule.occurs(first, "UTC", Repeat.WEEKLY, LocalDate.of(2026, 10, 31), every = 2))
        assertTrue(Schedule.occurs(first, "UTC", Repeat.WEEKLY, LocalDate.of(2026, 11, 7), every = 2))
        assertEquals(1L, Schedule.index(first, "UTC", Repeat.WEEKLY, LocalDate.of(2026, 11, 7), 2))
    }
    @Test fun recurringAllDayEventsKeepTheirLocalMidnightEndAcrossDst() {
        val first = start("2026-03-28T00:00:00Z[Europe/London]")
        val end = start("2026-03-29T00:00:00Z[Europe/London]")
        val day = LocalDate.of(2026, 3, 29)
        val length = Schedule.occurrenceEnd(first, end, "Europe/London", day) - Schedule.occurrenceStart(first, "Europe/London", day)
        assertEquals(23 * 3600000L, length)
    }
}
