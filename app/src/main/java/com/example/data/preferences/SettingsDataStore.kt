package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.noqtatiDataStore by preferencesDataStore(name = "noqtati_settings")

enum class ThemeMode(val labelAr: String) {
    SYSTEM("تلقائي حسب النظام"),
    LIGHT("الوضع الفاتح"),
    DARK("الوضع الليلي")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val teacherName: String = "",
    val schoolName: String = "",
    val wilaya: String = "",
    val defaultAcademicYear: String = "2025/2026",
    val aiOcrEnabled: Boolean = true
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TEACHER_NAME = stringPreferencesKey("teacher_name")
        val SCHOOL_NAME = stringPreferencesKey("school_name")
        val WILAYA = stringPreferencesKey("wilaya")
        val ACADEMIC_YEAR = stringPreferencesKey("default_academic_year")
        val AI_OCR_ENABLED = booleanPreferencesKey("ai_ocr_enabled")
    }

    val settingsFlow: Flow<AppSettings> = context.noqtatiDataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = ThemeMode.entries.firstOrNull { it.name == themeStr } ?: ThemeMode.SYSTEM
        AppSettings(
            themeMode = themeMode,
            teacherName = prefs[Keys.TEACHER_NAME] ?: "",
            schoolName = prefs[Keys.SCHOOL_NAME] ?: "",
            wilaya = prefs[Keys.WILAYA] ?: "",
            defaultAcademicYear = prefs[Keys.ACADEMIC_YEAR] ?: "2025/2026",
            aiOcrEnabled = prefs[Keys.AI_OCR_ENABLED] ?: true
        )
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.noqtatiDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updateProfile(
        teacherName: String,
        schoolName: String,
        wilaya: String,
        academicYear: String
    ) {
        context.noqtatiDataStore.edit { prefs ->
            prefs[Keys.TEACHER_NAME] = teacherName.trim()
            prefs[Keys.SCHOOL_NAME] = schoolName.trim()
            prefs[Keys.WILAYA] = wilaya.trim()
            if (academicYear.isNotBlank()) {
                prefs[Keys.ACADEMIC_YEAR] = academicYear.trim()
            }
        }
    }

    suspend fun updateAiOcrEnabled(enabled: Boolean) {
        context.noqtatiDataStore.edit { it[Keys.AI_OCR_ENABLED] = enabled }
    }

    suspend fun restoreSettings(settings: AppSettings) {
        context.noqtatiDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = settings.themeMode.name
            prefs[Keys.TEACHER_NAME] = settings.teacherName
            prefs[Keys.SCHOOL_NAME] = settings.schoolName
            prefs[Keys.WILAYA] = settings.wilaya
            prefs[Keys.ACADEMIC_YEAR] = settings.defaultAcademicYear
            prefs[Keys.AI_OCR_ENABLED] = settings.aiOcrEnabled
        }
    }
}
