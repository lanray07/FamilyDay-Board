package com.familyday.board.domain

import java.time.*
import java.util.UUID

enum class Kind { EVENT, NOTE, CHORE, LIST, ITEM }
enum class Repeat { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }
enum class Role { OWNER, PARENT, CARER, ADULT, CHILD }
data class Session(val memberId: String, val role: Role, val expiresAt: Long = Long.MAX_VALUE) {
    val adult get() = role != Role.CHILD && System.currentTimeMillis() < expiresAt
}
fun newId(): String = UUID.randomUUID().toString()

/** Recurrence is evaluated in the event's zone, retaining wall time across DST. */
object Schedule {
    fun occurs(start: Long, zone: String, repeat: Repeat, date: LocalDate, excluded: Set<String> = emptySet(), every: Int = 1): Boolean {
        require(every >= 1)
        if (date.toString() in excluded) return false
        val first = Instant.ofEpochMilli(start).atZone(ZoneId.of(zone)).toLocalDate()
        if (date < first) return false
        return when (repeat) {
            Repeat.NONE -> date == first
            Repeat.DAILY -> java.time.temporal.ChronoUnit.DAYS.between(first, date) % every == 0L
            Repeat.WEEKLY -> date.dayOfWeek == first.dayOfWeek && java.time.temporal.ChronoUnit.WEEKS.between(first, date) % every == 0L
            Repeat.MONTHLY -> date.dayOfMonth == minOf(first.dayOfMonth, date.lengthOfMonth()) && java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(first), YearMonth.from(date)) % every == 0L
            Repeat.YEARLY -> date.month == first.month && date.dayOfMonth == minOf(first.dayOfMonth, date.lengthOfMonth()) && (date.year - first.year) % every == 0
        }
    }
    fun index(start: Long, zone: String, repeat: Repeat, date: LocalDate, every: Int): Long {
        val first = Instant.ofEpochMilli(start).atZone(ZoneId.of(zone)).toLocalDate()
        return (when (repeat) {
            Repeat.NONE -> 0L
            Repeat.DAILY -> java.time.temporal.ChronoUnit.DAYS.between(first, date)
            Repeat.WEEKLY -> java.time.temporal.ChronoUnit.WEEKS.between(first, date)
            Repeat.MONTHLY -> java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(first), YearMonth.from(date))
            Repeat.YEARLY -> (date.year - first.year).toLong()
        } / every).coerceAtLeast(0)
    }
    fun occurrenceStart(start: Long, zone: String, date: LocalDate): Long {
        val z = ZoneId.of(zone)
        val time = Instant.ofEpochMilli(start).atZone(z).toLocalTime()
        return date.atTime(time).atZone(z).toInstant().toEpochMilli()
    }
    fun occurrenceEnd(start: Long, end: Long, zone: String, date: LocalDate): Long {
        val z = ZoneId.of(zone)
        val a = Instant.ofEpochMilli(start).atZone(z)
        val b = Instant.ofEpochMilli(end).atZone(z)
        val days = java.time.temporal.ChronoUnit.DAYS.between(a.toLocalDate(), b.toLocalDate())
        return date.plusDays(days).atTime(b.toLocalTime()).atZone(z).toInstant().toEpochMilli()
    }
    fun overlaps(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Boolean = aStart < bEnd && bStart < aEnd
}
