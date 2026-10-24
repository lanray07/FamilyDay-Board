package com.familyday.board.data

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import com.familyday.board.domain.Kind
import com.familyday.board.security.ParentGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.*
import java.util.UUID

/** Read-only, foreground, consent-driven import. No calendar account credentials are accessed. */
class CalendarImporter(private val context: Context, private val gate: ParentGate) {
    suspend fun candidates(zone: String): List<Entry> = withContext(Dispatchers.IO) {
        gate.requireAdult()
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, now - 86_400_000)
            ContentUris.appendId(this, now + 90L * 86_400_000)
        }.build()
        val columns = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION)
        val result = mutableListOf<Entry>()
        context.contentResolver.query(uri, columns, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { cursor ->
            while (result.size < 200 && cursor.moveToNext()) {
                val allDay = cursor.getInt(4) == 1
                fun time(index: Int): Long {
                    val raw = cursor.getLong(index)
                    return if (allDay) Instant.ofEpochMilli(raw).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneId.of(zone)).toInstant().toEpochMilli() else raw
                }
                val title = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                val start = time(2); val end = time(3)
                if (end <= start || end - start > 366L * 86_400_000) continue
                result += Entry(id = UUID.nameUUIDFromBytes("device-calendar:${cursor.getLong(0)}:$start".toByteArray()).toString(),
                    kind = Kind.EVENT, title = title.take(200), notes = cursor.getString(5).orEmpty().take(4000),
                    start = start, end = end, allDay = allDay, zone = zone, source = "device_calendar", childVisible = false)
            }
        }
        result
    }
}
