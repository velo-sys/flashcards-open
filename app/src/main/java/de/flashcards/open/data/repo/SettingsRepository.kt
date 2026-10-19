package de.flashcards.open.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.flashcards.open.domain.study.MixMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class Settings(
    val newPerDay: Int = 20,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val cardFontScale: Float = 1f,
    val mixMode: MixMode = MixMode.MIXED,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val NEW_PER_DAY = intPreferencesKey("newPerDay")
        val THEME = stringPreferencesKey("themeMode")
        val DYNAMIC = booleanPreferencesKey("dynamicColor")
        val FONT = floatPreferencesKey("cardFontScale")
        val MIX = stringPreferencesKey("mixMode")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            newPerDay = p[Keys.NEW_PER_DAY] ?: d.newPerDay,
            themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            dynamicColor = p[Keys.DYNAMIC] ?: d.dynamicColor,
            cardFontScale = p[Keys.FONT] ?: d.cardFontScale,
            mixMode = p[Keys.MIX]?.let { runCatching { MixMode.valueOf(it) }.getOrNull() } ?: d.mixMode,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setNewPerDay(v: Int) = context.dataStore.edit { it[Keys.NEW_PER_DAY] = v.coerceIn(0, 9999) }
    suspend fun setThemeMode(v: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = v.name }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[Keys.DYNAMIC] = v }
    suspend fun setCardFontScale(v: Float) = context.dataStore.edit { it[Keys.FONT] = v.coerceIn(0.7f, 2f) }
    suspend fun setMixMode(v: MixMode) = context.dataStore.edit { it[Keys.MIX] = v.name }

    suspend fun replaceAll(s: Settings) = context.dataStore.edit {
        it[Keys.NEW_PER_DAY] = s.newPerDay
        it[Keys.THEME] = s.themeMode.name
        it[Keys.DYNAMIC] = s.dynamicColor
        it[Keys.FONT] = s.cardFontScale
        it[Keys.MIX] = s.mixMode.name
    }
}
