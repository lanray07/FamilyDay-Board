package com.familyday.board

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.familyday.board.data.*
import com.familyday.board.domain.*
import com.familyday.board.security.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.assertEquals
import org.koin.core.context.GlobalContext
import java.io.File
import java.time.*

class BoardUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val koin get() = GlobalContext.get()
    @Before fun seed() = runBlocking {
        val db = koin.get<BoardDatabase>(); val gate = koin.get<ParentGate>(); val dao = db.boardDao()
        gate.lock(); koin.get<SecureVault>().clear()
        dao.clearEntries(); dao.clearMembers(); dao.clearHousehold(); dao.clearQueue()
        gate.setup("123456")
        val repo = koin.get<BoardRepository>()
        repo.createHousehold("The Willow household", "Alex", "Europe/London", 1)
        gate.unlock("123456", dao.members().first().id)
        repo.addMember("Jamie", Role.ADULT, 1); repo.addMember("Sam", Role.CHILD, 2)
        val sam = dao.members().last(); val alex = dao.members().first()
        val today = LocalDate.now(ZoneId.of("Europe/London"))
        fun at(hour: Int) = today.atTime(hour, 0).atZone(ZoneId.of("Europe/London")).toInstant().toEpochMilli()
        repo.save(Entry(kind = Kind.EVENT, title = "Saturday football", notes = "Remember the water bottles", memberIds = sam.id, start = at(10), end = at(11), zone = "Europe/London"))
        repo.save(Entry(kind = Kind.EVENT, title = "Lunch with Grandma", notes = "Meet at the garden café", memberIds = "${alex.id},${sam.id}", start = at(13), end = at(14), zone = "Europe/London"))
        repo.save(Entry(kind = Kind.EVENT, title = "Private appointment", memberIds = alex.id, start = at(16), end = at(17), childVisible = false, zone = "Europe/London"))
        repo.save(Entry(kind = Kind.CHORE, title = "Pack your school bag", memberIds = sam.id, start = at(8), end = at(9), repeat = Repeat.DAILY, stars = 2, approvalRequired = true, zone = "Europe/London"))
        repo.save(Entry(kind = Kind.CHORE, title = "Water the plants", memberIds = alex.id, start = at(9), end = at(10), repeat = Repeat.WEEKLY, zone = "Europe/London"))
        repo.save(Entry(kind = Kind.NOTE, title = "A little family adventure", notes = "Let’s take a walk after lunch. Pick your favourite path!", category = "Celebration", author = "Alex", pinned = true))
        repo.save(Entry(kind = Kind.NOTE, title = "Adults only note", childVisible = false))
        val list = Entry(kind = Kind.LIST, title = "This week’s groceries"); repo.save(list)
        repo.save(Entry(kind = Kind.ITEM, title = "Apples", notes = "A bag for lunchboxes", category = "Produce", parentId = list.id))
        repo.save(Entry(kind = Kind.ITEM, title = "Milk", category = "Dairy", parentId = list.id, position = 1))
        repo.save(Entry(kind = Kind.ITEM, title = "Fresh bread", category = "Bakery", parentId = list.id, position = 2))
        repo.priority("A walk, a good lunch, and a little time together.")
        Unit
    }
    @After fun clean() = runBlocking {
        val dao = koin.get<BoardDatabase>().boardDao()
        koin.get<ParentGate>().lock(); koin.get<SecureVault>().clear()
        dao.clearEntries(); dao.clearMembers(); dao.clearHousehold(); dao.clearQueue()
    }
    private fun settle() { compose.waitForIdle(); Thread.sleep(600) }
    private fun openBoard() {
        // Compact layouts put Board in the overflow menu; child navigation includes it directly.
        if (compose.onAllNodesWithText("Board").fetchSemanticsNodes().isEmpty()) compose.onNodeWithText("Family").performClick()
        compose.onNodeWithText("Board").performClick()
    }
    private fun screenshot(name: String) {
        settle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(compose.activity.getExternalFilesDir(null), "screenshots/$name.png").apply { parentFile!!.mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
    }
    @Test fun adultCanCreateNoteAndChildSeesOnlyAllowedRecords() {
        compose.waitUntil(15000) { compose.onAllNodesWithText("Hello, The Willow household").fetchSemanticsNodes().isNotEmpty() }
        openBoard()
        compose.onNodeWithText("Add note").performClick()
        compose.onNodeWithText("Title").performTextInput("Bring your wellies")
        compose.onNodeWithText("Visible in child spaces and kitchen board").performScrollTo().performClick()
        compose.onNodeWithText("Save", useUnmergedTree = false).performClick()
        compose.waitUntil(10000) { runBlocking { koin.get<BoardDatabase>().boardDao().allEntries().any { it.title == "Bring your wellies" } } }
        compose.onNodeWithContentDescription("Lock board").performClick()
        compose.onNodeWithText("Sam’s space").performClick()
        compose.onAllNodesWithText("Private appointment").assertCountEquals(0)
        compose.onAllNodesWithText("Adults only note").assertCountEquals(0)
        openBoard()
        compose.onNodeWithText("Bring your wellies").assertExists()
        compose.onAllNodesWithText("Add note").assertCountEquals(0)
    }
    @Test fun captureGenuineTabletScreensInBothOrientations() {
        compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        compose.waitUntil(15000) { compose.onAllNodesWithText("Hello, The Willow household").fetchSemanticsNodes().isNotEmpty() }
        screenshot("01-dashboard-landscape")
        compose.onNodeWithText("Calendar", useUnmergedTree = false).performClick(); screenshot("02-calendar-landscape")
        compose.onNodeWithText("Chores", useUnmergedTree = false).performClick(); screenshot("03-chores-landscape")
        compose.onNodeWithText("Lists", useUnmergedTree = false).performClick(); screenshot("04-lists-landscape")
        compose.onNodeWithContentDescription("Kitchen board").performClick(); screenshot("05-kitchen-landscape")
        compose.onNodeWithText("Leave kitchen board").performClick()
        compose.onNodeWithText("Today", useUnmergedTree = false).performClick()
        compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        settle(); screenshot("06-dashboard-portrait")
        compose.onNodeWithContentDescription("Lock board").performClick()
        compose.onNodeWithText("Sam’s space").performClick(); screenshot("07-child-portrait")
    }
    @Test fun savingAnUnchangedAllDayEventAndExpiryRetainsTheirDates() {
        val zone = ZoneId.of("Europe/London")
        val day = LocalDate.now(zone)
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val event = Entry(kind = Kind.EVENT, title = "A whole family day", start = start, end = end, zone = zone.id, allDay = true)
        val note = Entry(kind = Kind.NOTE, title = "A note for today", expires = end)
        runBlocking { koin.get<BoardRepository>().save(event); koin.get<BoardRepository>().save(note) }
        compose.waitUntil(15000) { compose.onAllNodesWithText("Hello, The Willow household").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Calendar").performClick()
        compose.onNodeWithText("Day").performClick()
        compose.onNodeWithText(event.title).performScrollTo().performClick()
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Save both").performClick()
        compose.waitUntil(10000) { runBlocking { koin.get<BoardDatabase>().boardDao().entry(event.id)!!.version > 1 } }
        assertEquals(end, runBlocking { koin.get<BoardDatabase>().boardDao().entry(event.id)!!.end })
        openBoard()
        compose.onNodeWithText(note.title).performScrollTo().performClick()
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10000) { runBlocking { koin.get<BoardDatabase>().boardDao().entry(note.id)!!.version > 1 } }
        assertEquals(end, runBlocking { koin.get<BoardDatabase>().boardDao().entry(note.id)!!.expires })
    }
}
