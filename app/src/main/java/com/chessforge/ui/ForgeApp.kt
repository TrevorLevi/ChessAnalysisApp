package com.chessforge.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chessforge.data.model.Motif
import com.chessforge.ui.screens.DashboardScreen
import com.chessforge.ui.screens.DashboardViewModel
import com.chessforge.ui.screens.GameReviewScreen
import com.chessforge.ui.screens.GamesScreen
import com.chessforge.ui.screens.GamesViewModel
import com.chessforge.ui.screens.OpeningsScreen
import com.chessforge.ui.screens.OpeningsViewModel
import com.chessforge.ui.screens.PuzzleListScreen
import com.chessforge.ui.screens.PuzzleListViewModel
import com.chessforge.ui.screens.PuzzleTrainerScreen
import com.chessforge.ui.screens.ReviewViewModel
import com.chessforge.ui.screens.SettingsScreen
import com.chessforge.ui.screens.SettingsViewModel
import com.chessforge.ui.screens.TrainerViewModel
import java.net.URLDecoder
import java.net.URLEncoder

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    DASHBOARD("dashboard", "Tableau", Icons.Filled.Insights),
    GAMES("games", "Parties", Icons.Filled.SportsEsports),
    PUZZLES("puzzles", "Puzzles", Icons.Filled.Extension),
    OPENINGS("openings", "Ouvertures", Icons.Filled.MenuBook),
    SETTINGS("settings", "Reglages", Icons.Filled.Settings),
}

private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
private fun decode(value: String): String = URLDecoder.decode(value, "UTF-8")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgeApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val currentTab = Tab.entries.firstOrNull { route?.startsWith(it.route) == true }
    val isDetail = route?.startsWith("game/") == true || route?.startsWith("trainer") == true

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Ecran deplie du telephone : la navigation passe sur le cote, plus de place pour le contenu.
        val useRail = maxWidth >= 720.dp

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(titleFor(route)) },
                    navigationIcon = {
                        if (isDetail) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.Filled.ArrowBack, "Retour")
                            }
                        }
                    },
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        for (tab in Tab.entries) {
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = { navController.navigateToTab(tab.route) },
                                icon = { Icon(tab.icon, null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (useRail) {
                    NavigationRail {
                        for (tab in Tab.entries) {
                            NavigationRailItem(
                                selected = currentTab == tab,
                                onClick = { navController.navigateToTab(tab.route) },
                                icon = { Icon(tab.icon, null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    ForgeNavHost(navController)
                }
            }
        }
    }
}

@Composable
private fun ForgeNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Tab.DASHBOARD.route) {

        composable(Tab.DASHBOARD.route) {
            val viewModel = forgeViewModel { DashboardViewModel(it) }
            DashboardScreen(
                viewModel = viewModel,
                onOpenPuzzles = { motif ->
                    navController.navigate("trainer?motif=${motif?.name ?: ""}&due=true")
                },
                onOpenGames = { navController.navigateToTab(Tab.GAMES.route) },
                onOpenOpening = { family -> navController.navigate("games?family=${encode(family)}") },
                onOpenSettings = { navController.navigateToTab(Tab.SETTINGS.route) },
            )
        }

        composable("${Tab.GAMES.route}?family={family}") { entry ->
            val family = entry.arguments?.getString("family")?.takeIf { it.isNotBlank() }?.let { decode(it) }
            val viewModel = forgeViewModel(key = "games-${family ?: "all"}") { GamesViewModel(it, family) }
            GamesScreen(
                viewModel = viewModel,
                onOpenGame = { gameId -> navController.navigate("game/${encode(gameId)}") },
            )
        }

        composable(Tab.GAMES.route) {
            val viewModel = forgeViewModel(key = "games-all") { GamesViewModel(it, null) }
            GamesScreen(
                viewModel = viewModel,
                onOpenGame = { gameId -> navController.navigate("game/${encode(gameId)}") },
            )
        }

        composable("game/{gameId}") { entry ->
            val gameId = decode(entry.arguments?.getString("gameId") ?: "")
            val viewModel = forgeViewModel(key = "review-$gameId") { ReviewViewModel(it, gameId) }
            GameReviewScreen(viewModel = viewModel)
        }

        composable(Tab.PUZZLES.route) {
            val viewModel = forgeViewModel { PuzzleListViewModel(it) }
            PuzzleListScreen(
                viewModel = viewModel,
                onTrain = { motif, dueOnly ->
                    navController.navigate("trainer?motif=${motif?.name ?: ""}&due=$dueOnly")
                },
            )
        }

        composable("trainer?motif={motif}&due={due}") { entry ->
            val motif = entry.arguments?.getString("motif")
                ?.takeIf { it.isNotBlank() }
                ?.let { name -> Motif.entries.firstOrNull { it.name == name } }
            val dueOnly = entry.arguments?.getString("due")?.toBooleanStrictOrNull() ?: true
            val viewModel = forgeViewModel(key = "trainer-${motif?.name}-$dueOnly") {
                TrainerViewModel(it, motif, dueOnly)
            }
            PuzzleTrainerScreen(
                viewModel = viewModel,
                onOpenGame = { gameId -> navController.navigate("game/${encode(gameId)}") },
                onDone = { navController.popBackStack() },
            )
        }

        composable(Tab.OPENINGS.route) {
            val viewModel = forgeViewModel { OpeningsViewModel(it) }
            OpeningsScreen(
                viewModel = viewModel,
                onOpenFamily = { family -> navController.navigate("games?family=${encode(family)}") },
            )
        }

        composable(Tab.SETTINGS.route) {
            val viewModel = forgeViewModel { SettingsViewModel(it) }
            SettingsScreen(viewModel = viewModel)
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun titleFor(route: String?): String = when {
    route == null -> "ChessForge"
    route.startsWith("dashboard") -> "Tableau de bord"
    route.startsWith("games") -> "Mes parties"
    route.startsWith("game/") -> "Revue de partie"
    route.startsWith("puzzles") -> "Mes puzzles"
    route.startsWith("trainer") -> "Entrainement"
    route.startsWith("openings") -> "Ouvertures"
    route.startsWith("settings") -> "Reglages"
    else -> "ChessForge"
}
