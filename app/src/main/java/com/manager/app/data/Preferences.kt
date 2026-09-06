package com.manager.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "manager_prefs")

enum class ThemeMode(val label: String) { System("Follow system"), Light("Light"), Dark("Dark") }

data class ManagerPreferences(
    /**
     * False until DataStore has answered. Without this the app would paint onboarding for a
     * frame on every launch, because "not onboarded" and "not yet known" would look identical.
     */
    val loaded: Boolean = false,
    val onboarded: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.Light,
    val usageWindow: UsageWindow = UsageWindow.Week,
    val sortKey: SortKey = SortKey.Name,
    val sortDirection: SortDirection = SortDirection.Ascending,
    val hapticsEnabled: Boolean = true,
)

class PreferencesStore(private val context: Context) {

    val preferences: Flow<ManagerPreferences> = context.dataStore.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs ->
            ManagerPreferences(
                loaded = true,
                onboarded = prefs[KeyOnboarded] ?: false,
                themeMode = prefs[KeyTheme]?.toEnum(ThemeMode.entries) ?: ThemeMode.Light,
                usageWindow = prefs[KeyUsageWindow]?.toEnum(UsageWindow.entries) ?: UsageWindow.Week,
                sortKey = prefs[KeySortKey]?.toEnum(SortKey.entries) ?: SortKey.Name,
                sortDirection = prefs[KeySortDirection]?.toEnum(SortDirection.entries) ?: SortDirection.Ascending,
                hapticsEnabled = prefs[KeyHaptics] ?: true,
            )
        }

    suspend fun setOnboarded(value: Boolean) = edit { it[KeyOnboarded] = value }
    suspend fun setThemeMode(value: ThemeMode) = edit { it[KeyTheme] = value.name }
    suspend fun setUsageWindow(value: UsageWindow) = edit { it[KeyUsageWindow] = value.name }
    suspend fun setHaptics(value: Boolean) = edit { it[KeyHaptics] = value }
    suspend fun setSort(key: SortKey, direction: SortDirection) = edit {
        it[KeySortKey] = key.name
        it[KeySortDirection] = direction.name
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        runCatching { context.dataStore.edit(block) }
    }

    private companion object {
        val KeyOnboarded = booleanPreferencesKey("onboarded")
        val KeyTheme = stringPreferencesKey("theme_mode")
        val KeyUsageWindow = stringPreferencesKey("usage_window")
        val KeySortKey = stringPreferencesKey("sort_key")
        val KeySortDirection = stringPreferencesKey("sort_direction")
        val KeyHaptics = booleanPreferencesKey("haptics")
    }
}

private fun <T : Enum<T>> String.toEnum(values: List<T>): T? = values.firstOrNull { it.name == this }
