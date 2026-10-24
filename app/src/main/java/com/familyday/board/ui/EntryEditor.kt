package com.familyday.board.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.familyday.board.R
import com.familyday.board.data.*
import com.familyday.board.domain.*
import java.time.*

@Composable fun DateButton(label: String, date: LocalDate, change: (LocalDate) -> Unit) {
    val context = LocalContext.current
    OutlinedButton({ DatePickerDialog(context, { _, y, m, d -> change(LocalDate.of(y, m + 1, d)) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }) {
        Text("$label: ${formatDate(date)}")
    }
}
@Composable fun TimeButton(label: String, time: LocalTime, change: (LocalTime) -> Unit) {
    val context = LocalContext.current
    OutlinedButton({ TimePickerDialog(context, { _, h, m -> change(LocalTime.of(h, m)) }, time.hour, time.minute, android.text.format.DateFormat.is24HourFormat(context)).show() }) {
        Text("$label: ${time.format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))}")
    }
}
@Composable fun EntryEditor(original: Entry, state: BoardState, vm: BoardViewModel, close: () -> Unit) {
    var title by remember { mutableStateOf(original.title) }
    var notes by remember { mutableStateOf(original.notes) }
    var category by remember { mutableStateOf(original.category) }
    var zone by remember { mutableStateOf(original.zone) }
    val initialZone = ZoneId.of(original.zone)
    var date by remember { mutableStateOf(Instant.ofEpochMilli(original.start).atZone(initialZone).toLocalDate()) }
    var endDate by remember { mutableStateOf(Instant.ofEpochMilli(original.end).atZone(initialZone).toLocalDate().let { if (original.allDay) it.minusDays(1) else it }) }
    var startTime by remember { mutableStateOf(Instant.ofEpochMilli(original.start).atZone(initialZone).toLocalTime()) }
    var endTime by remember { mutableStateOf(Instant.ofEpochMilli(original.end).atZone(initialZone).toLocalTime()) }
    var repeat by remember { mutableStateOf(original.repeat) }
    var every by remember { mutableStateOf(original.every.toString()) }
    var rotate by remember { mutableStateOf(original.rotate) }
    var allDay by remember { mutableStateOf(original.allDay) }
    var members by remember { mutableStateOf(original.memberIds.split(',').filter { it.isNotBlank() }.toSet()) }
    var child by remember { mutableStateOf(original.childVisible) }
    var pinned by remember { mutableStateOf(original.pinned) }
    var approval by remember { mutableStateOf(original.approvalRequired) }
    var stars by remember { mutableStateOf(original.stars.toString()) }
    var reminder by remember { mutableIntStateOf(original.reminderMinutes) }
    var expires by remember { mutableStateOf(if (original.expires > 0) Instant.ofEpochMilli(original.expires - 1).atZone(initialZone).toLocalDate() else null) }
    var expiryEdited by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Entry?>(null) }
    var overlaps by remember { mutableStateOf(emptyList<Entry>()) }
    var saving by remember { mutableStateOf(false) }
    val rewards by vm.rewards.collectAsState()
    val timed = original.kind in listOf(Kind.EVENT, Kind.CHORE)
    fun save(entry: Entry) { vm.action { saving = true; try { vm.repo.save(entry); close() } finally { saving = false } } }
    Dialog(close, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.94f).widthIn(max = 680.dp).fillMaxHeight(.94f), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(original.kind.label()), style = MaterialTheme.typography.headlineMedium)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(title, { title = it.take(200) }, label = { Text(stringResource(R.string.title)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(notes, { notes = it.take(4000) }, label = { Text(stringResource(R.string.notes)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(category, { category = it.take(80) }, label = { Text(stringResource(R.string.category)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (original.kind == Kind.NOTE || original.kind == Kind.ITEM) {
                        ChipsRow {
                            val options = if (original.kind == Kind.NOTE) listOf(R.string.category_general, R.string.category_reminder, R.string.category_request, R.string.category_late, R.string.category_forget, R.string.category_celebration, R.string.category_important)
                                else listOf(R.string.category_produce, R.string.category_dairy, R.string.category_bakery, R.string.category_frozen, R.string.category_household)
                            options.forEach { res -> val text = stringResource(res); SuggestionChip({ category = text }, { Text(text) }) }
                        }
                    }
                    if (timed) {
                        DateButton(stringResource(R.string.date), date) { date = it; if (endDate < it) endDate = it }
                        if (original.kind == Kind.EVENT) {
                            DateButton(stringResource(R.string.end_date), endDate) { endDate = it }
                            SettingToggle(stringResource(R.string.all_day), allDay) { allDay = it }
                        }
                        if (!allDay) {
                            TimeButton(stringResource(R.string.start_time), startTime) { startTime = it }
                            if (original.kind == Kind.EVENT) TimeButton(stringResource(R.string.end_time), endTime) { endTime = it }
                        }
                        OutlinedTextField(zone, { zone = it }, label = { Text(stringResource(R.string.zone)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.repeat), style = MaterialTheme.typography.titleSmall)
                        ChipsRow { Repeat.entries.forEach { r -> FilterChip(repeat == r, { repeat = r }, { Text(stringResource(r.label())) }) } }
                        if (repeat != Repeat.NONE) OutlinedTextField(every, { every = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.repeat_every)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Text(stringResource(R.string.reminder), style = MaterialTheme.typography.titleSmall)
                        ChipsRow { listOf(-1 to R.string.no_reminder, 0 to R.string.at_start, 15 to R.string.before_15, 60 to R.string.before_60).forEach { (value, res) ->
                            FilterChip(reminder == value, { reminder = value }, { Text(stringResource(res)) })
                        } }
                    }
                    if (original.kind != Kind.LIST) {
                        Text(stringResource(R.string.assigned), style = MaterialTheme.typography.titleSmall)
                        ChipsRow {
                            FilterChip(members.isEmpty(), { members = emptySet() }, { Text(stringResource(R.string.everyone)) })
                            state.members.forEach { m -> FilterChip(m.id in members, { members = if (m.id in members) members - m.id else members + m.id }, { Text(m.name) }) }
                        }
                    }
                    if (original.kind in listOf(Kind.NOTE, Kind.EVENT, Kind.CHORE)) SettingToggle(stringResource(R.string.child_visible), child) { child = it }
                    if (original.kind == Kind.LIST) SettingToggle(stringResource(R.string.kitchen_visible), child) { child = it }
                    if (original.kind == Kind.NOTE) {
                        SettingToggle(stringResource(R.string.pin_note), pinned) { pinned = it }
                        Text(stringResource(R.string.expires))
                        DateButton(stringResource(R.string.date), expires ?: date) { expires = it; expiryEdited = true }
                        if (expires != null) TextButton({ expires = null; expiryEdited = true }) { Text(stringResource(R.string.no_expiry)) }
                    }
                    if (original.kind == Kind.CHORE) {
                        SettingToggle(stringResource(R.string.approval), approval) { approval = it }
                        if (members.size > 1) SettingToggle(stringResource(R.string.rotate), rotate) { rotate = it }
                        if (rewards) OutlinedTextField(stars, { stars = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.stars)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    if (invalid) Text(stringResource(R.string.invalid_details), color = MaterialTheme.colorScheme.error)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(close) { Text(stringResource(R.string.cancel)) }
                    Button({
                        val built = runCatching {
                            val z = ZoneId.of(zone)
                            val start = date.atTime(if (allDay) LocalTime.MIDNIGHT else startTime).atZone(z).toInstant().toEpochMilli()
                            val end = if (original.kind == Kind.CHORE) start + 3600000 else
                                (if (allDay) endDate.plusDays(1).atStartOfDay(z) else endDate.atTime(endTime).atZone(z)).toInstant().toEpochMilli()
                            require(!timed || end > start)
                            require(!timed || end - start <= 366L * 86_400_000)
                            require(stars.toInt() in 0..10)
                            require(every.toInt() in 1..365)
                            original.copy(title = title, notes = notes, category = category, memberIds = members.joinToString(","), zone = zone,
                                start = if (timed) start else original.start, end = if (timed) end else original.end, allDay = allDay, repeat = repeat, every = every.toInt(), rotate = rotate,
                                childVisible = child, pinned = pinned, approvalRequired = approval, stars = stars.toInt(), reminderMinutes = reminder,
                                expires = if (!expiryEdited) original.expires else expires?.plusDays(1)?.atStartOfDay(z)?.toInstant()?.toEpochMilli() ?: 0,
                                author = original.author.ifBlank { state.members.firstOrNull { it.id == vm.session.value?.memberId }?.name.orEmpty() })
                        }
                        if (built.isFailure) invalid = true else {
                            val e = built.getOrThrow()
                            overlaps = if (e.kind == Kind.EVENT) state.entries.filter { other ->
                                if (other.kind != Kind.EVENT || other.id == e.id) false else {
                                    val shared = e.memberIds.isBlank() || other.memberIds.isBlank() || e.memberIds.split(',').any { it in other.memberIds.split(',') }
                                    val otherZone = ZoneId.of(other.zone)
                                    val span = java.time.temporal.ChronoUnit.DAYS.between(Instant.ofEpochMilli(other.start).atZone(otherZone).toLocalDate(), Instant.ofEpochMilli(other.end).atZone(otherZone).toLocalDate()).coerceIn(0, 366)
                                    val first = Instant.ofEpochMilli(e.start).atZone(otherZone).toLocalDate().minusDays(span)
                                    val last = Instant.ofEpochMilli(e.end).atZone(otherZone).toLocalDate()
                                    shared && (0..java.time.temporal.ChronoUnit.DAYS.between(first, last)).any { offset ->
                                        val d = first.plusDays(offset)
                                        val os = Schedule.occurrenceStart(other.start, other.zone, d)
                                        Schedule.occurs(other.start, other.zone, other.repeat, d, other.excluded.split(',').toSet(), other.every) && Schedule.overlaps(e.start, e.end, os, Schedule.occurrenceEnd(other.start, other.end, other.zone, d))
                                    }
                                }
                            } else emptyList()
                            if (overlaps.isNotEmpty()) pending = e else save(e)
                        }
                    }, enabled = title.isNotBlank() && !saving) { Text(stringResource(if (saving) R.string.saving else R.string.save)) }
                }
            }
        }
    }
    pending?.let { entry -> AlertDialog(onDismissRequest = { pending = null }, title = { Text(stringResource(R.string.overlap_title)) },
        text = { Text(stringResource(R.string.overlap_body, overlaps.joinToString { it.title })) },
        confirmButton = { TextButton({ pending = null; save(entry) }) { Text(stringResource(R.string.save_anyway)) } },
        dismissButton = { TextButton({ pending = null }) { Text(stringResource(R.string.back)) } }) }
}
