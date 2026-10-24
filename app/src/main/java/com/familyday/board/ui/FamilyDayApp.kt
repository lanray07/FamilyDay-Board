package com.familyday.board.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyday.board.MainActivity
import com.familyday.board.R
import com.familyday.board.data.Entry
import com.familyday.board.data.CalendarImporter
import com.familyday.board.data.visibleTo
import com.familyday.board.domain.*
import kotlinx.coroutines.*
import org.koin.androidx.compose.koinViewModel
import java.time.*
import java.util.Locale

private enum class Page(val label: Int, val icon: ImageVector) {
    TODAY(R.string.today, Icons.Default.Cottage), CALENDAR(R.string.calendar, Icons.Default.CalendarMonth),
    CHORES(R.string.chores, Icons.Default.TaskAlt), LISTS(R.string.lists, Icons.Default.ShoppingBasket),
    MESSAGES(R.string.messages, Icons.Default.StickyNote2), FAMILY(R.string.family, Icons.Default.People), SETTINGS(R.string.settings, Icons.Default.Settings)
}
@Composable fun FamilyDayApp(vm: BoardViewModel = koinViewModel()) {
    val rawState by vm.state.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val dark by vm.dark.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as MainActivity
    var page by rememberSaveable { mutableStateOf(Page.TODAY) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    val kitchen = session?.board == true
    var keepAwake by rememberSaveable { mutableStateOf(false) }
    var editor by remember { mutableStateOf<Entry?>(null) }
    var editingActions by remember { mutableStateOf<Entry?>(null) }
    var deleting by remember { mutableStateOf<Entry?>(null) }
    var pinOpen by remember { mutableStateOf(false) }
    var afterAuth by remember { mutableStateOf<(() -> Unit)?>(null) }
    var voiceOpen by remember { mutableStateOf(false) }
    var words by rememberSaveable { mutableStateOf("") }
    var voiceUnavailable by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf("") }
    var listPicker by remember { mutableStateOf(false) }
    var calendarCandidates by remember { mutableStateOf<List<Entry>?>(null) }
    var pendingItemTitle by remember { mutableStateOf("") }
    val adult = session?.adult == true
    // Recheck synchronously so a role switch can never show a cached adult frame.
    val state = rawState.copy(entries = rawState.entries.visibleTo(session))
    fun freshAuth(action: () -> Unit) { afterAuth = action; pinOpen = true }
    fun add(kind: Kind, parentId: String = "", title: String = "") {
        if (kind == Kind.ITEM && parentId.isBlank()) { pendingItemTitle = title; listPicker = true; return }
        val zone = state.household?.zone ?: ZoneId.systemDefault().id
        val start = date.atTime(9, 0).atZone(ZoneId.of(zone)).toInstant().toEpochMilli()
        editor = Entry(kind = kind, title = title, parentId = parentId, start = start, end = start + 3600000, zone = zone,
            position = state.entries.count { it.parentId == parentId }, childVisible = kind != Kind.NOTE)
    }
    fun edit(e: Entry) { editingActions = e }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.action { calendarCandidates = CalendarImporter(context, vm.gate).candidates(state.household!!.zone) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        activity.externalActivity = false
        if (uri != null) vm.action {
            vm.gate.requireAdult()
            withContext(Dispatchers.IO) { checkNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(exportText.toByteArray(Charsets.UTF_8)) } }
            exportText = ""
        } else exportText = ""
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        activity.externalActivity = false
        if (uri != null) vm.action {
            vm.gate.requireAdult()
            val text = withContext(Dispatchers.IO) {
                checkNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use { reader ->
                    val buffer = CharArray(5_000_001); var count = 0
                    while (count < buffer.size) { val n = reader.read(buffer, count, buffer.size - count); if (n < 0) break; count += n }
                    require(count <= 5_000_000); String(buffer, 0, count)
                }
            }
            freshAuth { vm.action { vm.backups.restore(text) } }
        }
    }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        activity.externalActivity = false
        if (result.resultCode == Activity.RESULT_OK) words = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
    }
    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
        tts = engine
        onDispose { engine.stop(); engine.shutdown() }
    }
    DisposableEffect(kitchen, keepAwake, session) {
        if (kitchen && keepAwake && session != null) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    LaunchedEffect(session) {
        if (!adult) { page = Page.TODAY; editor = null; editingActions = null; voiceOpen = false; listPicker = false }
        while (session?.adult == true) {
            delay(1000)
            if (vm.session.value?.adult != true) { vm.gate.lock(); break }
        }
    }
    LaunchedEffect(state.household?.zone) { state.household?.let { date = LocalDate.now(ZoneId.of(it.zone)) } }
    LaunchedEffect(kitchen, state.household?.zone) {
        while (kitchen) { date = LocalDate.now(ZoneId.of(state.household?.zone ?: ZoneId.systemDefault().id)); delay(60_000) }
    }
    BackHandler(kitchen || page != Page.TODAY) { if (kitchen) freshAuth { page = Page.TODAY } else page = Page.TODAY }
    FamilyDayTheme(dark) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                when {
                    !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.household == null -> Onboarding(vm)
                    session == null -> LockedBoard(state, { pinOpen = true }, { vm.gate.child(it) })
                    else -> {
                        if (kitchen) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton({ freshAuth { page = Page.TODAY } }) { Text(stringResource(R.string.exit_kitchen)) }
                                Spacer(Modifier.weight(1f))
                                Text(stringResource(R.string.keep_awake), Modifier.weight(2f), style = MaterialTheme.typography.bodySmall)
                                Switch(keepAwake, { keepAwake = it })
                            }
                        } else Surface(color = MaterialTheme.colorScheme.surface) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.Cottage, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                                    Text(stringResource(R.string.local_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (adult) IconButton({ vm.gate.presentBoard() }) { Icon(Icons.Default.Dashboard, stringResource(R.string.kitchen)) }
                                IconButton({ voiceOpen = true }) { Icon(Icons.Default.Mic, stringResource(R.string.voice)) }
                                IconButton({ vm.gate.lock() }) { Icon(Icons.Default.Lock, stringResource(R.string.lock)) }
                            }
                        }
                        BoxWithConstraints(Modifier.weight(1f)) {
                            val wide = maxWidth >= 840.dp && !kitchen
                            val pages = if (adult) Page.entries else listOf(Page.TODAY, Page.CALENDAR, Page.CHORES, Page.MESSAGES)
                            Column {
                                Row(Modifier.weight(1f)) {
                                    if (wide) NavigationRail(containerColor = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
                                        Spacer(Modifier.height(16.dp))
                                        pages.forEach { p -> NavigationRailItem(selected = page == p, onClick = { page = p }, icon = { Icon(p.icon, null) }, label = { Text(stringResource(p.label)) }, modifier = Modifier.padding(vertical = 4.dp)) }
                                    }
                                    Box(Modifier.weight(1f)) {
                                        when (if (kitchen) Page.TODAY else page) {
                                            Page.TODAY -> Dashboard(state, vm, date, adult || kitchen, kitchen, ::edit, { add(it) })
                                            Page.CALENDAR -> CalendarScreen(state, adult, date, { date = it }, ::edit, { add(Kind.EVENT) }, { e, day -> vm.action { vm.repo.save(e.copy(excluded = (e.excluded.split(',').filter { it.isNotBlank() } + day.toString()).distinct().joinToString(","))) } })
                                            Page.CHORES -> ChoresScreen(state, vm, adult, date, { date = it }, ::edit, { add(Kind.CHORE) })
                                            Page.MESSAGES -> MessagesScreen(state, vm, adult, ::edit, { add(Kind.NOTE) })
                                            Page.LISTS -> if (adult) ListsScreen(state, vm, ::edit, { k, parent -> add(k, parent) }, { text ->
                                                freshAuth { activity.externalActivity = true; context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, null)); activity.externalActivity = false }
                                            })
                                            Page.FAMILY -> if (adult) FamilyScreen(state, vm, ::freshAuth)
                                            Page.SETTINGS -> if (adult) SettingsScreen(state, vm,
                                                export = { freshAuth { vm.action { exportText = vm.backups.export(); activity.externalActivity = true; exportLauncher.launch("FamilyDay-backup-${LocalDate.now()}.json") } } },
                                                restore = { freshAuth { activity.externalActivity = true; importLauncher.launch(arrayOf("application/json", "text/plain")) } },
                                                notifications = { if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                    else context.startActivity(Intent("android.settings.APP_NOTIFICATION_SETTINGS").putExtra("android.provider.extra.APP_PACKAGE", context.packageName)) },
                                                importCalendar = { freshAuth { calendarPermission.launch(Manifest.permission.READ_CALENDAR) } }, freshAuth = ::freshAuth)
                                        }
                                    }
                                }
                                if (!wide && !kitchen) {
                                    var more by remember { mutableStateOf(false) }
                                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                                        pages.take(4).forEach { p -> NavigationBarItem(page == p, { page = p }, { Icon(p.icon, null) }, label = { Text(stringResource(p.label)) }) }
                                        if (adult) {
                                            NavigationBarItem(page in pages.drop(4), { more = true }, { Icon(Icons.Default.MoreHoriz, null) }, label = { Text(stringResource(R.string.family)) })
                                            DropdownMenu(more, { more = false }) { pages.drop(4).forEach { p -> DropdownMenuItem({ Text(stringResource(p.label)) }, { page = p; more = false }) } }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (pinOpen) PinDialog(vm, state.members, { pinOpen = false; afterAuth = null }, {
            pinOpen = false; val next = afterAuth; afterAuth = null; next?.invoke()
        })
        if (adult) {
            calendarCandidates?.let { candidates ->
                var selected by remember { mutableStateOf(emptySet<String>()) }
                AlertDialog(onDismissRequest = { calendarCandidates = null }, title = { Text(stringResource(R.string.calendar_import_title)) }, text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (candidates.isEmpty()) Text(stringResource(R.string.calendar_empty))
                        candidates.forEach { e -> SettingToggle("${e.title} · ${formatDate(Instant.ofEpochMilli(e.start).atZone(ZoneId.of(e.zone)).toLocalDate())}", e.id in selected) { checked -> selected = if (checked) selected + e.id else selected - e.id } }
                    }
                }, confirmButton = { TextButton({ vm.action { candidates.filter { it.id in selected }.forEach { e -> vm.repo.save(e.copy(version = state.entries.firstOrNull { it.id == e.id }?.version ?: 1)) }; calendarCandidates = null } }, enabled = selected.isNotEmpty()) { Text(stringResource(R.string.save)) } },
                    dismissButton = { TextButton({ calendarCandidates = null }) { Text(stringResource(R.string.cancel)) } })
            }
            editor?.let { entry -> key(entry.id) { EntryEditor(entry, state, vm) { editor = null } } }
            editingActions?.let { entry -> AlertDialog(onDismissRequest = { editingActions = null }, title = { Text(entry.title) },
                confirmButton = { TextButton({ editingActions = null; editor = entry }) { Text(stringResource(R.string.edit)) } },
                dismissButton = { TextButton({ editingActions = null; deleting = entry }) { Text(stringResource(R.string.delete)) } }) }
            deleting?.let { entry -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.delete_title, stringResource(entry.kind.label()))) }, text = { Text(stringResource(R.string.delete_body)) },
                confirmButton = { TextButton({ vm.action { vm.repo.delete(entry); deleting = null } }) { Text(stringResource(R.string.delete)) } }, dismissButton = { TextButton({ deleting = null }) { Text(stringResource(R.string.cancel)) } }) }
            if (listPicker) AlertDialog(onDismissRequest = { listPicker = false }, title = { Text(stringResource(R.string.select_list)) }, text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    state.entries.filter { it.kind == Kind.LIST }.forEach { list -> TextButton({ listPicker = false; add(Kind.ITEM, list.id, pendingItemTitle) }) { Text(list.title) } }
                    TextButton({ listPicker = false; add(Kind.LIST) }) { Text(stringResource(R.string.add_list)) }
                }
            }, confirmButton = { TextButton({ listPicker = false }) { Text(stringResource(R.string.cancel)) } })
        }
        if (voiceOpen && session != null) AlertDialog(onDismissRequest = { voiceOpen = false; tts?.stop() }, title = { Text(stringResource(R.string.voice_title)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(if (adult) R.string.voice_body else R.string.voice_child_help))
                if (adult) {
                    OutlinedButton({
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                        try { activity.externalActivity = true; speechLauncher.launch(intent) } catch (_: android.content.ActivityNotFoundException) { activity.externalActivity = false; voiceUnavailable = true }
                    }) { Text(stringResource(R.string.listen)) }
                    if (voiceUnavailable) Text(stringResource(R.string.voice_unavailable))
                    OutlinedTextField(words, { words = it.take(4000) }, label = { Text(stringResource(R.string.recognised_text)) }, modifier = Modifier.fillMaxWidth())
                    if (words.isNotBlank()) {
                        TextButton({ voiceOpen = false; add(Kind.NOTE, title = words.take(200)) }) { Text(stringResource(R.string.voice_review)) }
                        TextButton({ voiceOpen = false; add(Kind.ITEM, title = words.replace(Regex("(?i)^add\\s+|\\s+to (the |my )?shopping list[.!]?$"), "").take(200)) }) { Text(stringResource(R.string.voice_item)) }
                        TextButton({ voiceOpen = false; add(Kind.EVENT, title = words.take(200)) }) { Text(stringResource(R.string.voice_event)) }
                    }
                }
                OutlinedButton({
                    if (ttsReady && tts?.setLanguage(Locale.getDefault())?.let { it >= TextToSpeech.LANG_AVAILABLE } == true) {
                        val text = state.entries.filter { it.kind in listOf(Kind.EVENT, Kind.CHORE) && it.on(date) }.joinToString(". ") { it.title }
                        tts?.speak(text.ifBlank { context.getString(R.string.empty_events) }, TextToSpeech.QUEUE_FLUSH, null, "today")
                    } else vm.error.value = "tts"
                }) { Text(stringResource(R.string.read_today)) }
            }
        }, confirmButton = { TextButton({ voiceOpen = false; tts?.stop() }) { Text(stringResource(R.string.close)) } })
        error?.let { message -> AlertDialog(onDismissRequest = { vm.error.value = null }, title = { Text(stringResource(R.string.error_title)) }, text = {
            Text(stringResource(when (message) { "conflict" -> R.string.conflict_error; "tts" -> R.string.tts_unavailable; else -> R.string.error_body }))
        }, confirmButton = { TextButton({ vm.error.value = null }) { Text(stringResource(R.string.close)) } }) }
    }
}
