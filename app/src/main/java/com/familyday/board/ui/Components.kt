package com.familyday.board.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.familyday.board.R
import com.familyday.board.data.*
import com.familyday.board.domain.*
import java.time.*
import java.time.format.*

@StringRes fun Kind.label() = when (this) { Kind.EVENT -> R.string.event; Kind.CHORE -> R.string.chore; Kind.NOTE -> R.string.note; Kind.LIST -> R.string.list; Kind.ITEM -> R.string.item }
@StringRes fun Repeat.label() = when (this) { Repeat.NONE -> R.string.repeat_none; Repeat.DAILY -> R.string.repeat_daily; Repeat.WEEKLY -> R.string.repeat_weekly; Repeat.MONTHLY -> R.string.repeat_monthly; Repeat.YEARLY -> R.string.repeat_yearly }
@StringRes fun Role.label() = when (this) { Role.OWNER -> R.string.role_owner; Role.PARENT -> R.string.role_parent; Role.CARER -> R.string.role_carer; Role.ADULT -> R.string.role_adult; Role.CHILD -> R.string.role_child }
fun formatDate(date: LocalDate) = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
fun formatTime(millis: Long, zone: String) = Instant.ofEpochMilli(millis).atZone(ZoneId.of(zone)).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

@Composable fun PageTitle(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            subtitle?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        action?.invoke()
    }
}
@Composable fun BoardCard(title: String, icon: ImageVector, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            content()
        }
    }
}
@Composable fun EmptyState(@StringRes text: Int) {
    Text(stringResource(text), Modifier.fillMaxWidth().padding(vertical = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable fun MemberAvatar(member: Member, size: Dp = 38.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(MemberColours[member.color.mod(MemberColours.size)]), contentAlignment = Alignment.Center) {
        Text(member.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * .4f).sp)
    }
}
@Composable fun ChipsRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}
@Composable fun SettingToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().minimumInteractiveComponentSize().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked, onCheckedChange = onChange)
    }
}
@Composable fun BackButton(onClick: () -> Unit) { IconButton(onClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }
@Composable fun AddButton(@StringRes text: Int, onClick: () -> Unit) {
    FilledTonalButton(onClick) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(text)) }
}

@Composable fun EntryRow(e: Entry, members: List<Member>, date: LocalDate, adult: Boolean, large: Boolean = false,
    onToggle: () -> Unit = {}, onEdit: () -> Unit = {}, onApprove: () -> Unit = {}) {
    val assigned = members.filter { e.assignedOn(it.id, date) }
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
        .then(if (adult) Modifier.clickable(onClick = onEdit) else Modifier).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (e.kind in listOf(Kind.CHORE, Kind.ITEM) || (e.kind == Kind.NOTE && adult)) {
            Checkbox(e.completed(date), { onToggle() }, Modifier.size(if (large) 56.dp else 48.dp))
        } else {
            Box(Modifier.width(4.dp).height(if (large) 56.dp else 40.dp).clip(CircleShape)
                .background(assigned.firstOrNull()?.let { MemberColours[it.color.mod(6)] } ?: MaterialTheme.colorScheme.primary))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(e.title, style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium, color = if (e.completed(date)) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            if (e.kind == Kind.EVENT) Text(if (e.allDay) stringResource(R.string.all_day) else formatTime(Schedule.occurrenceStart(e.start, e.zone, date), e.zone),
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            if (e.notes.isNotBlank()) Text(e.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (assigned.isNotEmpty()) Text(assigned.joinToString { it.name }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (e.source != "local") Text(stringResource(R.string.imported_event), style = MaterialTheme.typography.bodySmall)
            if (e.kind == Kind.CHORE && e.approvalRequired && e.completed(date)) {
                Text(stringResource(if (e.approved(date)) R.string.approved else R.string.awaiting_approval), style = MaterialTheme.typography.labelMedium)
                if (adult && !e.approved(date)) TextButton(onApprove) { Text(stringResource(R.string.approve)) }
            }
        }
        if (e.pinned) Icon(Icons.Default.PushPin, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
        assigned.firstOrNull()?.let { MemberAvatar(it, if (large) 44.dp else 32.dp) }
    }
}
