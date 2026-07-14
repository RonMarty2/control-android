package com.rnd.remoto.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rnd.remoto.data.DeviceRepository
import com.rnd.remoto.ir.IrController
import com.rnd.remoto.network.RemoteControllerFactory
import com.rnd.remoto.premium.BillingManager
import com.rnd.remoto.premium.PremiumRepository
import com.rnd.remoto.ui.screens.AddDeviceScreen
import com.rnd.remoto.ui.screens.HomeScreen
import com.rnd.remoto.ui.screens.RemoteScreen
import com.rnd.remoto.ui.screens.WallpaperScreen

@Composable
fun AppNavHost(
    repository: DeviceRepository,
    irController: IrController,
    controllerFactory: RemoteControllerFactory,
    premiumRepository: PremiumRepository,
    billingManager: BillingManager
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                repository = repository,
                onAddDevice = { navController.navigate("add_device") },
                onOpenDevice = { id -> navController.navigate("remote/$id") },
                onOpenWallpaper = { navController.navigate("wallpaper") }
            )
        }
        composable("add_device") {
            AddDeviceScreen(
                repository = repository,
                onDone = { navController.popBackStack() }
            )
        }
        composable(
            route = "remote/{deviceId}",
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId")
            if (deviceId != null) {
                RemoteScreen(
                    deviceId = deviceId,
                    repository = repository,
                    irController = irController,
                    controllerFactory = controllerFactory,
                    premiumRepository = premiumRepository,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable("wallpaper") {
            WallpaperScreen(
                premiumRepository = premiumRepository,
                billingManager = billingManager,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
