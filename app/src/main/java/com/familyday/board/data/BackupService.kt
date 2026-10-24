package com.familyday.board.data

import androidx.room.withTransaction
import com.familyday.board.domain.*
import com.familyday.board.security.ParentGate
import org.json.*
import java.time.ZoneId

/** Explicit adult-initiated portable backup; intentionally excludes the parent PIN and sessions. */
class BackupService(private val db: BoardDatabase, private val gate: ParentGate) {
    suspend fun export(): String {
        gate.requireAdult()
        return snapshot(false)
    }
    internal suspend fun snapshot(includeTombstones: Boolean = true): String {
        return db.withTransaction {
            val h = checkNotNull(db.boardDao().household())
            JSONObject().put("format", 1).put("household", JSONObject().put("name", h.name).put("zone", h.zone).put("weekStart", h.weekStart).put("priority", h.priority))
                .put("members", JSONArray(db.boardDao().members().map { m -> JSONObject().put("id", m.id).put("name", m.name).put("role", m.role.name).put("color", m.color) }))
                .put("entries", JSONArray(db.boardDao().allEntries().filter { includeTombstones || !it.deleted }.map(::encode))).toString(2)
        }
    }
    suspend fun restore(text: String) {
        gate.requireAdult()
        db.withTransaction {
            replace(text, false)
            db.boardDao().enqueue(Mutation(entityId = "restore", version = System.currentTimeMillis()))
        }
    }
    internal suspend fun replace(text: String, requireClean: Boolean): Boolean {
        require(text.length <= 5_000_000)
        val root = JSONObject(text)
        require(root.getInt("format") == 1)
        val h = root.getJSONObject("household")
        ZoneId.of(h.getString("zone"))
        require(h.getString("name").isNotBlank() && h.getInt("weekStart") in 1..7)
        val membersJson = root.getJSONArray("members")
        val members = (0 until membersJson.length()).map { membersJson.getJSONObject(it).run { Member(getString("id"), getString("name"), Role.valueOf(getString("role")), getInt("color")) } }
        require(members.size in 1..6 && members.count { it.role == Role.OWNER } == 1 && members.map { it.id }.distinct().size == members.size)
        require(members.all { it.name.isNotBlank() && it.name.length <= 50 })
        val entriesJson = root.getJSONArray("entries")
        require(entriesJson.length() <= 10000)
        val entries = (0 until entriesJson.length()).map { decode(entriesJson.getJSONObject(it)) }
        require(entries.map { it.id }.distinct().size == entries.size)
        entries.forEach { e ->
            require(e.title.isNotBlank() && e.title.length <= 200 && e.notes.length <= 4000 && e.end > e.start && e.stars in 0..10 && e.every in 1..365)
            ZoneId.of(e.zone)
            require(e.end - e.start <= 366L * 86_400_000)
            listOf(e.excluded, e.completedDates, e.approvedDates).forEach { dates ->
                require(dates.length <= 110000)
                dates.split(',').filter { it.isNotBlank() }.forEach { java.time.LocalDate.parse(it) }
            }
            require(e.memberIds.isBlank() || e.memberIds.split(',').all { id -> members.any { it.id == id } })
            if (e.kind == Kind.ITEM) require(entries.any { it.id == e.parentId && it.kind == Kind.LIST })
        }
        val applied = db.withTransaction {
            val dao = db.boardDao()
            if (requireClean && dao.pending().isNotEmpty()) return@withTransaction false
            dao.clearEntries(); dao.clearMembers(); dao.clearQueue()
            val old = checkNotNull(dao.household())
            dao.put(old.copy(name = h.getString("name"), zone = h.getString("zone"), weekStart = h.getInt("weekStart"), priority = h.optString("priority")))
            members.forEach { dao.put(it) }; entries.forEach { dao.put(it) }
            true
        }
        if (applied && gate.session.value?.board != true) gate.lock()
        return applied
    }
    private fun encode(e: Entry) = JSONObject().apply {
        put("id", e.id); put("kind", e.kind.name); put("title", e.title); put("notes", e.notes); put("category", e.category)
        put("source", e.source)
        put("memberIds", e.memberIds); put("parentId", e.parentId); put("start", e.start); put("end", e.end); put("zone", e.zone)
        put("allDay", e.allDay); put("repeat", e.repeat.name); put("excluded", e.excluded); put("completedDates", e.completedDates)
        put("every", e.every); put("rotate", e.rotate)
        put("done", e.done); put("pinned", e.pinned); put("childVisible", e.childVisible); put("expires", e.expires)
        put("approvalRequired", e.approvalRequired); put("approvedDates", e.approvedDates); put("stars", e.stars)
        put("position", e.position); put("reminderMinutes", e.reminderMinutes); put("author", e.author); put("updated", e.updated)
        put("version", e.version); put("deleted", e.deleted)
    }
    private fun decode(o: JSONObject) = Entry(
        id = o.getString("id"), kind = Kind.valueOf(o.getString("kind")), title = o.getString("title"),
        notes = o.optString("notes"), category = o.optString("category"), source = o.optString("source", "local"), memberIds = o.optString("memberIds"), parentId = o.optString("parentId"),
        start = o.getLong("start"), end = o.getLong("end"), zone = o.getString("zone"), allDay = o.optBoolean("allDay"), repeat = Repeat.valueOf(o.getString("repeat")),
        every = o.optInt("every", 1), rotate = o.optBoolean("rotate"), excluded = o.optString("excluded"), completedDates = o.optString("completedDates"), done = o.optBoolean("done"), pinned = o.optBoolean("pinned"),
        childVisible = o.optBoolean("childVisible", true), expires = o.optLong("expires"), approvalRequired = o.optBoolean("approvalRequired"),
        approvedDates = o.optString("approvedDates"), stars = o.optInt("stars"), position = o.optInt("position"), reminderMinutes = o.optInt("reminderMinutes", -1), author = o.optString("author"),
        version = o.optLong("version", 1), deleted = o.optBoolean("deleted"))
}
