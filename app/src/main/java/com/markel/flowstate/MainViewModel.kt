package com.markel.flowstate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.markel.flowstate.core.data.MainTab
import com.markel.flowstate.core.data.ThemeMode
import com.markel.flowstate.core.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    /** Tab to use as the second element of the initial back stack. */
    private val _initialTab = MutableStateFlow(MainTab.TASKS)
    val initialTab = _initialTab.asStateFlow()

    /** True once the initial back stack has been resolved from DataStore. */
    private val _isReady = MutableStateFlow(false)
    val isReady = _isReady.asStateFlow()

    /** Current bottom nav order (all tabs in user-configured order). */
    val bottomNavOrder: StateFlow<List<MainTab>> = userPreferencesRepository.bottomNavOrder
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainTab.DEFAULT_ORDER)

    /** Current set of hidden tabs. */
    val bottomNavHidden: StateFlow<Set<MainTab>> = userPreferencesRepository.bottomNavHidden
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Current theme mode (system, light, dark)*/
    val themeMode: StateFlow<ThemeMode> = userPreferencesRepository.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeMode.SYSTEM
        )

    /** Whether the dynamic color is activated or not*/
    val dynamicColor: StateFlow<Boolean> = userPreferencesRepository.dynamicColor
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /** Whether neutral pure surfaces (true black / true white) are activated or not */
    val pureSurfaces: StateFlow<Boolean> = userPreferencesRepository.pureSurfaces
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    init {
        viewModelScope.launch {
            // Combine last tab with hidden tabs to ensure startDestination is always visible
            combine(
                userPreferencesRepository.lastTab,
                userPreferencesRepository.bottomNavHidden
            ) { lastTab, hiddenTabs ->
                if (lastTab in hiddenTabs) {
                    // Fallback to the first non-hidden tab
                    MainTab.DEFAULT_ORDER.firstOrNull { it !in hiddenTabs } ?: MainTab.TASKS
                } else {
                    lastTab
                }
            }.collect { tab ->
                _initialTab.value = tab
                _isReady.value = true
            }
        }
    }

    fun saveLastTab(tab: MainTab) {
        viewModelScope.launch {
            userPreferencesRepository.saveLastTab(tab)
        }
    }

    fun saveBottomNavConfig(order: List<MainTab>, hidden: Set<MainTab>) {
        viewModelScope.launch {
            userPreferencesRepository.saveBottomNavConfig(order, hidden)
        }
    }

    fun saveThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferencesRepository.saveThemeMode(mode)
        }
    }

    fun saveDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveDynamicColor(enabled)
        }
    }

    fun savePureSurfaces(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.savePureSurfaces(enabled)
        }
    }

}