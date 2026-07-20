package com.example.resqmesh.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.resqmesh.ui.screens.*

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String = "", val icon: ImageVector? = null) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Profile : Screen("profile", "Profile", Icons.Default.Person)
    object Home : Screen("home", "Messages", Icons.Default.Chat)
    object Tools : Screen("tools", "Tools", Icons.Default.Build)
    object SurvivalGuide : Screen("survival_guide")
    object SOS : Screen("sos")
    object Chat : Screen("chat/{peerName}") {
        fun createRoute(peerName: String) = "chat/$peerName"
    }
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(onTimeout = {
                navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = { navController.navigate(Screen.Profile.route) },
                onSkipLogin = { navController.navigate(Screen.Profile.route) }
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreen(onProfileComplete = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToSurvivalGuide = {
                    navController.navigate(Screen.SurvivalGuide.route)
                },
                onNavigateToChat = { peerName ->
                    navController.navigate(Screen.Chat.createRoute(peerName))
                },
                onNavigateToSOS = {
                    navController.navigate(Screen.SOS.route)
                }
            )
        }
        composable(Screen.SOS.route) {
            SOSScreen(onBackClick = {
                navController.popBackStack()
            })
        }
        composable(Screen.SurvivalGuide.route) {
            SurvivalGuideScreen(onBackClick = {
                navController.popBackStack()
            })
        }
        composable(Screen.Chat.route) { backStackEntry ->
            val peerName = backStackEntry.arguments?.getString("peerName") ?: "Unknown"
            ChatScreen(peerName = peerName, onBackClick = {
                navController.popBackStack()
            })
        }
    }
}
