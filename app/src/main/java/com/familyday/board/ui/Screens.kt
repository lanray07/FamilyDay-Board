package com.familyday.board.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.familyday.board.R
import com.familyday.board.data.*
import com.familyday.board.domain.*
import java.time.*
import java.time.format.*
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable fun ScreenBody(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp), content = content)
}
@Composable fun Dashboard(state: BoardState, vm: BoardViewModel, date: LocalDate, adult: Boolean, kitchen: Boolean, edit: (Entry) -> Unit, add: (Kind) -> Unit) {
    val session by vm.session.collectAsState()
    val rewards by vm.rewards.collectAsState()
    val all = if (kitchen) state.entries.filter { it.childVisible } else state.entries
    val events = all.filter { it.kind == Kind.EVENT && it.on(date) }.sortedBy { Schedule.occurrenceStart(it.start, it.zone, date) }
    val chores = all.filter { it.kind == Kind.CHORE && it.on(date) && (adult || it.assignedOn(session?.memberId.orEmpty(), date)) }
    val notes = all.filter { it.kind == Kind.NOTE && !it.done && (it.expires == 0L || it.expires > System.currentTimeMillis()) }.sortedByDescending { it.pinned }
    val items = all.filter { it.kind == Kind.ITEM && !it.done }
    ScreenBody {
        PageTitle(if (!adult) stringResource(R.string.child_space, state.members.firstOrNull { it.id == session?.memberId }?.name.orEmpty())
            else stringResource(R.string.greeting, state.household?.name.orEmpty()),
            date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())))
        Text(stringResource(if (adult) R.string.today_intro else R.string.child_intro), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        if (adult && !kitchen) ChipsRow {
            listOf(Kind.EVENT to R.string.add_event, Kind.CHORE to R.string.add_chore, Kind.NOTE to R.string.add_message, Kind.ITEM to R.string.add_item).forEach { (k, r) -> AddButton(r) { add(k) } }
        }
        if (adult && state.household?.priority?.isNotBlank() == true && !kitchen) Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.priority), style = MaterialTheme.typography.labelLarge)
                Text(state.household.priority, style = MaterialTheme.typography.headlineMedium)
            }
        }
        BoxWithConstraints {
            val wide = maxWidth >= 680.dp
            val schedule: @Composable () -> Unit = {
                BoardCard(stringResource(R.string.schedule), Icons.Default.WbSunny, Modifier.fillMaxWidth()) {
                    if (events.isEmpty()) EmptyState(R.string.empty_events)
                    events.forEach { e -> EntryRow(e, state.members, date, adult && !kitchen, kitchen, onEdit = { edit(e) }) }
                }
            }
            val home: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    BoardCard(stringResource(R.string.around_home), Icons.Default.CheckCircle, Modifier.fillMaxWidth()) {
                        if (chores.isEmpty()) EmptyState(R.string.empty_chores) else {
                            val done = chores.count { it.completed(date) }
                            Text(stringResource(R.string.progress, done, chores.size), color = MaterialTheme.colorScheme.primary)
                            LinearProgressIndicator(progress = { done.toFloat() / chores.size }, Modifier.fillMaxWidth())
                            chores.forEach { e -> EntryRow(e, state.members, date, adult && !kitchen, kitchen,
                                onToggle = { if (!kitchen) vm.action { vm.repo.complete(e.id, date) } }, onEdit = { edit(e) },
                                onApprove = { vm.action { vm.repo.approve(e.id, date) } }) }
                        }
                    }
                    if (adult) BoardCard(stringResource(R.string.shopping_summary), Icons.Default.ShoppingBasket, Modifier.fillMaxWidth()) {
                        if (items.isEmpty()) EmptyState(R.string.empty_items)
                        items.take(5).forEach { e -> EntryRow(e, state.members, date, !kitchen, kitchen,
                            onToggle = { if (!kitchen) vm.action { vm.repo.complete(e.id, date) } }, onEdit = { edit(e) }) }
                    }
                }
            }
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    schedule()
                    BoardCard(stringResource(R.string.on_board), Icons.Default.StickyNote2, Modifier.fillMaxWidth()) {
                        if (notes.isEmpty()) EmptyState(R.string.empty_notes)
                        notes.take(5).forEach { e -> EntryRow(e, state.members, date, adult && !kitchen, kitchen, onEdit = { edit(e) }, onToggle = { vm.action { vm.repo.complete(e.id, date) } }) }
                    }
                }
                Column(Modifier.weight(1f)) { home() }
            } else Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                schedule(); home()
                BoardCard(stringResource(R.string.on_board), Icons.Default.StickyNote2, Modifier.fillMaxWidth()) {
                    if (notes.isEmpty()) EmptyState(R.string.empty_notes)
                    notes.take(5).forEach { e -> EntryRow(e, state.members, date, adult && !kitchen, kitchen, onEdit = { edit(e) }, onToggle = { vm.action { vm.repo.complete(e.id, date) } }) }
                }
            }
        }
        if (rewards && !adult) {
            val earned = all.filter { it.kind == Kind.CHORE && it.assigned(session?.memberId.orEmpty()) }.sumOf { e ->
                e.completedDates.split(',').filter { it.isNotBlank() && e.assignedOn(session?.memberId.orEmpty(), LocalDate.parse(it)) && (!e.approvalRequired || it in e.approvedDates.split(',')) }.size * e.stars
            }
            Text(pluralStringResource(R.plurals.reward_total, earned, earned), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable fun CalendarScreen(state: BoardState, adult: Boolean, date: LocalDate, setDate: (LocalDate) -> Unit, edit: (Entry) -> Unit, add: () -> Unit, skip: (Entry, LocalDate) -> Unit) {
    var view by rememberSaveable { mutableIntStateOf(1) }
    var member by rememberSaveable { mutableStateOf("") }
    val events = state.entries.filter { it.kind == Kind.EVENT && (member.isBlank() || it.memberIds.isBlank() || it.assigned(member)) }
    val weekStart = DayOfWeek.of(state.household?.weekStart ?: 1)
    val first = when (view) {
        1 -> date.with(TemporalAdjusters.previousOrSame(weekStart))
        2 -> date.withDayOfMonth(1).with(TemporalAdjusters.previousOrSame(weekStart))
        else -> date
    }
    ScreenBody {
        PageTitle(stringResource(R.string.calendar)) { if (adult) AddButton(R.string.add_event, add) }
        ChipsRow { listOf(R.string.day, R.string.week, R.string.month, R.string.agenda).forEachIndexed { index, res -> FilterChip(view == index, { view = index }, { Text(stringResource(res)) }) } }
        if (adult) ChipsRow {
            FilterChip(member.isBlank(), { member = "" }, { Text(stringResource(R.string.everyone)) })
            state.members.forEach { m -> FilterChip(member == m.id, { member = m.id }, { Text(m.name) }) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ setDate(when (view) { 1 -> date.minusWeeks(1); 2 -> date.minusMonths(1); else -> date.minusDays(1) }) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous)) }
            Text(date.format(DateTimeFormatter.ofPattern(if (view == 2) "MMMM yyyy" else "d MMM yyyy", Locale.getDefault())), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton({ setDate(LocalDate.now(ZoneId.of(state.household?.zone ?: ZoneId.systemDefault().id))) }) { Text(stringResource(R.string.today)) }
            IconButton({ setDate(when (view) { 1 -> date.plusWeeks(1); 2 -> date.plusMonths(1); else -> date.plusDays(1) }) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.following)) }
        }
        if (view == 2) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row { (0..6).forEach { d -> Text(first.plusDays(d.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium) } }
                (0..5).forEach { week -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0..6).forEach { d ->
                        val day = first.plusDays((week * 7 + d).toLong())
                        val count = events.count { it.on(day) }
                        Surface(onClick = { setDate(day) }, modifier = Modifier.weight(1f).heightIn(min = 64.dp), shape = MaterialTheme.shapes.small,
                            color = if (day == date) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                            Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(day.dayOfMonth.toString(), fontWeight = if (day == date) FontWeight.Bold else FontWeight.Normal)
                                if (count > 0) Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { repeat(minOf(count, 3)) { Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) } }
                            }
                        }
                    }
                } }
            }
        }
        val days = when (view) { 1 -> (0..6).map { first.plusDays(it.toLong()) }; 3 -> (0..29).map { date.plusDays(it.toLong()) }; else -> listOf(date) }
        days.forEach { day ->
            val plans = events.filter { it.on(day) }.sortedBy { Schedule.occurrenceStart(it.start, it.zone, day) }
            if (view != 3 || plans.isNotEmpty()) BoardCard(day.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault())), Icons.Default.CalendarToday, Modifier.fillMaxWidth()) {
                if (plans.isEmpty()) EmptyState(R.string.empty_events)
                plans.forEach { e ->
                    EntryRow(e, state.members, day, adult, onEdit = { edit(e) })
                    if (adult && e.repeat != Repeat.NONE) TextButton({ skip(e, day) }) { Text(stringResource(R.string.skip_date)) }
                }
            }
        }
        if (view == 3 && events.none { e -> days.any { e.on(it) } }) EmptyState(R.string.empty_events)
    }
}

@Composable fun ChoresScreen(state: BoardState, vm: BoardViewModel, adult: Boolean, date: LocalDate, setDate: (LocalDate) -> Unit, edit: (Entry) -> Unit, add: () -> Unit) {
    val chores = state.entries.filter { it.kind == Kind.CHORE }
    val session by vm.session.collectAsState()
    fun dueOn(day: LocalDate) = chores.filter { it.on(day) && (adult || it.assignedOn(session?.memberId.orEmpty(), day)) }
    ScreenBody {
        PageTitle(stringResource(R.string.chores)) { if (adult) AddButton(R.string.add_chore, add) }
        DateButton(stringResource(R.string.date), date, setDate)
        ChipsRow {
            val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.of(state.household?.weekStart ?: 1)))
            (0..6).forEach { index ->
                val day = start.plusDays(index.toLong()); val due = dueOn(day)
                FilterChip(day == date, { setDate(day) }, { Text("${day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${due.count { it.completed(day) }}/${due.size}") })
            }
        }
        BoardCard(stringResource(R.string.around_home), Icons.Default.CheckCircle, Modifier.fillMaxWidth()) {
            val due = dueOn(date)
            if (due.isEmpty()) EmptyState(R.string.empty_chores)
            due.forEach { e -> EntryRow(e, state.members, date, adult,
                onToggle = { vm.action { vm.repo.complete(e.id, date) } }, onEdit = { edit(e) }, onApprove = { vm.action { vm.repo.approve(e.id, date) } }) }
        }
    }
}

@Composable fun MessagesScreen(state: BoardState, vm: BoardViewModel, adult: Boolean, edit: (Entry) -> Unit, add: () -> Unit) {
    var category by remember { mutableStateOf("") }; var archived by remember { mutableStateOf(false) }
    val all = state.entries.filter { it.kind == Kind.NOTE }
    val now = System.currentTimeMillis()
    val notes = all.filter { (category.isBlank() || it.category == category) && ((it.done || (it.expires > 0 && it.expires <= now)) == archived) }.sortedWith(compareByDescending<Entry> { it.pinned }.thenByDescending { it.updated })
    ScreenBody {
        PageTitle(stringResource(R.string.messages)) { if (adult) AddButton(R.string.add_message, add) }
        ChipsRow {
            FilterChip(!archived, { archived = false }, { Text(stringResource(R.string.active)) })
            FilterChip(archived, { archived = true }, { Text(stringResource(R.string.archived)) })
        }
        ChipsRow {
            FilterChip(category.isBlank(), { category = "" }, { Text(stringResource(R.string.all_categories)) })
            all.map { it.category }.filter { it.isNotBlank() }.distinct().forEach { c -> FilterChip(category == c, { category = c }, { Text(c) }) }
        }
        if (notes.isEmpty()) EmptyState(R.string.empty_notes)
        notes.forEach { e -> BoardCard(e.category.ifBlank { stringResource(R.string.note) }, Icons.Default.StickyNote2, Modifier.fillMaxWidth()) {
            EntryRow(e, state.members, LocalDate.now(), adult, onToggle = { vm.action { vm.repo.complete(e.id, LocalDate.now()) } }, onEdit = { edit(e) })
            Text(stringResource(R.string.created_by, e.author, Instant.ofEpochMilli(e.updated).atZone(ZoneId.of(e.zone)).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))), style = MaterialTheme.typography.bodySmall)
        } }
    }
}
