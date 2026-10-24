package com.familyday.board.data

import androidx.room.*
import com.familyday.board.domain.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "households")
data class Household(
    @PrimaryKey val id: String = newId(), val name: String,
    val zone: String = java.time.ZoneId.systemDefault().id,
    val weekStart: Int = 1, val priority: String = ""
)
@Entity(tableName = "members")
data class Member(@PrimaryKey val id: String = newId(), val name: String, val role: Role, val color: Int = 0)
@Entity(tableName = "entries", indices = [Index("kind"), Index("parentId")])
data class Entry(
    @PrimaryKey val id: String = newId(), val kind: Kind, val title: String,
    val notes: String = "", val category: String = "", val memberIds: String = "",
    val source: String = "local",
    val parentId: String = "", val start: Long = System.currentTimeMillis(),
    val end: Long = start + 3600000, val zone: String = java.time.ZoneId.systemDefault().id,
    val allDay: Boolean = false, val repeat: Repeat = Repeat.NONE,
    val every: Int = 1, val rotate: Boolean = false,
    val excluded: String = "", val completedDates: String = "", val done: Boolean = false,
    val pinned: Boolean = false, val childVisible: Boolean = true,
    val expires: Long = 0, val approvalRequired: Boolean = false, val approvedDates: String = "",
    val stars: Int = 0, val position: Int = 0, val reminderMinutes: Int = -1,
    val author: String = "", val updated: Long = System.currentTimeMillis(),
    val version: Long = 1, val deleted: Boolean = false
) {
    fun assigned(id: String) = id in memberIds.split(',')
    fun on(date: java.time.LocalDate): Boolean {
        if (kind != Kind.EVENT) return Schedule.occurs(start, zone, repeat, date, excluded.split(',').toSet(), every)
        val z = java.time.ZoneId.of(zone)
        val from = date.atStartOfDay(z).toInstant().toEpochMilli()
        val until = date.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli()
        val span = java.time.temporal.ChronoUnit.DAYS.between(java.time.Instant.ofEpochMilli(start).atZone(z).toLocalDate(), java.time.Instant.ofEpochMilli(end).atZone(z).toLocalDate()).coerceIn(0, 366)
        return (0..span).any { offset ->
            val origin = date.minusDays(offset)
            Schedule.occurs(start, zone, repeat, origin, excluded.split(',').toSet(), every) &&
                Schedule.overlaps(Schedule.occurrenceStart(start, zone, origin), Schedule.occurrenceEnd(start, end, zone, origin), from, until)
        }
    }
    fun assignedOn(id: String, date: java.time.LocalDate): Boolean {
        if (!rotate || kind != Kind.CHORE) return assigned(id)
        val people = memberIds.split(',').filter { it.isNotBlank() }
        return people.isNotEmpty() && people[(Schedule.index(start, zone, repeat, date, every) % people.size).toInt()] == id
    }
    fun completed(date: java.time.LocalDate) = if (kind == Kind.CHORE) date.toString() in completedDates.split(',') else done
    fun approved(date: java.time.LocalDate) = date.toString() in approvedDates.split(',')
}
@Entity(tableName = "mutations")
data class Mutation(@PrimaryKey val operationId: String = newId(), val entityId: String, val version: Long, val created: Long = System.currentTimeMillis())
class Converters {
    @TypeConverter fun kind(v: String): Kind = Kind.valueOf(v)
    @TypeConverter fun kind(v: Kind): String = v.name
    @TypeConverter fun role(v: String): Role = Role.valueOf(v)
    @TypeConverter fun role(v: Role): String = v.name
    @TypeConverter fun repeat(v: String): Repeat = Repeat.valueOf(v)
    @TypeConverter fun repeat(v: Repeat): String = v.name
}
@Dao
interface BoardDao {
    @Query("SELECT * FROM households LIMIT 1") fun householdFlow(): Flow<Household?>
    @Query("SELECT * FROM households LIMIT 1") suspend fun household(): Household?
    @Query("SELECT * FROM members ORDER BY rowid") fun membersFlow(): Flow<List<Member>>
    @Query("SELECT * FROM members ORDER BY rowid") suspend fun members(): List<Member>
    @Query("SELECT * FROM entries WHERE deleted = 0 ORDER BY position, start") fun entriesFlow(): Flow<List<Entry>>
    @Query("SELECT * FROM entries") suspend fun allEntries(): List<Entry>
    @Query("SELECT * FROM entries WHERE id = :id") suspend fun entry(id: String): Entry?
    @Upsert suspend fun put(h: Household)
    @Upsert suspend fun put(m: Member)
    @Upsert suspend fun put(e: Entry)
    @Insert suspend fun enqueue(m: Mutation)
    @Query("SELECT * FROM mutations ORDER BY created LIMIT 100") suspend fun pending(): List<Mutation>
    @Query("DELETE FROM mutations WHERE operationId = :id") suspend fun acknowledge(id: String)
    @Query("DELETE FROM members WHERE id = :id") suspend fun removeMember(id: String)
    @Query("DELETE FROM households") suspend fun clearHousehold()
    @Query("DELETE FROM members") suspend fun clearMembers()
    @Query("DELETE FROM entries") suspend fun clearEntries()
    @Query("DELETE FROM mutations") suspend fun clearQueue()
}
@Database(entities = [Household::class, Member::class, Entry::class, Mutation::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class BoardDatabase : RoomDatabase() { abstract fun boardDao(): BoardDao }
