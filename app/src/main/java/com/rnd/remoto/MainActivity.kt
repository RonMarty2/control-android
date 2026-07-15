package com.rnd.remoto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.rnd.remoto.data.DeviceRepository
import com.rnd.remoto.ir.IrController
import com.rnd.remoto.lockscreen.LockScreenControlsService
import com.rnd.remoto.network.RemoteControllerFactory
import com.rnd.remoto.network.androidtv.AndroidTvIdentity
import com.rnd.remoto.premium.BillingManager
import com.rnd.remoto.premium.DebugConfig
import com.rnd.remoto.premium.PremiumRepository
import com.rnd.remoto.ui.AppNavHost
import com.rnd.remoto.ui.theme.ControlRemotoTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidTvIdentity.initialize(applicationContext)
        val repository = DeviceRepository(applicationContext)
        val irController = IrController(applicationContext)
        val premiumRepository = PremiumRepository(applicationContext)
        val billingManager = BillingManager(applicationContext, premiumRepository, lifecycleScope)
        billingManager.startConnection()

        requestNotificationPermissionIfNeeded()
        restartLockScreenServiceIfEnabled(premiumRepository)

        setContent {
            ControlRemotoTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val scope = rememberCoroutineScope()
                    val controllerFactory = remember { RemoteControllerFactory(applicationContext, repository, scope) }
                    AppNavHost(
                        repository = repository,
                        irController = irController,
                        controllerFactory = controllerFactory,
                        premiumRepository = premiumRepository,
                        billingManager = billingManager
                    )
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun restartLockScreenServiceIfEnabled(premiumRepository: PremiumRepository) {
        lifecycleScope.launch {
            val state = premiumRepository.state.first()
            if ((state.isPremium || DebugConfig.FORCE_PREMIUM) && state.lockScreenControlsEnabled) {
                ContextCompat.startForegroundService(
                    this@MainActivity,
                    Intent(this@MainActivity, LockScreenControlsService::class.java)
                )
            }
        }
    }
}
