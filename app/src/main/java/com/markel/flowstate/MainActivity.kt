package com.markel.flowstate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

// Definimos nuestras rutas de navegación
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Tasks : Screen("tasks", "Tareas", Icons.Default.CheckCircle)
    object Habits : Screen("habits", "Hábitos", Icons.Default.DateRange)
    object Mood : Screen("mood", "Ánimo", Icons.Default.Face)
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
                // Controlador de navegación de Compose
                val navController = rememberNavController()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        FlowBottomBar(navController = navController)
                    }
                ) { innerPadding ->
                    // Host de navegación: decide qué pantalla mostrar
                    // basado en la ruta (route)
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Tasks.route, // Empezamos en Tareas
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        // --- Aquí definimos cada pantalla ---
                        composable(Screen.Tasks.route) {
                            val taskViewModel: TaskViewModel = hiltViewModel()
                            // Pasamos el ViewModel a la pantalla de tareas
                            TaskScreen(viewModel = taskViewModel)
                        }
                        composable(Screen.Habits.route) {
                            // Temporalmente un placeholder
                            PlaceholderScreen("Módulo de Hábitos")
                        }
                        composable(Screen.Mood.route) {
                            PlaceholderScreen("Módulo de Ánimo")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FlowBottomBar(navController: NavHostController) {
    // Obtenemos la ruta actual para saber qué item seleccionar
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        bottomNavItems.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label) },
                selected = currentRoute == screen.route,
                onClick = {
                    // Navega a la nueva pantalla
                    navController.navigate(screen.route) {
                        // Evita acumular pantallas en el back stack
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

// Composable simple para las pantallas que aún no hemos hecho
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
