package com.familyday.board.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.settings by preferencesDataStore("settings")
class Preferences(private val context: Context) {
    val dark = context.settings.data.map { it[booleanPreferencesKey("dark")] ?: false }
    val rewards = context.settings.data.map { it[booleanPreferencesKey("rewards")] ?: false }
    val quiet = context.settings.data.map { it[booleanPreferencesKey("quiet")] ?: true }
    suspend fun set(key: String, value: Boolean) { context.settings.edit { it[booleanPreferencesKey(key)] = value } }
}
