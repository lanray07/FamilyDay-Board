package com.familyday.board.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyday.board.data.*
import com.familyday.board.domain.*
import com.familyday.board.security.ParentGate
import com.familyday.board.sync.SyncService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class BoardState(val loaded: Boolean = false, val household: Household? = null, val members: List<Member> = emptyList(), val entries: List<Entry> = emptyList())
class BoardViewModel(val repo: BoardRepository, val gate: ParentGate, val preferences: Preferences, val backups: BackupService, val sync: SyncService) : ViewModel() {
    val state = combine(repo.household, repo.members, repo.entries) { h, m, e -> BoardState(true, h, m, e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BoardState())
    val session = gate.session
    val dark = preferences.dark.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val rewards = preferences.rewards.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val quiet = preferences.quiet.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    fun action(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { error.value = e.message ?: "error" }
    }
    fun setup(name: String, owner: String, pin: String, zone: String, weekStart: Int) = action {
        busy.value = true
        try {
            if (!gate.configured) gate.setup(pin)
            repo.createHousehold(name, owner, zone, weekStart)
            val member = repo.members.first().first()
            check(gate.unlock(pin, member.id))
        } finally { busy.value = false }
    }
}
