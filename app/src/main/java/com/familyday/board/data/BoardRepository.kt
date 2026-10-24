package com.familyday.board.data

import androidx.room.withTransaction
import com.familyday.board.domain.*
import com.familyday.board.security.ParentGate
import kotlinx.coroutines.flow.*
import java.time.*

/** The same policy is applied before emission and synchronously when a UI session changes. */
fun List<Entry>.visibleTo(session: Session?): List<Entry> {
    if (session?.adult == true) return this
    if (session?.board == true) {
        val publicLists = filter { it.kind == Kind.LIST && it.childVisible }.map { it.id }.toSet()
        return filter { it.childVisible && (it.kind != Kind.ITEM || it.parentId in publicLists) }
    }
    if (session?.role != Role.CHILD) return emptyList()
    return filter { it.childVisible && when (it.kind) {
        Kind.EVENT -> it.memberIds.isBlank() || it.assigned(session.memberId)
        Kind.CHORE -> it.assigned(session.memberId)
        Kind.NOTE -> true
        else -> false
    } }
}

class BoardRepository(private val db: BoardDatabase, private val gate: ParentGate) {
    private val dao = db.boardDao()
    val household = dao.householdFlow()
    val members = dao.membersFlow()
    // Permission filtering happens before data reaches any child UI or voice action.
    val entries = combine(dao.entriesFlow(), gate.session) { all, s ->
        all.visibleTo(s)
    }
    suspend fun createHousehold(name: String, owner: String, zone: String, weekStart: Int) {
        check(gate.configured && dao.household() == null)
        require(name.isNotBlank() && owner.isNotBlank())
        ZoneId.of(zone)
        require(weekStart in 1..7)
        db.withTransaction {
            dao.put(Household(name = name.trim(), zone = zone, weekStart = weekStart))
            dao.put(Member(name = owner.trim(), role = Role.OWNER))
            dao.enqueue(Mutation(entityId = "household", version = 1))
        }
    }
    suspend fun save(entry: Entry) {
        gate.requireAdult()
        require(entry.title.isNotBlank() && entry.title.length <= 200 && entry.notes.length <= 4000)
        ZoneId.of(entry.zone)
        require(entry.end > entry.start)
        require(entry.end - entry.start <= 366L * 86_400_000)
        require(entry.stars in 0..10)
        require(entry.every in 1..365)
        if (entry.kind == Kind.ITEM) check(dao.entry(entry.parentId)?.let { it.kind == Kind.LIST && !it.deleted } == true)
        val memberIds = dao.members().map { it.id }.toSet()
        require(entry.memberIds.isBlank() || entry.memberIds.split(',').all { it in memberIds })
        db.withTransaction {
            val old = dao.entry(entry.id)
            check(old == null || old.version == entry.version) { "conflict" }
            val saved = entry.copy(title = entry.title.trim(), updated = System.currentTimeMillis(), version = (old?.version ?: 0) + 1)
            dao.put(saved)
            dao.enqueue(Mutation(entityId = saved.id, version = saved.version))
        }
    }
    suspend fun complete(id: String, date: LocalDate) {
        db.withTransaction {
            val e = checkNotNull(dao.entry(id))
            val s = checkNotNull(gate.session.value)
            check(s.adult || (s.role == Role.CHILD && e.kind == Kind.CHORE && e.childVisible && e.assignedOn(s.memberId, date)))
            check(!e.deleted)
            check(e.kind in listOf(Kind.CHORE, Kind.ITEM, Kind.NOTE))
            if (e.kind == Kind.CHORE) check(e.on(date))
            val dates = e.completedDates.split(',').filter { it.isNotBlank() }.toMutableSet()
            if (!dates.add(date.toString())) dates.remove(date.toString())
            val updated = e.copy(done = !e.done, completedDates = dates.joinToString(","),
                approvedDates = e.approvedDates.split(',').filter { it != date.toString() }.joinToString(","),
                version = e.version + 1, updated = System.currentTimeMillis())
            dao.put(updated)
            dao.enqueue(Mutation(entityId = e.id, version = updated.version))
        }
    }
    suspend fun approve(id: String, date: LocalDate) {
        gate.requireAdult()
        val e = checkNotNull(dao.entry(id))
        require(e.completed(date))
        save(e.copy(approvedDates = (e.approvedDates.split(',').filter { it.isNotBlank() } + date.toString()).distinct().joinToString(",")))
    }
    suspend fun delete(e: Entry) {
        gate.requireAdult()
        db.withTransaction {
            save(e.copy(deleted = true))
            if (e.kind == Kind.LIST) dao.allEntries().filter { it.parentId == e.id && !it.deleted }.forEach { save(it.copy(deleted = true)) }
        }
    }
    suspend fun addMember(name: String, role: Role, color: Int) {
        gate.requireAdult()
        require(name.isNotBlank() && name.length <= 50 && role != Role.OWNER)
        db.withTransaction {
            check(dao.members().size < 6) { "member_limit" }
            dao.put(Member(name = name.trim(), role = role, color = color))
            dao.enqueue(Mutation(entityId = "members", version = System.currentTimeMillis()))
        }
    }
    suspend fun removeMember(id: String) {
        gate.requireAdult()
        check(dao.members().first { it.id == id }.role != Role.OWNER)
        db.withTransaction {
            dao.allEntries().filter { it.assigned(id) }.forEach { save(it.copy(memberIds = it.memberIds.split(',').filter { m -> m != id }.joinToString(","))) }
            dao.removeMember(id)
            dao.enqueue(Mutation(entityId = "members", version = System.currentTimeMillis()))
        }
    }
    suspend fun priority(value: String) {
        gate.requireAdult()
        db.withTransaction { dao.household()?.let { dao.put(it.copy(priority = value.take(200))); dao.enqueue(Mutation(entityId = "household", version = System.currentTimeMillis())) } }
    }
    suspend fun move(id: String, direction: Int) {
        gate.requireAdult()
        db.withTransaction {
            val e = checkNotNull(dao.entry(id))
            val siblings = dao.allEntries().filter { !it.deleted && it.kind == Kind.ITEM && it.parentId == e.parentId }.sortedWith(compareBy<Entry> { it.position }.thenBy { it.start }).toMutableList()
            val index = siblings.indexOfFirst { it.id == id }
            val next = index + direction
            if (index >= 0 && next in siblings.indices) {
                java.util.Collections.swap(siblings, index, next)
                siblings.forEachIndexed { i, item -> if (item.position != i) save(item.copy(position = i)) }
            }
        }
    }
    suspend fun duplicateList(e: Entry, name: String) {
        gate.requireAdult()
        require(e.kind == Kind.LIST)
        db.withTransaction {
            val copy = e.copy(id = newId(), title = name, version = 1)
            save(copy)
            dao.allEntries().filter { it.parentId == e.id && !it.deleted }.forEach { save(it.copy(id = newId(), parentId = copy.id, done = false, version = 1)) }
        }
    }
    suspend fun erase() {
        gate.requireAdult()
        db.withTransaction { dao.clearEntries(); dao.clearMembers(); dao.clearHousehold(); dao.clearQueue() }
        gate.reset()
    }
}
