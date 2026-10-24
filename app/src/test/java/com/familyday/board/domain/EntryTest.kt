package com.familyday.board.domain

import com.familyday.board.data.Entry
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class EntryTest {
    @Test fun multiDayEventOccupiesEveryCoveredDateButNotItsExclusiveEnd() {
        val zone = ZoneId.of("Europe/London")
        val first = LocalDate.of(2026, 10, 24)
        val event = Entry(kind = Kind.EVENT, title = "A weekend away", zone = zone.id, allDay = true,
            start = first.atStartOfDay(zone).toInstant().toEpochMilli(),
            end = first.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli())
        assertTrue(event.on(first)); assertTrue(event.on(first.plusDays(1)))
        assertFalse(event.on(first.minusDays(1))); assertFalse(event.on(first.plusDays(2)))
    }
    @Test fun rotatingChoresAssignExactlyOneMemberForEachPeriod() {
        val first = LocalDate.of(2026, 10, 24)
        val chore = Entry(kind = Kind.CHORE, title = "Feed pets", zone = "UTC", memberIds = "a,b", rotate = true, repeat = Repeat.DAILY,
            start = first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        assertTrue(chore.assignedOn("a", first)); assertFalse(chore.assignedOn("b", first))
        assertTrue(chore.assignedOn("b", first.plusDays(1))); assertFalse(chore.assignedOn("a", first.plusDays(1)))
        assertTrue(chore.assignedOn("a", first.plusDays(2)))
    }
}
