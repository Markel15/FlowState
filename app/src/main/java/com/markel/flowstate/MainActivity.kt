package com.markel.flowstate

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markel.flowstate.components.FlowBottomBar
import com.markel.flowstate.components.PlaceholderScreen
import com.markel.flowstate.core.designsystem.theme.FlowStateTheme
import com.markel.flowstate.core.designsystem.ui.LocalAnimatedVisibilityScope
import com.markel.flowstate.core.designsystem.ui.LocalSharedTransitionScope
import com.markel.flowstate.core.data.MainTab
import com.markel.flowstate.feature.flow.tasks.util.HandleSystemBars
import com.markel.flowstate.navigation.BottomNavScreen
import com.markel.flowstate.navigation.FlowStateNavDisplay
import com.markel.flowstate.navigation.FlowStateNavigator
import com.markel.flowstate.navigation.NavigationState
import com.markel.flowstate.navigation.TabKey
import com.markel.flowstate.navigation.fromKey
import com.markel.flowstate.navigation.rememberNavigationState
import com.markel.flowstate.navigation.toKey
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val isReady by mainViewModel.isReady.collectAsState()
            val initialTab by mainViewModel.initialTab.collectAsState()
            val bottomNavOrder by mainViewModel.bottomNavOrder.collectAsState()
            val bottomNavHidden by mainViewModel.bottomNavHidden.collectAsState()
            val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by mainViewModel.dynamicColor.collectAsStateWithLifecycle()
            val pureSurfaces by mainViewModel.pureSurfaces.collectAsStateWithLifecycle()
            val systemFont by mainViewModel.systemFont.collectAsStateWithLifecycle()

            splashScreen.setKeepOnScreenCondition { !isReady }

            if (isReady) {
                FlowStateTheme(
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    pureSurfaces = pureSurfaces,
                    systemFont = systemFont
                ) {
                    // Check Orientation
                    val configuration = LocalConfiguration.current
                    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    HandleSystemBars(isLandscape)

                    // Build the dynamic bottom nav items based on user configuration.
                    val visibleBottomNavItems = remember(bottomNavOrder, bottomNavHidden) {
                        val screenMap = allBottomNavScreens.associateBy { MainTab.fromKey(it.key) }
                        bottomNavOrder
                            .filter { it !in bottomNavHidden }
                            .mapNotNull { screenMap[it] }
                    }

                    // The set of all top-level tabs — each gets its own NavBackStack.
                    val topLevelRoutes: Set<TabKey> = remember(bottomNavOrder, bottomNavHidden) {
                        bottomNavOrder
                            .filter { it !in bottomNavHidden }
                            .map { it.toKey() }
                            .toSet()
                    }

                    // Per-tab back stacks. Each visible tab has its own NavBackStack.
                    // Switching tabs preserves the target tab's scroll position and detail history.
                    //
                    // `initialRoute` is the persisted last tab from DataStore —
                    // used only to seed `topLevelRoute` on first composition.
                    val navigationState: NavigationState = rememberNavigationState(
                        initialRoute = initialTab.toKey(),
                        topLevelRoutes = topLevelRoutes,
                    )
                    val navigator = remember(navigationState) { FlowStateNavigator(navigationState) }

                    // On first composition, switch to the persisted initial tab
                    // (rememberSerializable restores topLevelRoute = startRoute by default).
                    LaunchedEffect(initialTab, topLevelRoutes) {
                        if (initialTab.toKey() in topLevelRoutes &&
                            navigationState.topLevelRoute != initialTab.toKey()
                        ) {
                            navigationState.topLevelRoute = initialTab.toKey()
                        }
                    }

                    // Persist the active top-level tab whenever it changes.
                    LaunchedEffect(navigationState) {
                        snapshotFlow { navigationState.topLevelRoute as? TabKey }
                            .collect { tabKey ->
                                tabKey?.let { MainTab.fromKey(it) }?.let(mainViewModel::saveLastTab)
                            }
                    }

                    SharedTransitionLayout(
                        modifier = Modifier.fillMaxSize().clipToBounds()
                    ) {
                        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                            FlowStateNavDisplay(
                                navigationState = navigationState,
                                navigator = navigator,
                                bottomNavOrder = bottomNavOrder,
                                bottomNavHidden = bottomNavHidden,
                                onBottomNavConfigChanged = mainViewModel::saveBottomNavConfig,
                                themeMode = themeMode,
                                dynamicColor = dynamicColor,
                                pureSurfaces = pureSurfaces,
                                systemFont = systemFont,
                                onThemeModeChange = mainViewModel::saveThemeMode,
                                onDynamicColorChange = mainViewModel::saveDynamicColor,
                                onPureSurfacesChange = mainViewModel::savePureSurfaces,
                                onSystemFontChange = mainViewModel::saveSystemFont,
                                sharedTransitionScope = this,
                                bottomBar = {
                                    FlowBottomBar(
                                        topLevelRoute = navigationState.topLevelRoute,
                                        onNavigate = { key -> navigator.navigate(key) },
                                        isLandscape = isLandscape,
                                        items = visibleBottomNavItems
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** All possible bottom-nav screens, used as a lookup map by MainActivity. */
val allBottomNavScreens: List<BottomNavScreen> = listOf(
    BottomNavScreen.Tasks,
    BottomNavScreen.Calendar,
    BottomNavScreen.Habits,
    BottomNavScreen.Mood,
    BottomNavScreen.Settings,
)