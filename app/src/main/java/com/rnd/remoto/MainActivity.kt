package com.rnd.remoto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.rnd.remoto.data.DeviceRepository
import com.rnd.remoto.ir.IrController
import com.rnd.remoto.network.RemoteControllerFactory
import com.rnd.remoto.ui.AppNavHost
import com.rnd.remoto.ui.theme.ControlRemotoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = DeviceRepository(applicationContext)
        val irController = IrController(applicationContext)

        setContent {
            ControlRemotoTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val scope = rememberCoroutineScope()
                    val controllerFactory = remember { RemoteControllerFactory(repository, scope) }
                    AppNavHost(
                        repository = repository,
                        irController = irController,
                        controllerFactory = controllerFactory
                    )
                }
            }
        }
    }
}
