package com.chessforge.data.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class EnginePreference(val label: String) {
    AUTO("Automatique (le plus fort disponible)"),
    FORGE("Moteur integre"),
    STOCKFISH("Stockfish natif"),
}

enum class ThemeMode(val label: String) {
    SYSTEM("Comme le systeme"),
    DARK("Sombre"),
    LIGHT("Clair"),
}

data class SettingsData(
    val username: String = "",
    val syncMonths: Int = 3,
    val includeUnrated: Boolean = false,
    val analysisPresetIndex: Int = 1,
    val autoAnalyzeAfterSync: Boolean = true,
    val maxPuzzlesPerGame: Int = 4,
    val dailyPuzzleGoal: Int = 10,
    val boardTheme: String = "forest",
    val showCoordinates: Boolean = true,
    val showBestMoveArrow: Boolean = true,
    val enginePreference: EnginePreference = EnginePreference.AUTO,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val lastSyncAt: Long = 0L,
    val hapticFeedback: Boolean = true,
) {
    val isConfigured: Boolean get() = username.isNotBlank()
}

/** Preferences locales. Simple et synchrone : le volume est minuscule. */
class Settings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("chessforge.settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<SettingsData> = _state.asStateFlow()

    val current: SettingsData get() = _state.value

    private fun read() = SettingsData(
        username = prefs.getString(KEY_USERNAME, "") ?: "",
        syncMonths = prefs.getInt(KEY_SYNC_MONTHS, 3),
        includeUnrated = prefs.getBoolean(KEY_INCLUDE_UNRATED, false),
        analysisPresetIndex = prefs.getInt(KEY_PRESET, 1),
        autoAnalyzeAfterSync = prefs.getBoolean(KEY_AUTO_ANALYZE, true),
        maxPuzzlesPerGame = prefs.getInt(KEY_MAX_PUZZLES, 4),
        dailyPuzzleGoal = prefs.getInt(KEY_DAILY_GOAL, 10),
        boardTheme = prefs.getString(KEY_BOARD_THEME, "forest") ?: "forest",
        showCoordinates = prefs.getBoolean(KEY_COORDS, true),
        showBestMoveArrow = prefs.getBoolean(KEY_ARROW, true),
        enginePreference = runCatching {
            EnginePreference.valueOf(prefs.getString(KEY_ENGINE, EnginePreference.AUTO.name)!!)
        }.getOrDefault(EnginePreference.AUTO),
        themeMode = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.DARK.name)!!)
        }.getOrDefault(ThemeMode.DARK),
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, false),
        lastSyncAt = prefs.getLong(KEY_LAST_SYNC, 0L),
        hapticFeedback = prefs.getBoolean(KEY_HAPTIC, true),
    )

    fun update(block: (SettingsData) -> SettingsData) {
        val updated = block(_state.value)
        prefs.edit().apply {
            putString(KEY_USERNAME, updated.username)
            putInt(KEY_SYNC_MONTHS, updated.syncMonths)
            putBoolean(KEY_INCLUDE_UNRATED, updated.includeUnrated)
            putInt(KEY_PRESET, updated.analysisPresetIndex)
            putBoolean(KEY_AUTO_ANALYZE, updated.autoAnalyzeAfterSync)
            putInt(KEY_MAX_PUZZLES, updated.maxPuzzlesPerGame)
            putInt(KEY_DAILY_GOAL, updated.dailyPuzzleGoal)
            putString(KEY_BOARD_THEME, updated.boardTheme)
            putBoolean(KEY_COORDS, updated.showCoordinates)
            putBoolean(KEY_ARROW, updated.showBestMoveArrow)
            putString(KEY_ENGINE, updated.enginePreference.name)
            putString(KEY_THEME, updated.themeMode.name)
            putBoolean(KEY_DYNAMIC, updated.dynamicColor)
            putLong(KEY_LAST_SYNC, updated.lastSyncAt)
            putBoolean(KEY_HAPTIC, updated.hapticFeedback)
        }.apply()
        _state.value = updated
    }

    private companion object {
        const val KEY_USERNAME = "username"
        const val KEY_SYNC_MONTHS = "syncMonths"
        const val KEY_INCLUDE_UNRATED = "includeUnrated"
        const val KEY_PRESET = "analysisPreset"
        const val KEY_AUTO_ANALYZE = "autoAnalyze"
        const val KEY_MAX_PUZZLES = "maxPuzzles"
        const val KEY_DAILY_GOAL = "dailyGoal"
        const val KEY_BOARD_THEME = "boardTheme"
        const val KEY_COORDS = "showCoordinates"
        const val KEY_ARROW = "showArrow"
        const val KEY_ENGINE = "enginePreference"
        const val KEY_THEME = "themeMode"
        const val KEY_DYNAMIC = "dynamicColor"
        const val KEY_LAST_SYNC = "lastSyncAt"
        const val KEY_HAPTIC = "haptic"
    }
}
