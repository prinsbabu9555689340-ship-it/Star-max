package com.example.data.firebase

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.starMaxDataStore by preferencesDataStore(name = "starmax_prefs")

class ThemePreferences(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val usageDateKey = stringPreferencesKey("usage_date")
    private val usageSecondsKey = longPreferencesKey("usage_seconds")

    val themeMode: Flow<ThemeMode> = context.starMaxDataStore.data.map { prefs ->
        ThemeMode.fromWire(prefs[themeKey])
    }

    val dailyUsageSeconds: Flow<Long> = context.starMaxDataStore.data.map { prefs ->
        val today = todayDateKey()
        if (prefs[usageDateKey] == today) prefs[usageSecondsKey] ?: 0L else 0L
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.starMaxDataStore.edit { prefs ->
            prefs[themeKey] = mode.wireValue
        }
    }

    suspend fun setDailyUsage(date: String, seconds: Long) {
        context.starMaxDataStore.edit { prefs ->
            prefs[usageDateKey] = date
            prefs[usageSecondsKey] = seconds.coerceAtLeast(0L)
        }
    }

    companion object {
        fun todayDateKey(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}

