import com.markel.flowstate.MainViewModel
import com.markel.flowstate.core.data.MainTab
import com.markel.flowstate.core.data.ThemeMode
import com.markel.flowstate.core.data.UserPreferencesRepository
import com.markel.flowstate.core.testing.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [com.markel.flowstate.MainViewModel].
 *
 * The whole point of [com.markel.flowstate.MainViewModel] post-Navigation3 migration is to expose
 * [com.markel.flowstate.MainViewModel.initialTab] (the tab to seed [NavigationState.topLevelRoute]
 * with on cold start) and [com.markel.flowstate.MainViewModel.isReady] (a gate so [com.markel.flowstate.MainActivity]
 * doesn't compose the nav graph until the start tab is resolved from DataStore).
 *
 * The interesting logic lives in the `init {}` block: a `combine(lastTab,
 * bottomNavHidden)` that falls back through three layers when the last tab is
 * hidden, and the four persistence setters that propagate UI configuration
 * back to [UserPreferencesRepository]. None of this had any coverage before.
 */
class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)

    // Shared flows with replay=0 so the init block's combine() doesn't emit
    // until we explicitly push a value. This lets us observe the pre-DataStore
    // state (isReady=false, initialTab=TASKS) before resolution.
    private val lastTabFlow = MutableSharedFlow<MainTab>(replay = 0, extraBufferCapacity = 1)
    private val hiddenTabsFlow = MutableSharedFlow<Set<MainTab>>(replay = 0, extraBufferCapacity = 1)
    private val orderFlow = MutableStateFlow(MainTab.DEFAULT_ORDER)

    @Before
    fun setup() {
        coEvery { userPreferencesRepository.lastTab } returns lastTabFlow
        coEvery { userPreferencesRepository.bottomNavHidden } returns hiddenTabsFlow
        coEvery { userPreferencesRepository.bottomNavOrder } returns orderFlow
        coEvery { userPreferencesRepository.themeMode } returns flowOf(ThemeMode.SYSTEM)
        coEvery { userPreferencesRepository.dynamicColor } returns flowOf(false)
        coEvery { userPreferencesRepository.pureSurfaces } returns flowOf(false)
        coEvery { userPreferencesRepository.systemFont } returns flowOf(false)
    }

    private fun buildViewModel() = MainViewModel(userPreferencesRepository)

    // ── isReady ──────────────────────────────────────────────────────────────────

    /**
     * `isReady` must start at `false` and flip to `true` exactly once when
     * the first `lastTab` emission arrives from DataStore.
     *
     * [com.markel.flowstate.MainActivity] uses this as the gate to skip rendering the nav graph
     * before the start destination is resolved — if it ever flips back to
     * `false`, the UI would flash a loading screen on every config change.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun isReady_startsFalse_thenFlipsTrueOnce() = runTest {
        val viewModel = buildViewModel()

        // Before DataStore emits — isReady must be false
        assertFalse(viewModel.isReady.value)

        // combine(lastTab, bottomNavHidden) needs BOTH to emit before it fires.
        // Push both — this triggers the init block's collect → _isReady = true
        lastTabFlow.emit(MainTab.TASKS)
        hiddenTabsFlow.emit(emptySet())
        advanceUntilIdle()

        assertTrue(viewModel.isReady.value)
    }

    // ── initialTab default ───────────────────────────────────────────────────────

    /**
     * Before the DataStore emits anything, [MainViewModel.initialTab] must
     * default to [MainTab.TASKS] (the non-removable home tab).
     *
     * This guards against: a future refactor that changes the
     * `MutableStateFlow(MainTab.TASKS)` initial value to something else, which
     * would race with [com.markel.flowstate.MainActivity]'s `initialRoute = initialTab.toKey()`.
     */
    @Test
    fun initialTab_defaultsToTasks_beforeDataStoreEmits() = runTest {
        // Don't emit anything — lastTabFlow is a no-replay SharedFlow
        val viewModel = buildViewModel()

        assertEquals(MainTab.TASKS, viewModel.initialTab.value)
    }

    // ── init: lastTab resolution ────────────────────────────────────────────────

    /**
     * Happy path: when the persisted `lastTab` is not in the hidden set,
     * [MainViewModel.initialTab] must take that value directly.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun initialTab_usesLastTab_whenNotHidden() = runTest {
        val viewModel = buildViewModel()

        lastTabFlow.emit(MainTab.HABITS)
        hiddenTabsFlow.emit(emptySet())
        advanceUntilIdle()

        assertEquals(MainTab.HABITS, viewModel.initialTab.value)
    }

    /**
     * Fallback #1: if the persisted `lastTab` is in the hidden set (the user
     * hid it from the bottom-nav config screen since last launch),
     * [MainViewModel.initialTab] must fall back to the first non-hidden tab
     * in [MainTab.DEFAULT_ORDER].
     *
     * DEFAULT_ORDER = [TASKS, CALENDAR, HABITS, MOOD, SETTINGS]
     * If HABITS is hidden, the first visible is TASKS.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun initialTab_fallsBackToFirstVisible_whenLastTabHidden() = runTest {
        val viewModel = buildViewModel()

        lastTabFlow.emit(MainTab.HABITS)
        hiddenTabsFlow.emit(setOf(MainTab.HABITS))
        advanceUntilIdle()

        assertEquals(MainTab.TASKS, viewModel.initialTab.value)
    }

    /**
     * Fallback #1 (variant): the first non-hidden tab is not always TASKS.
     * If TASKS is somehow hidden too, the next visible one must win.
     *
     * DEFAULT_ORDER = [TASKS, CALENDAR, HABITS, MOOD, SETTINGS]
     * Hiding TASKS leaves CALENDAR as the first visible. (TASKS has
     * `isRemovable = false` in practice, but the VM doesn't enforce that —
     * this test documents the contract for any future change.)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun initialTab_fallsBackToNextVisible_whenFirstIsAlsoHidden() = runTest {
        val viewModel = buildViewModel()

        lastTabFlow.emit(MainTab.MOOD)
        hiddenTabsFlow.emit(setOf(MainTab.TASKS, MainTab.MOOD))
        advanceUntilIdle()

        // First non-hidden in DEFAULT_ORDER is CALENDAR
        assertEquals(MainTab.CALENDAR, viewModel.initialTab.value)
    }

    /**
     * Fallback #2 (degenerate): if EVERY tab is hidden, [MainViewModel.initialTab]
     * must fall back to [MainTab.TASKS] (the `?: MainTab.TASKS` branch).
     *
     * This shouldn't happen in practice (the bottom-nav config UI prevents
     * hiding non-removable tabs), but the VM must not crash or produce `null`
     * if it ever does.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun initialTab_fallsBackToTasks_whenAllTabsHidden() = runTest {
        val viewModel = buildViewModel()

        lastTabFlow.emit(MainTab.CALENDAR)
        hiddenTabsFlow.emit(MainTab.entries.toSet()) // every tab hidden
        advanceUntilIdle()

        assertEquals(MainTab.TASKS, viewModel.initialTab.value)
    }

    // ── Setters ──────────────────────────────────────────────────────────────────

    /**
     * `saveLastTab` must propagate to [UserPreferencesRepository.saveLastTab]
     * so the next cold start lands on the same tab.
     *
     * Called by [FlowBottomBar] via [com.markel.flowstate.MainActivity] on every tab switch.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun saveLastTab_persistsViaRepository() = runTest {
        val viewModel = buildViewModel()

        viewModel.saveLastTab(MainTab.HABITS)
        advanceUntilIdle()

        coVerify { userPreferencesRepository.saveLastTab(MainTab.HABITS) }
    }

    /**
     * `saveBottomNavConfig` must atomically persist both the order and the
     * hidden set in a single DataStore edit (not two separate calls).
     *
     * Called by [BottomNavConfigScreen] when the user reorders or hides tabs.
     * If this ever split into two calls, a process death between them could
     * leave the persisted order/hidden set inconsistent.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun saveBottomNavConfig_persistsOrderAndHiddenAtomically() = runTest {
        val viewModel = buildViewModel()
        val order = listOf(MainTab.CALENDAR, MainTab.TASKS, MainTab.HABITS)
        val hidden = setOf(MainTab.MOOD)

        viewModel.saveBottomNavConfig(order, hidden)
        advanceUntilIdle()

        // Single atomic call — not saveBottomNavOrder + saveBottomNavHidden
        coVerify(exactly = 1) {
            userPreferencesRepository.saveBottomNavConfig(order, hidden)
        }
        coVerify(exactly = 0) { userPreferencesRepository.saveBottomNavOrder(any()) }
        coVerify(exactly = 0) { userPreferencesRepository.saveBottomNavHidden(any()) }
    }

    /**
     * `saveThemeMode` and `saveDynamicColor` must persist via their respective
     * repository methods.
     *
     * Called by [AppearanceScreen].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun saveThemeMode_and_saveDynamicColor_persistIndependently() = runTest {
        val viewModel = buildViewModel()

        viewModel.saveThemeMode(ThemeMode.DARK)
        viewModel.saveDynamicColor(enabled = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { userPreferencesRepository.saveThemeMode(ThemeMode.DARK) }
        coVerify(exactly = 1) { userPreferencesRepository.saveDynamicColor(true) }
    }
}