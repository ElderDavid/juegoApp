package com.elderdavid.quientienelarazon.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.elderdavid.quientienelarazon.AppContainer
import com.elderdavid.quientienelarazon.settings.SettingsViewModel
import com.elderdavid.quientienelarazon.ui.screens.HomeScreen
import com.elderdavid.quientienelarazon.ui.screens.MainMenuScreen
import com.elderdavid.quientienelarazon.ui.screens.RulesScreen
import com.elderdavid.quientienelarazon.ui.screens.SettingsScreen

/** Rutas de la app. Las de fases posteriores se añadirán aquí a medida que existan. */
object Routes {
    const val HOME = "home"
    const val MENU = "menu"
    const val RULES = "rules"
    const val SETTINGS = "settings"
}

@Composable
fun QtrNavHost(container: AppContainer) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onStart = {
                    navController.navigate(Routes.MENU) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.MENU) {
            MainMenuScreen(
                onRules = { navController.navigate(Routes.RULES) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.RULES) {
            RulesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container.settingsRepository))
            SettingsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}
