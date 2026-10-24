package com.familyday.board

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.familyday.board.data.*
import com.familyday.board.domain.*
import com.familyday.board.security.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: BoardDatabase
    private lateinit var gate: ParentGate
    private lateinit var repo: BoardRepository
    @Before fun before() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SecureVault(context, "test_parent_vault").clear()
        db = Room.inMemoryDatabaseBuilder(context, BoardDatabase::class.java).build()
        gate = ParentGate(SecureVault(context, "test_parent_vault")); repo = BoardRepository(db, gate)
        gate.setup("123456"); repo.createHousehold("Test home", "Alex", "Europe/London", 1)
        gate.unlock("123456", db.boardDao().members().first().id)
        Unit
    }
    @After fun after() { SecureVault(ApplicationProvider.getApplicationContext(), "test_parent_vault").clear(); db.close() }
    private suspend fun child(): Member { repo.addMember("Sam", Role.CHILD, 1); return db.boardDao().members().last() }
    @Test fun childCannotReadPrivateNotesOrShoppingAndCannotWriteAdultRecords() = runBlocking {
        val child = child()
        repo.save(Entry(kind = Kind.NOTE, title = "Private", childVisible = false))
        repo.save(Entry(kind = Kind.NOTE, title = "Shared", childVisible = true))
        repo.save(Entry(kind = Kind.LIST, title = "Shopping"))
        gate.child(child.id)
        assertEquals(listOf("Shared"), repo.entries.first().map { it.title })
        assertTrue(runCatching { repo.save(Entry(kind = Kind.NOTE, title = "Forged")) }.isFailure)
        assertTrue(runCatching { repo.addMember("Intruder", Role.ADULT, 0) }.isFailure)
        assertTrue(runCatching { repo.erase() }.isFailure)
    }
    @Test fun childCompletesOnlyOwnVisibleChoresAndCannotApprove() = runBlocking {
        val child = child(); val today = LocalDate.now(ZoneId.of("Europe/London"))
        val own = Entry(kind = Kind.CHORE, title = "School bag", memberIds = child.id, zone = "Europe/London", approvalRequired = true)
        val other = Entry(kind = Kind.CHORE, title = "Other job")
        repo.save(own); repo.save(other); gate.child(child.id)
        repo.complete(own.id, today)
        assertTrue(db.boardDao().entry(own.id)!!.completed(today))
        assertFalse(db.boardDao().entry(own.id)!!.approved(today))
        assertTrue(runCatching { repo.approve(own.id, today) }.isFailure)
        assertTrue(runCatching { repo.complete(other.id, today) }.isFailure)
    }
    @Test fun staleVersionsCannotSilentlyOverwriteAndDeletesHaveDurableTombstones() = runBlocking {
        val e = Entry(kind = Kind.NOTE, title = "Initial")
        repo.save(e)
        val old = db.boardDao().entry(e.id)!!
        repo.save(old.copy(title = "Updated"))
        assertTrue(runCatching { repo.save(old.copy(title = "Stale")) }.isFailure)
        repo.delete(db.boardDao().entry(e.id)!!)
        assertTrue(db.boardDao().entry(e.id)!!.deleted)
        assertTrue(repo.entries.first().isEmpty())
        assertEquals(3, db.boardDao().pending().count { it.entityId == e.id })
    }
    @Test fun backupRestoreIsAtomicAndRetainsAssignments() = runBlocking {
        val child = child()
        repo.save(Entry(kind = Kind.CHORE, title = "Feed cat", memberIds = child.id))
        val backups = BackupService(db, gate)
        val json = backups.export()
        assertFalse(json.contains("123456"))
        assertTrue(runCatching { backups.restore("{}") }.isFailure)
        assertEquals(1, repo.entries.first().size)
        backups.restore(json)
        assertNull(gate.session.value)
        gate.unlock("123456", db.boardDao().members().first().id)
        assertTrue(repo.entries.first().single().assigned(child.id))
    }
    @Test fun fiveIncorrectPinsCausePersistentLockout() = runBlocking {
        val owner = db.boardDao().members().first().id
        gate.lock()
        repeat(5) { assertFalse(gate.unlock("654321", owner)) }
        val freshGate = ParentGate(SecureVault(ApplicationProvider.getApplicationContext(), "test_parent_vault"))
        assertTrue(runCatching { freshGate.unlock("123456", owner) }.isFailure)
    }
    @Test fun kitchenBoardCanReadPublicContentButHasNoAdultPrivileges() = runBlocking {
        val list = Entry(kind = Kind.LIST, title = "Private gifts", childVisible = false)
        repo.save(list)
        repo.save(Entry(kind = Kind.ITEM, title = "A surprise", parentId = list.id))
        repo.save(Entry(kind = Kind.NOTE, title = "A public reminder", childVisible = true))
        repo.save(Entry(kind = Kind.NOTE, title = "Private note", childVisible = false))
        gate.presentBoard()
        assertTrue(gate.session.value!!.board)
        assertFalse(gate.session.value!!.adult)
        assertEquals(Long.MAX_VALUE, gate.session.value!!.expiresAt)
        assertEquals(listOf("A public reminder"), repo.entries.first().map { it.title })
        assertTrue(runCatching { repo.addMember("Uninvited", Role.ADULT, 0) }.isFailure)
        assertTrue(runCatching { repo.erase() }.isFailure)
        assertTrue(runCatching { repo.save(Entry(kind = Kind.NOTE, title = "Forged")) }.isFailure)
    }
}
