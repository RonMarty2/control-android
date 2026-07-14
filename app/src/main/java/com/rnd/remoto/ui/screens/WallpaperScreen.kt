package com.rnd.remoto.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rnd.remoto.lockscreen.LockScreenControlsService
import com.rnd.remoto.premium.BillingManager
import com.rnd.remoto.premium.PremiumRepository
import com.rnd.remoto.wallpaper.PhotoWallpaperSetter
import com.rnd.remoto.wallpaper.VideoWallpaperLauncher
import com.rnd.remoto.wallpaper.WallpaperFiles
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperScreen(
    premiumRepository: PremiumRepository,
    billingManager: BillingManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val state by premiumRepository.state.collectAsState(initial = null)
    val priceText by billingManager.priceTextFlow.collectAsState()

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = PhotoWallpaperSetter.setPhotoWallpaper(context, uri)
            if (result.isSuccess) {
                premiumRepository.setPhotoWallpaper(WallpaperFiles.photoFile(context).absolutePath)
                notify("Fondo de pantalla actualizado")
            } else {
                notify(result.exceptionOrNull()?.message ?: "No se pudo poner el fondo de pantalla")
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                WallpaperFiles.copyToInternal(context, uri, WallpaperFiles.videoFile(context))
            }.onSuccess {
                premiumRepository.setVideoWallpaper(WallpaperFiles.videoFile(context).absolutePath)
                context.startActivity(VideoWallpaperLauncher.changeLiveWallpaperIntent(context))
            }.onFailure {
                notify(it.message ?: "No se pudo copiar el video")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fondo de pantalla") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val currentState = state ?: return@Scaffold
        val isPremium = currentState.isPremium

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Poné una foto o un video como fondo de pantalla de tu celular. Gratis podés " +
                    "elegir 1 foto y 1 video; para cambiarlos de nuevo hace falta la versión premium.",
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = {
                    if (!isPremium && currentState.hasUsedFreePhotoSlot) {
                        notify("Ya usaste tu foto gratis. Comprá premium para cambiarla.")
                    } else {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (currentState.photoWallpaperUri == null) "Elegir foto de fondo" else "Cambiar foto de fondo")
            }

            Button(
                onClick = {
                    if (!isPremium && currentState.hasUsedFreeVideoSlot) {
                        notify("Ya usaste tu video gratis. Comprá premium para cambiarlo.")
                    } else {
                        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (currentState.videoWallpaperUri == null) "Elegir video de fondo" else "Cambiar video de fondo")
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Controles en pantalla de bloqueo", style = MaterialTheme.typography.titleSmall)
                        Switch(
                            checked = currentState.lockScreenControlsEnabled,
                            onCheckedChange = { enabled ->
                                if (!isPremium) {
                                    notify("Esta función es premium")
                                } else {
                                    scope.launch { premiumRepository.setLockScreenControlsEnabled(enabled) }
                                    val serviceIntent = Intent(context, LockScreenControlsService::class.java)
                                    if (enabled) {
                                        androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
                                    } else {
                                        context.stopService(serviceIntent)
                                    }
                                }
                            }
                        )
                    }
                    Text(
                        "Muestra botones de Power, Volumen, Canal y Play/Pausa directo en tu " +
                            "pantalla de bloqueo, para el último dispositivo que usaste.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (!isPremium) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Control Remoto Premium", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Pago único, sin suscripción. Desbloquea fotos y videos de fondo " +
                                "ilimitados, y los controles en pantalla de bloqueo.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = {
                                val activity = context as? Activity
                                if (activity != null) {
                                    billingManager.launchPurchase(activity)
                                } else {
                                    notify("No se pudo iniciar la compra")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(priceText?.let { "Comprar premium · $it" } ?: "Comprar premium")
                        }
                    }
                }
            }
        }
    }
}
