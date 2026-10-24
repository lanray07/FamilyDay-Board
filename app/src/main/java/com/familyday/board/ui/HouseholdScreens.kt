package com.familyday.board.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.familyday.board.R
import com.familyday.board.data.*
import com.familyday.board.domain.*
import java.time.LocalDate
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.familyday.board.sync.SyncStatus

@Composable fun ListsScreen(state: BoardState, vm: BoardViewModel, edit: (Entry) -> Unit, add: (Kind, String) -> Unit, share: (String) -> Unit) {
    var selected by remember { mutableStateOf("") }
    var shopping by remember { mutableStateOf(false) }
    val lists = state.entries.filter { it.kind == Kind.LIST }
    val list = lists.firstOrNull { it.id == selected } ?: lists.firstOrNull()
    val items = state.entries.filter { it.kind == Kind.ITEM && it.parentId == list?.id }.sortedBy { it.position }
    ScreenBody {
        PageTitle(stringResource(R.string.lists)) { AddButton(R.string.add_list) { add(Kind.LIST, "") } }
        ChipsRow { lists.forEach { l -> FilterChip(l.id == list?.id, { selected = l.id }, { Text(l.title) }) } }
        if (list == null) EmptyState(R.string.empty_lists) else {
            PageTitle(list.title, stringResource(R.string.progress, items.count { it.done }, items.size)) { AddButton(R.string.add_item) { add(Kind.ITEM, list.id) } }
            if (items.isNotEmpty()) LinearProgressIndicator(progress = { items.count { it.done }.toFloat() / items.size }, Modifier.fillMaxWidth())
            ChipsRow {
                FilterChip(shopping, { shopping = !shopping }, { Text(stringResource(if (shopping) R.string.exit_shopping else R.string.shopping_mode)) })
                if (!shopping) {
                    AssistChip({ edit(list) }, { Text(stringResource(R.string.edit)) })
                    val copyName = stringResource(R.string.copy_name, list.title)
                    AssistChip({ vm.action { vm.repo.duplicateList(list, copyName) } }, { Text(stringResource(R.string.duplicate)) })
                    AssistChip({ share(list.title + "\n" + items.joinToString("\n") { (if (it.done) "☑ " else "☐ ") + it.title + if (it.notes.isBlank()) "" else " — " + it.notes }) }, { Text(stringResource(R.string.share_list)) })
                }
            }
            if (items.isEmpty()) EmptyState(R.string.empty_items)
            items.groupBy { it.category }.forEach { (category, group) ->
                BoardCard(category.ifBlank { stringResource(R.string.shopping_summary) }, Icons.Default.ShoppingBasket, Modifier.fillMaxWidth()) {
                    group.forEach { e ->
                        EntryRow(e, state.members, LocalDate.now(), !shopping, shopping,
                            onToggle = { vm.action { vm.repo.complete(e.id, LocalDate.now()) } }, onEdit = { edit(e) })
                        if (!shopping) Row {
                            IconButton({ vm.action { vm.repo.move(e.id, -1) } }, enabled = items.indexOf(e) > 0) { Icon(Icons.Default.ArrowUpward, stringResource(R.string.move_up)) }
                            IconButton({ vm.action { vm.repo.move(e.id, 1) } }, enabled = items.indexOf(e) < items.lastIndex) { Icon(Icons.Default.ArrowDownward, stringResource(R.string.move_down)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable fun FamilyScreen(state: BoardState, vm: BoardViewModel, freshAuth: (() -> Unit) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Member?>(null) }
    ScreenBody {
        PageTitle(stringResource(R.string.family)) { AddButton(R.string.new_member) { adding = true } }
        Text(stringResource(R.string.family_help))
        state.members.forEach { m -> Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MemberAvatar(m, 54.dp)
                Column(Modifier.weight(1f)) { Text(m.name, style = MaterialTheme.typography.titleLarge); Text(stringResource(m.role.label())) }
                if (m.role != Role.OWNER) IconButton({ removing = m }) { Icon(Icons.Default.PersonRemove, stringResource(R.string.remove_member)) }
            }
        } }
    }
    if (adding) {
        var name by remember { mutableStateOf("") }; var role by remember { mutableStateOf(Role.CHILD) }; var color by remember { mutableIntStateOf(state.members.size.mod(6)) }
        AlertDialog(onDismissRequest = { adding = false }, title = { Text(stringResource(R.string.new_member)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(name, { name = it.take(50) }, label = { Text(stringResource(R.string.member_name)) }, singleLine = true)
                Text(stringResource(R.string.role))
                ChipsRow { Role.entries.filter { it != Role.OWNER }.forEach { r -> FilterChip(role == r, { role = r }, { Text(stringResource(r.label())) }) } }
                Text(stringResource(R.string.colour))
                Row { MemberColours.forEachIndexed { i, c ->
                    IconButton({ color = i }) { Icon(if (color == i) Icons.Default.CheckCircle else Icons.Default.Circle, stringResource(R.string.member_colour, i + 1), tint = c) }
                } }
            }
        }, confirmButton = { TextButton({ vm.action { vm.repo.addMember(name, role, color); adding = false } }, enabled = name.isNotBlank() && state.members.size < 6) { Text(stringResource(R.string.add)) } },
            dismissButton = { TextButton({ adding = false }) { Text(stringResource(R.string.cancel)) } })
    }
    removing?.let { member -> AlertDialog(onDismissRequest = { removing = null }, title = { Text(stringResource(R.string.remove_member)) },
        text = { Text(stringResource(R.string.remove_member_body, member.name)) }, confirmButton = { TextButton({ removing = null; freshAuth { vm.action { vm.repo.removeMember(member.id) } } }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton({ removing = null }) { Text(stringResource(R.string.cancel)) } }) }
}

@Composable fun SettingsScreen(state: BoardState, vm: BoardViewModel, export: () -> Unit, restore: () -> Unit, notifications: () -> Unit, importCalendar: () -> Unit, freshAuth: (() -> Unit) -> Unit) {
    val dark by vm.dark.collectAsState(); val rewards by vm.rewards.collectAsState(); val quiet by vm.quiet.collectAsState()
    var priority by remember(state.household?.priority) { mutableStateOf(state.household?.priority.orEmpty()) }
    var erasing by remember { mutableStateOf(false) }
    ScreenBody {
        PageTitle(stringResource(R.string.settings))
        BoardCard(stringResource(R.string.appearance), Icons.Default.Palette, Modifier.fillMaxWidth()) {
            SettingToggle(stringResource(R.string.dark_theme), dark) { vm.action { vm.gate.requireAdult(); vm.preferences.set("dark", it) } }
            SettingToggle(stringResource(R.string.rewards), rewards) { freshAuth { vm.action { vm.gate.requireAdult(); vm.preferences.set("rewards", it) } } }
            OutlinedTextField(priority, { priority = it.take(200) }, label = { Text(stringResource(R.string.priority)) }, placeholder = { Text(stringResource(R.string.priority_hint)) }, modifier = Modifier.fillMaxWidth())
            TextButton({ vm.action { vm.repo.priority(priority) } }) { Text(stringResource(R.string.save)) }
            Text(stringResource(R.string.language_help))
        }
        BoardCard(stringResource(R.string.reminders), Icons.Default.Notifications, Modifier.fillMaxWidth()) {
            SettingToggle(stringResource(R.string.quiet_hours), quiet) { vm.action { vm.gate.requireAdult(); vm.preferences.set("quiet", it) } }
            OutlinedButton(notifications) { Text(stringResource(R.string.notifications)) }
            Text(stringResource(R.string.notification_help))
        }
        BoardCard(stringResource(R.string.privacy), Icons.Default.Shield, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.privacy_body)); Text(stringResource(R.string.backup_warning))
            OutlinedButton(export) { Text(stringResource(R.string.backup)) }
            OutlinedButton(restore) { Text(stringResource(R.string.restore)) }
            TextButton({ erasing = true }) { Text(stringResource(R.string.erase), color = MaterialTheme.colorScheme.error) }
        }
        SyncSettings(vm, freshAuth)
        BoardCard(stringResource(R.string.import_calendar), Icons.Default.CalendarMonth, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.calendar_consent))
            OutlinedButton(importCalendar) { Text(stringResource(R.string.import_calendar)) }
        }
        BoardCard(stringResource(R.string.development), Icons.Default.Info, Modifier.fillMaxWidth()) { Text(stringResource(R.string.development_body)) }
    }
    if (erasing) AlertDialog(onDismissRequest = { erasing = false }, title = { Text(stringResource(R.string.erase)) }, text = { Text(stringResource(R.string.erase_body)) },
        confirmButton = { TextButton({ erasing = false; freshAuth { vm.action { vm.repo.erase() } } }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton({ erasing = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun SyncSettings(vm: BoardViewModel, freshAuth: (() -> Unit) -> Unit) {
    val status by vm.sync.status.collectAsState()
    var connected by remember { mutableStateOf(vm.sync.connected) }
    var url by remember { mutableStateOf(vm.sync.endpoint()) }
    var token by remember { mutableStateOf("") }
    var resolving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var invitation by remember { mutableStateOf<String?>(null) }
    var devices by remember { mutableStateOf<List<String>?>(null) }
    BoardCard(stringResource(R.string.connected), Icons.Default.CloudSync, Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.sync_consent))
        if (!connected) {
            OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.sync_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(token, { token = it }, label = { Text(stringResource(R.string.sync_token)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedButton({ freshAuth { vm.action { vm.sync.configure(url, token); token = ""; connected = true; vm.sync.sync() } } }, enabled = url.startsWith("https://") && token.length >= 32) { Text(stringResource(R.string.connect)) }
            TextButton({ freshAuth { vm.action { vm.sync.join(url, token); token = ""; connected = true; vm.sync.sync() } } }, enabled = url.startsWith("https://") && token.length >= 32) { Text(stringResource(R.string.join)) }
        } else {
            Text(url, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(when (status) { SyncStatus.CONFLICT -> R.string.sync_conflict; SyncStatus.ERROR -> R.string.sync_error; SyncStatus.SYNCING -> R.string.sync_working; else -> R.string.sync_ready }))
            OutlinedButton({ vm.action { vm.gate.requireAdult(); vm.sync.sync() } }) { Text(stringResource(R.string.sync_now)) }
            if (status == SyncStatus.CONFLICT) Button({ resolving = true }) { Text(stringResource(R.string.resolve_sync)) }
            TextButton({ freshAuth { vm.action { invitation = vm.sync.invite() } } }) { Text(stringResource(R.string.invite_device)) }
            TextButton({ freshAuth { vm.action { devices = vm.sync.devices() } } }) { Text(stringResource(R.string.manage_devices)) }
            TextButton({ freshAuth { vm.action { vm.sync.disconnect(); connected = false } } }) { Text(stringResource(R.string.disconnect)) }
            TextButton({ deleting = true }) { Text(stringResource(R.string.delete_remote), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (resolving) AlertDialog(onDismissRequest = { resolving = false }, title = { Text(stringResource(R.string.resolve_sync)) }, text = { Text(stringResource(R.string.resolve_body)) },
        confirmButton = { TextButton({ resolving = false; freshAuth { vm.action { vm.sync.resolve(true); vm.sync.sync() } } }) { Text(stringResource(R.string.keep_local)) } },
        dismissButton = { TextButton({ resolving = false; freshAuth { vm.action { vm.sync.resolve(false) } } }) { Text(stringResource(R.string.use_server)) } })
    invitation?.let { code -> AlertDialog(onDismissRequest = { invitation = null }, title = { Text(stringResource(R.string.invite_device)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(stringResource(R.string.invite_help)); androidx.compose.foundation.text.selection.SelectionContainer { Text(code) } }
    }, confirmButton = { TextButton({ invitation = null }) { Text(stringResource(R.string.close)) } }) }
    devices?.let { all -> AlertDialog(onDismissRequest = { devices = null }, title = { Text(stringResource(R.string.manage_devices)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) { all.forEach { id -> Column { Text(id, style = MaterialTheme.typography.bodySmall); TextButton({ devices = null; freshAuth { vm.action { vm.sync.revoke(id) } } }) { Text(stringResource(R.string.revoke)) } } } }
    }, confirmButton = { TextButton({ devices = null }) { Text(stringResource(R.string.close)) } }) }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text(stringResource(R.string.delete_remote)) }, text = { Text(stringResource(R.string.delete_remote_body)) },
        confirmButton = { TextButton({ deleting = false; freshAuth { vm.action { vm.sync.deleteRemote(); connected = false } } }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton({ deleting = false }) { Text(stringResource(R.string.cancel)) } })
}
