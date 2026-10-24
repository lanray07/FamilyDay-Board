package com.familyday.board.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.text.input.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.familyday.board.R
import com.familyday.board.data.Member
import com.familyday.board.domain.Role
import com.familyday.board.security.PinHash
import java.time.ZoneId

@Composable fun Onboarding(vm: BoardViewModel) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var owner by rememberSaveable { mutableStateOf("") }
    var zone by rememberSaveable { mutableStateOf(ZoneId.systemDefault().id) }
    var week by rememberSaveable { mutableIntStateOf(1) }
    var pin by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }
    val busy by vm.busy.collectAsState()
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.app_name), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
            if (step < 3) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().height(180.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(when (step) { 0 -> Icons.Default.CalendarMonth; 1 -> Icons.Default.Cottage; else -> Icons.Default.CloudDone }, null, Modifier.size(88.dp), tint = MaterialTheme.colorScheme.primary) }
                }
                Text(stringResource(when (step) { 0 -> R.string.welcome_title; 1 -> R.string.tasks_title; else -> R.string.offline_title }), style = MaterialTheme.typography.displaySmall)
                Text(stringResource(when (step) { 0 -> R.string.welcome_body; 1 -> R.string.tasks_body; else -> R.string.offline_body }), style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(progress = { (step + 1) / 4f }, Modifier.fillMaxWidth())
                Button({ step++ }, Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text(stringResource(if (step == 2) R.string.get_started else R.string.next)) }
            } else {
                Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.setup_body))
                OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.household_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(owner, { owner = it.take(50) }, label = { Text(stringResource(R.string.your_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(zone, { zone = it }, label = { Text(stringResource(R.string.zone)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.week_start))
                ChipsRow {
                    FilterChip(week == 1, { week = 1 }, { Text(stringResource(R.string.monday)) })
                    FilterChip(week == 7, { week = 7 }, { Text(stringResource(R.string.sunday)) })
                }
                PinField(pin, { pin = it }, R.string.pin)
                PinField(confirm, { confirm = it }, R.string.confirm_pin)
                Text(stringResource(R.string.pin_help), style = MaterialTheme.typography.bodySmall)
                Button({ vm.setup(name, owner, pin, zone, week) }, enabled = !busy && name.isNotBlank() && owner.isNotBlank() && PinHash.valid(pin) && pin == confirm && runCatching { ZoneId.of(zone) }.isSuccess,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text(stringResource(if (busy) R.string.saving else R.string.get_started)) }
            }
        }
    }
}
@Composable fun PinField(pin: String, change: (String) -> Unit, label: Int = R.string.pin) {
    OutlinedTextField(pin, { change(it.filter { c -> c in '0'..'9' }.take(12)) }, label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
}
@Composable fun PinDialog(vm: BoardViewModel, members: List<Member>, close: () -> Unit, success: () -> Unit) {
    var pin by remember { mutableStateOf("") }; var failed by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = close, title = { Text(stringResource(R.string.adult_gate)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.adult_gate_body)); PinField(pin, { pin = it; failed = false })
            if (failed) Text(stringResource(R.string.pin_error), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton({
        vm.action {
            busy = true
            try {
                val good = runCatching { vm.gate.unlock(pin, members.first { it.role == Role.OWNER }.id) }.getOrDefault(false)
                if (good) success() else { failed = true; pin = "" }
            } finally { busy = false }
        }
    }, enabled = !busy && pin.length >= 6) { Text(stringResource(R.string.next)) } }, dismissButton = { TextButton(close) { Text(stringResource(R.string.cancel)) } })
}
@Composable fun LockedBoard(state: BoardState, unlock: () -> Unit, child: (String) -> Unit) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Icon(Icons.Default.Cottage, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text(state.household?.name.orEmpty(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.locked_title), style = MaterialTheme.typography.displaySmall)
            Text(stringResource(R.string.locked_body))
            Button(unlock, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.unlock)) }
            state.members.filter { it.role == Role.CHILD }.forEach { member ->
                OutlinedButton({ child(member.id) }, Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                    MemberAvatar(member); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.child_space, member.name))
                }
            }
        }
    }
}
