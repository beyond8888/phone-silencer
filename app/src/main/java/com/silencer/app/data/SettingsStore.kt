package com.silencer.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.silencer.app.logic.Settings
import com.silencer.app.logic.WorkWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_settings")

/** 用户设置持久化（DataStore） */
class SettingsStore(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val WINDOWS = stringPreferencesKey("windows")          // "09:00-12:00;14:00-18:00"
        val WEEKDAYS = stringPreferencesKey("weekdays")        // "1,2,3,4,5"
        val RESPECT_HOLIDAYS = booleanPreferencesKey("respect_holidays")
        val SILENCE_MODE = stringPreferencesKey("silence_mode")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            enabled = prefs[Keys.ENABLED] ?: false,
            windows = prefs[Keys.WINDOWS]?.let(::parseWindows) ?: Settings.defaultWindows(),
            weekdays = prefs[Keys.WEEKDAYS]?.let(::parseWeekdays) ?: Settings.defaultWeekdays(),
            respectHolidays = prefs[Keys.RESPECT_HOLIDAYS] ?: true,
            silenceMode = prefs[Keys.SILENCE_MODE] ?: "dnd"
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun update(transform: (Settings) -> Settings) {
        val next = transform(current())
        context.dataStore.edit { prefs ->
            prefs[Keys.ENABLED] = next.enabled
            prefs[Keys.WINDOWS] = next.windows.joinToString(";") { it.toString() }
            prefs[Keys.WEEKDAYS] = next.weekdays.sorted().joinToString(",")
            prefs[Keys.RESPECT_HOLIDAYS] = next.respectHolidays
            prefs[Keys.SILENCE_MODE] = next.silenceMode
        }
    }

    private fun parseWindows(s: String): List<WorkWindow> =
        s.split(";").mapNotNull { WorkWindow.parse(it.trim()) }

    private fun parseWeekdays(s: String): Set<Int> =
        s.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()
}
