package com.bachatas4.android

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bachatas4.android.feature.library.LibraryScreen
import com.bachatas4.android.feature.settings.SettingsScreen
import com.bachatas4.android.feature.setup.SetupScreen
import com.bachatas4.android.feature.session.SessionScreen

object BachataRoutes {
    const val Setup = "setup"
    const val Library = "library"
    const val Game = "game/{id}"
    const val Session = "session/{id}"
    const val Settings = "settings"
}

@Composable
fun BachataNavHost(startDestination: String = BachataRoutes.Setup) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        composable(BachataRoutes.Setup) {
            SetupScreen(onContinue = { navController.navigate(BachataRoutes.Library) })
        }
        composable(BachataRoutes.Library) {
            LibraryScreen(
                onOpenSettings = { navController.navigate(BachataRoutes.Settings) },
                onLaunch = { id -> navController.navigate("session/$id") },
            )
        }
        composable(BachataRoutes.Settings) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(BachataRoutes.Game) {
            LibraryScreen(
                onOpenSettings = { navController.navigate(BachataRoutes.Settings) },
                onLaunch = { id -> navController.navigate("session/$id") },
            )
        }
        composable(BachataRoutes.Session) { entry ->
            SessionScreen(gameId = requireNotNull(entry.arguments?.getString("id")))
        }
    }
}
