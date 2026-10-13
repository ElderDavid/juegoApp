package com.elderdavid.quientienelarazon.ui.navigation

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.elderdavid.quientienelarazon.AppContainer
import com.elderdavid.quientienelarazon.account.ProfileViewModel
import com.elderdavid.quientienelarazon.ads.AgeStatus
import com.elderdavid.quientienelarazon.core.ads.AgeGroup
import com.elderdavid.quientienelarazon.economy.EconomyViewModel
import com.elderdavid.quientienelarazon.local.LocalGameViewModel
import com.elderdavid.quientienelarazon.online.OnlineGameViewModel
import com.elderdavid.quientienelarazon.settings.SettingsViewModel
import com.elderdavid.quientienelarazon.stats.StatsViewModel
import com.elderdavid.quientienelarazon.ui.local.LocalGameRoute
import com.elderdavid.quientienelarazon.ui.online.OnlineRoute
import com.elderdavid.quientienelarazon.ui.screens.AgeGateScreen
import com.elderdavid.quientienelarazon.ui.screens.HomeScreen
import com.elderdavid.quientienelarazon.ui.screens.MainMenuScreen
import com.elderdavid.quientienelarazon.ui.screens.ProfileScreen
import com.elderdavid.quientienelarazon.ui.screens.RulesScreen
import com.elderdavid.quientienelarazon.ui.screens.SettingsScreen
import com.elderdavid.quientienelarazon.ui.screens.ShopScreen
import com.elderdavid.quientienelarazon.ui.screens.StatsScreen
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Rutas de la app. Las de fases posteriores se añadirán aquí a medida que existan. */
object Routes {
    const val HOME = "home"
    const val MENU = "menu"
    const val RULES = "rules"
    const val SETTINGS = "settings"
    const val LOCAL_GAME = "local"
    const val PROFILE = "profile"
    const val ONLINE = "online"
    const val STATS = "stats"
    const val SHOP = "shop"
}

@Composable
fun QtrNavHost(container: AppContainer) {
    // Pregunta neutral de edad, una vez por teléfono, antes de todo lo demás.
    val age by container.ageGate.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    when (age) {
        AgeStatus.Loading -> Unit
        AgeStatus.Unknown -> AgeGateScreen(onAnswered = { group -> scope.launch { container.ageGate.save(group) } })
        is AgeStatus.Known -> AppNavHost(container)
    }
}

@Composable
private fun AppNavHost(container: AppContainer) {
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
                ads = container.ads,
                onLocalGame = { navController.navigate(Routes.LOCAL_GAME) },
                onOnline = { navController.navigate(Routes.ONLINE) },
                onProfile = { navController.navigate(Routes.PROFILE) },
                onShop = { navController.navigate(Routes.SHOP) },
                onStats = { navController.navigate(Routes.STATS) },
                onRules = { navController.navigate(Routes.RULES) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.LOCAL_GAME) {
            val vm: LocalGameViewModel = viewModel(
                factory = LocalGameViewModel.factory(
                    loadBank = container::questionBank,
                    clock = SystemClock::elapsedRealtime,
                    newSeed = { Random.nextLong() },
                    recordFinished = container.localHistoryRepository::record,
                    economy = container.economyRepository,
                    cachedOwnedModes = container.economyCache.ownedModes,
                ),
            )
            LocalGameRoute(
                viewModel = vm,
                settingsRepository = container.settingsRepository,
                ads = container.ads,
                equippedFlow = container.economyCache.equipped,
                onExit = { navController.popBackStack() },
            )
        }
        composable(Routes.ONLINE) {
            val ageStatus by container.ageGate.status.collectAsStateWithLifecycle()
            val vm: OnlineGameViewModel = viewModel(
                factory = OnlineGameViewModel.factory(
                    repository = container.onlineRepository,
                    loadBank = container::questionBank,
                    clock = SystemClock::elapsedRealtime,
                    childPlayer = ageStatus == AgeStatus.Known(AgeGroup.CHILD),
                ),
            )
            OnlineRoute(
                viewModel = vm,
                settingsRepository = container.settingsRepository,
                ads = container.ads,
                onExit = { navController.popBackStack() },
            )
        }
        composable(Routes.PROFILE) {
            val vm: ProfileViewModel = viewModel(
                factory = ProfileViewModel.factory(
                    accounts = container.accountRepository,
                    profiles = container.profileRepository,
                    googleConfigured = container.supabaseSettings.googleConfigured,
                ),
            )
            ProfileScreen(
                viewModel = vm,
                economy = economyViewModel(container),
                googleWebClientId = container.supabaseSettings.googleWebClientId,
                onShop = { navController.navigate(Routes.SHOP) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SHOP) {
            ShopScreen(viewModel = economyViewModel(container), onBack = { navController.popBackStack() })
        }
        composable(Routes.STATS) {
            val vm: StatsViewModel = viewModel(
                factory = StatsViewModel.factory(
                    accounts = container.accountRepository,
                    profiles = container.profileRepository,
                    leaderboard = container.leaderboardRepository,
                    localHistory = container.localHistoryRepository,
                    loadBank = container::questionBank,
                ),
            )
            StatsScreen(viewModel = vm, ads = container.ads, onBack = { navController.popBackStack() })
        }
        composable(Routes.RULES) {
            RulesScreen(ads = container.ads, onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container.settingsRepository))
            SettingsScreen(
                viewModel = vm,
                economy = economyViewModel(container),
                adsConsent = container.adsConsent,
                onShop = { navController.navigate(Routes.SHOP) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun economyViewModel(container: AppContainer): EconomyViewModel = viewModel(
    factory = EconomyViewModel.factory(container.economyRepository, container.billingRepository, container.ads),
)
