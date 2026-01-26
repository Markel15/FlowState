package com.markel.flowstate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.markel.flowstate.feature.tasks.TaskScreen
import com.markel.flowstate.feature.tasks.TaskViewModel
import com.markel.flowstate.core.designsystem.theme.FlowStateTheme
import dagger.hilt.android.AndroidEntryPoint

// We define our navigation routes
sealed class Screen(val route: String, @StringRes val labelRes: Int, val icon: ImageVector) {
    object Tasks : Screen("tasks", com.markel.flowstate.feature.tasks.R.string.tasks, Icons.Default.CheckCircle)
    object Habits : Screen("habits", com.markel.flowstate.feature.tasks.R.string.habits, Icons.Default.DateRange)
    object Mood : Screen("mood", com.markel.flowstate.feature.tasks.R.string.mood, Icons.Default.Face)
}

val bottomNavItems = listOf(
    Screen.Tasks,
    Screen.Habits,
    Screen.Mood
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlowStateTheme {
                // Compose navigation controller
                val navController = rememberNavController()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        FlowBottomBar(navController = navController)
                    }
                ) { innerPadding ->
                    // Navigation host: decides which screen to show
                    // based on the route
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Tasks.route, // Starting in Tasks
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        // --- Here we define each screen ---
                        composable(Screen.Tasks.route) {
                            val taskViewModel: TaskViewModel = hiltViewModel()
                            // We pass the ViewModel to the tasks screen
                            TaskScreen(viewModel = taskViewModel)
                        }
                        composable(Screen.Habits.route) {
                            // Temporarily a placeholder
                            PlaceholderScreen(stringResource(com.markel.flowstate.feature.tasks.R.string.habits))
                        }
                        composable(Screen.Mood.route) {
                            PlaceholderScreen(stringResource(com.markel.flowstate.feature.tasks.R.string.mood))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FlowBottomBar(navController: NavHostController) {
    // We get the current route to know which item to select
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Column(modifier = Modifier.fillMaxWidth()) {
        // Divider to separate bottom bar from content, both have the same surface color
        HorizontalDivider(
            thickness = 0.3.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.height(110.dp)
        ) {
            bottomNavItems.forEach { screen ->
                val label = stringResource(screen.labelRes)
                NavigationBarItem(
                    icon = { Icon(screen.icon, contentDescription = label) },
                    label = { Text(label) },
                    selected = currentRoute == screen.route,
                    onClick = {
                        // Navigate to the new screen
                        navController.navigate(screen.route) {
                            // Avoid accumulating screens in the back stack
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent,
                        selectedIconColor = MaterialTheme.colorScheme.tertiary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                )
            }
        }
    }
}

// Simple Composable for screens we haven't made yet
@Composable
fun PlaceholderScreen(text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.headlineMedium)
    }
}