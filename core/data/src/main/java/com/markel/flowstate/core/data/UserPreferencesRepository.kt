package com.markel.flowstate.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val lastTab: Flow<MainTab> = context.dataStore.data.map { preferences ->
        MainTab.fromName(preferences[stringPreferencesKey("last_tab_route")])
    }
    private val CALENDAR_VIEW_MODE = stringPreferencesKey("calendar_view_mode")

    val calendarViewMode: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[CALENDAR_VIEW_MODE]
    }

    suspend fun saveLastTab(tab: MainTab) {
        context.dataStore.edit { preferences ->
            preferences[stringPreferencesKey("last_tab_route")] = tab.name
        }
    }

    suspend fun saveCalendarViewMode(mode: String) {
        context.dataStore.edit { it[CALENDAR_VIEW_MODE] = mode }
    }

    // ── Bottom navigation configuration ───────────────────────────────────

    /** Ordered list of tab names (e.g. ["TASKS","CALENDAR","HABITS","MOOD","SETTINGS"]). */
    private val BOTTOM_NAV_ORDER = stringPreferencesKey("bottom_nav_order")

    /** Set of hidden (removed) tab names (e.g. ["MOOD"]). */
    private val BOTTOM_NAV_HIDDEN = stringSetPreferencesKey("bottom_nav_hidden")

    /** Emits the current ordered list of tabs. Falls back to [MainTab.DEFAULT_ORDER]. */
    val bottomNavOrder: Flow<List<MainTab>> = context.dataStore.data.map { preferences ->
        val raw = preferences[BOTTOM_NAV_ORDER]
        if (raw.isNullOrBlank()) {
            MainTab.DEFAULT_ORDER
        } else {
            raw.split(",")
                .mapNotNull { MainTab.fromNameOrNull(it.trim()) }
                .ifEmpty { MainTab.DEFAULT_ORDER }
        }
    }

    /** Emits the current set of hidden tabs. Defaults to empty. */
    val bottomNavHidden: Flow<Set<MainTab>> = context.dataStore.data.map { preferences ->
        val raw = preferences[BOTTOM_NAV_HIDDEN]
        if (raw.isNullOrEmpty()) {
            emptySet()
        } else {
            raw.mapNotNull { MainTab.fromNameOrNull(it) }.toSet()
        }
    }

    /** Saves the full bottom-nav order (comma-separated tab names). */
    suspend fun saveBottomNavOrder(order: List<MainTab>) {
        context.dataStore.edit { preferences ->
            preferences[BOTTOM_NAV_ORDER] = order.joinToString(",") { it.name }
        }
    }

    /** Saves the set of hidden tabs. */
    suspend fun saveBottomNavHidden(hidden: Set<MainTab>) {
        context.dataStore.edit { preferences ->
            preferences[BOTTOM_NAV_HIDDEN] = hidden.map { it.name }.toSet()
        }
    }

    /** Convenience: save order and hidden in a single DataStore edit. */
    suspend fun saveBottomNavConfig(order: List<MainTab>, hidden: Set<MainTab>) {
        context.dataStore.edit { preferences ->
            preferences[BOTTOM_NAV_ORDER] = order.joinToString(",") { it.name }
            preferences[BOTTOM_NAV_HIDDEN] = hidden.map { it.name }.toSet()
        }
    }

    // ── Theme configuration ───────────────────────────────────

    private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")

    private val PURE_SURFACES_KEY = booleanPreferencesKey("pure_surfaces")
    private val SYSTEM_FONT_KEY = booleanPreferencesKey("system_font")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val name = preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(name)
        } catch (_: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DYNAMIC_COLOR_KEY] ?: false
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    suspend fun saveDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR_KEY] = enabled
        }
    }

    /** Neutral "pure surfaces": true black backgrounds in dark theme, true white in light. */
    val pureSurfaces: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PURE_SURFACES_KEY] ?: false
    }

    suspend fun savePureSurfaces(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PURE_SURFACES_KEY] = enabled
        }
    }

    /** System font: platform default typeface instead of the bundled Roboto Flex. */
    val systemFont: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SYSTEM_FONT_KEY] ?: false
    }

    suspend fun saveSystemFont(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SYSTEM_FONT_KEY] = enabled
        }
    }

    // ── Categories configuration ───────────────────────────────────

    private val CATEGORIES_ENABLED_KEY = booleanPreferencesKey("categories_enabled")

    val categoriesEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[CATEGORIES_ENABLED_KEY] ?: false
    }

    suspend fun saveCategoriesEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[CATEGORIES_ENABLED_KEY] = enabled
        }
    }

    // ── General category name ───────────────────────────────────

    /**
     * Custom user-facing name for the "General" virtual category (the one
     * backed by categoryId == null). When null, the UI falls back to the
     * localized string R.string.category_general.
     */
    private val GENERAL_CATEGORY_NAME_KEY = stringPreferencesKey("general_category_name")

    val generalCategoryName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[GENERAL_CATEGORY_NAME_KEY]?.takeIf { it.isNotBlank() }
    }

    suspend fun saveGeneralCategoryName(name: String?) {
        context.dataStore.edit { preferences ->
            if (name.isNullOrBlank()) {
                preferences.remove(GENERAL_CATEGORY_NAME_KEY)
            } else {
                preferences[GENERAL_CATEGORY_NAME_KEY] = name.trim()
            }
        }
    }

    // ── Remember last visited category ───────────────────────────────────

    private val LAST_CATEGORY_ID_KEY = intPreferencesKey("last_category_id")

    /** Emits the id of the last visited category tab, or null if never set. */
    val lastCategoryId: Flow<Int?> = context.dataStore.data.map { preferences ->
        preferences[LAST_CATEGORY_ID_KEY]
    }

    /** Persists the last visited category id. */
    suspend fun saveLastCategoryId(id: Int) {
        context.dataStore.edit { preferences ->
            preferences[LAST_CATEGORY_ID_KEY] = id
        }
    }
}