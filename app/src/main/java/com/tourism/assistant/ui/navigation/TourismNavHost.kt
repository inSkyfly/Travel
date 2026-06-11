package com.tourism.assistant.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tourism.assistant.ui.chat.ChatScreen
import com.tourism.assistant.ui.form.FormScreen
import com.tourism.assistant.ui.home.HomeScreen
import com.tourism.assistant.ui.plan.PlanScreen

@Composable
fun TourismNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateChat = { navController.navigate(Routes.CHAT) },
                onNavigateForm = { navController.navigate(Routes.FORM) },
                onOpenPlan = { planId -> navController.navigate(Routes.plan(planId)) }
            )
        }
        composable(Routes.CHAT) {
            ChatScreen(
                onBack = { navController.popBackStack() },
                onPlanGenerated = { planId ->
                    navController.navigate(Routes.plan(planId)) {
                        popUpTo(Routes.HOME)
                    }
                }
            )
        }
        composable(Routes.FORM) {
            FormScreen(
                onBack = { navController.popBackStack() },
                onPlanGenerated = { planId ->
                    navController.navigate(Routes.plan(planId)) {
                        popUpTo(Routes.HOME)
                    }
                }
            )
        }
        composable(
            route = Routes.PLAN,
            arguments = listOf(navArgument("planId") { type = NavType.LongType })
        ) {
            PlanScreen(onBack = { navController.popBackStack() })
        }
    }
}
