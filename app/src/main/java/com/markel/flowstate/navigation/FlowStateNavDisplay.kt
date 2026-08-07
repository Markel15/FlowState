package com.markel.flowstate.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.markel.flowstate.BuildConfig
import com.markel.flowstate.components.PlaceholderScreen
import com.markel.flowstate.core.data.MainTab
import com.markel.flowstate.core.data.ThemeMode
import com.markel.flowstate.core.designsystem.ui.LocalAnimatedVisibilityScope
import com.markel.flowstate.core.designsystem.ui.LocalSharedTransitionScope
import com.markel.flowstate.core.notifications.NotificationSettingsIntentProvider
import com.markel.flowstate.feature.calendar.CalendarScreen
import com.markel.flowstate.feature.calendar.CalendarViewModel
import com.markel.flowstate.feature.flow.FlowScreen
import com.markel.flowstate.feature.flow.FlowViewModel
import com.markel.flowstate.feature.flow.checklists.CheckListEditorScreen
import com.markel.flowstate.feature.flow.ideas.IdeaEditorScreen
import com.markel.flowstate.feature.flow.tasks.components.TaskEditorScreen
import com.markel.flowstate.feature.habits.HabitScreen
import com.markel.flowstate.feature.habits.details.HabitDetailScreen
import com.markel.flowstate.feature.habits.details.HabitDetailViewModel
import com.markel.flowstate.feature.settings.AboutScreen
import com.markel.flowstate.feature.settings.AppearanceScreen
import com.markel.flowstate.feature.settings.BackupScreen
import com.markel.flowstate.feature.settings.BottomNavConfigScreen
import com.markel.flowstate.feature.settings.CategoriesScreen
import com.markel.flowstate.feature.settings.SettingsScreen

// ─────────────────────────────────────────────────────────────────────────────
// FlowStateNavDisplay
//
// Per-tab back stacks.
//
//   SharedTransitionLayout
//   └── NavDisplay
//       ├── entries = navigationState.toEntries(entryProvider)  ← Phase 2 change
//       ├── sceneDecoratorStrategies = [FlowStateSceneDecoratorStrategy]
//       ├── non-fullscreen scenes → wrapped with bottom bar
//       │   └── fullscreen scenes (FullScreenMeta=true) → pass-through
//       ├── sharedTransitionScope = this
//       └── onBack = { navigator.goBack() }
//
// Each top-level tab has its own NavBackStack (see NavigationState). The
// SaveableStateHolder decorator inside `toEntries` preserves each tab's
// scroll position and `rememberSaveable` state across tab switches.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FlowStateNavDisplay(
    navigationState: NavigationState,
    navigator: FlowStateNavigator,
    bottomNavOrder: List<MainTab>,
    bottomNavHidden: Set<MainTab>,
    onBottomNavConfigChanged: (order: List<MainTab>, hidden: Set<MainTab>) -> Unit,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    pureSurfaces: Boolean,
    systemFont: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onPureSurfacesChange: (Boolean) -> Unit,
    onSystemFontChange: (Boolean) -> Unit,
    bottomBar: @Composable () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    val sceneDecorator = rememberFlowStateSceneDecoratorStrategy(
        sharedTransitionScope = sharedTransitionScope,
        bottomBar = bottomBar,
    )

    val entryProvider = entryProvider {
        // ── TABS (decorated with the bottom bar) ────────────────────────
        entry<TabKey.Tasks>(metadata = fadeTransition()) {
            val flowViewModel: FlowViewModel = hiltViewModel()
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                lifecycleOwner.lifecycle.addObserver(flowViewModel)
                onDispose { lifecycleOwner.lifecycle.removeObserver(flowViewModel) }

            }

            val navScope = LocalNavAnimatedContentScope.current
            CompositionLocalProvider(LocalAnimatedVisibilityScope provides navScope) {
                FlowScreen(
                    flowViewModel = flowViewModel,
                    onNavigateToTaskEditor = { taskId ->
                        navigator.navigate(FullScreenKey.TaskEditor(taskId))
                    },
                    onNavigateToIdeaEditor = { ideaId ->
                        navigator.navigate(FullScreenKey.IdeaEditor(ideaId, null))
                    },
                    onNavigateToNewIdea = { categoryId ->
                        navigator.navigate(FullScreenKey.IdeaEditor(null, categoryId))
                    },
                    onNavigateToCheckListEditor = { checkListId, categoryId ->
                        navigator.navigate(FullScreenKey.CheckListEditor(checkListId, categoryId))
                    },
                )
            }
        }

        entry<TabKey.Calendar>(metadata = fadeTransition()) {
            val calendarViewModel: CalendarViewModel = hiltViewModel()
            CalendarScreen(viewModel = calendarViewModel)
        }


        entry<TabKey.Habits>(metadata = fadeTransition()) {
            HabitScreen(
                onNavigateToDetail = { habitId ->
                    navigator.navigate(FullScreenKey.HabitDetail(habitId))
                },
            )
        }


        entry<TabKey.Mood>(metadata = fadeTransition()) {
            PlaceholderScreen(stringResource(com.markel.flowstate.feature.tasks.R.string.mood))
        }

        entry<TabKey.Settings>(metadata = fadeTransition()) {
            val context = LocalContext.current
            val notificationSettingsProvider = remember {
                NotificationSettingsIntentProvider(context)
            }

            var notificationsEnabled by remember {
                mutableStateOf(notificationSettingsProvider.isNotificationPermissionGranted())
            }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        notificationsEnabled =
                            notificationSettingsProvider.isNotificationPermissionGranted()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            SettingsScreen(
                appVersion = BuildConfig.VERSION_NAME,
                onNavigateToNotifications = {
                    notificationSettingsProvider.createIntent()?.let { context.startActivity(it) }
                },
                onNavigateToAppearance = { navigator.navigate(FullScreenKey.Appearance) },
                onNavigateToBottomNavConfig = { navigator.navigate(FullScreenKey.BottomNavConfig) },
                onNavigateToCategories = { navigator.navigate(FullScreenKey.Categories) },
                onNavigateToIntegrations = { navigator.navigate(FullScreenKey.Integrations) },
                onNavigateToAbout = { navigator.navigate(FullScreenKey.About) },
                notificationsEnabled = notificationsEnabled,
            )
        }
        // ── FULLSCREEN — Flow editors (shared transitions) ─────────────
        entry<FullScreenKey.TaskEditor>(
            metadata = fullScreenVerticalSlide()
        ) { key ->
            val navScope = LocalNavAnimatedContentScope.current
            CompositionLocalProvider(LocalAnimatedVisibilityScope provides navScope) {
                TaskEditorScreen(
                    taskId = key.taskId,
                    onBack = { navigator.goBack() },
                )
            }
        }
        entry<FullScreenKey.IdeaEditor>(
            metadata = fullScreenSharedBounds()
        ) { key ->
            val navScope = LocalNavAnimatedContentScope.current
            CompositionLocalProvider(LocalAnimatedVisibilityScope provides navScope) {
                IdeaEditorScreen(
                    ideaId = key.ideaId,
                    categoryId = key.categoryId,
                    onBack = { navigator.goBack() },
                )
            }
        }

        entry<FullScreenKey.CheckListEditor>(
            metadata = fullScreenSharedBounds()
        ) { key ->
            val navScope = LocalNavAnimatedContentScope.current
            CompositionLocalProvider(LocalAnimatedVisibilityScope provides navScope) {
                CheckListEditorScreen(
                    checkListId = key.checkListId,
                    categoryId = key.categoryId,
                    onBack = { navigator.goBack() },
                )
            }
        }

        // ── FULLSCREEN — Habit detail ──────────────────────────────────
        entry<FullScreenKey.HabitDetail>(
            metadata = fullScreenVerticalSlide()
        ) { key ->
            val navScope = LocalNavAnimatedContentScope.current
            CompositionLocalProvider(LocalAnimatedVisibilityScope provides navScope) {
                val viewModel = hiltViewModel<HabitDetailViewModel, HabitDetailViewModel.Factory>(
                    creationCallback = { factory -> factory.create(key.habitId) }
                )
                HabitDetailScreen(
                    habitId = key.habitId,
                    onBack = { navigator.goBack() },
                    viewModel = viewModel
                )
            }
        }

        // ── FULLSCREEN — Settings sub-screens ──────────────────────────
        entry<FullScreenKey.About>(
            metadata = fullScreenVerticalSlide()
        ) {
            AboutScreen(
                appVersion = BuildConfig.VERSION_NAME,
                onBack = { navigator.goBack() },
            )
        }

        entry<FullScreenKey.BottomNavConfig>(
            metadata = fullScreenVerticalSlide()
        ) {
            BottomNavConfigScreen(
                currentOrder = bottomNavOrder,
                currentHidden = bottomNavHidden,
                onConfigChanged = onBottomNavConfigChanged,
                onBack = { navigator.goBack() },
            )
        }

        entry<FullScreenKey.Appearance>(
            metadata = fullScreenVerticalSlide()
        ) {
            AppearanceScreen(
                currentThemeMode = themeMode,
                currentDynamicColor = dynamicColor,
                currentPureSurfaces = pureSurfaces,
                currentSystemFont = systemFont,
                onThemeModeChange = onThemeModeChange,
                onDynamicColorChange = onDynamicColorChange,
                onPureSurfacesChange = onPureSurfacesChange,
                onSystemFontChange = onSystemFontChange,
                onBack = { navigator.goBack() },
            )
        }

        entry<FullScreenKey.Integrations>(
            metadata = fullScreenVerticalSlide()
        ) {
            BackupScreen(onBack = { navigator.goBack() })
        }

        entry<FullScreenKey.Categories>(
            metadata = fullScreenVerticalSlide()
        ) {
            CategoriesScreen(onBack = { navigator.goBack() })
        }
    }

    CompositionLocalProvider(LocalSharedTransitionScope provides sharedTransitionScope) {
        NavDisplay(
            // Use the entries overload so each tab's stack gets its own
            // SaveableStateHolder + ViewModelStore decorators (applied inside
            // NavigationState.toEntries). The `entries=` overload does NOT apply
            // entryDecorators itself.
            entries = navigationState.toEntries(entryProvider),
            modifier = modifier,
            onBack = { navigator.goBack() },
            sceneDecoratorStrategies = listOf(sceneDecorator),
            sharedTransitionScope = sharedTransitionScope
        )
    }
}
