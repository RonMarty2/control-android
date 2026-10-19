package com.rnd.remoto.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rnd.remoto.data.DeviceRepository
import com.rnd.remoto.data.DeviceType
import com.rnd.remoto.data.RemoteDevice
import com.rnd.remoto.ir.IrController
import com.rnd.remoto.network.NetworkScanner
import com.rnd.remoto.network.RemoteCommand
import com.rnd.remoto.network.RemoteController
import com.rnd.remoto.network.RemoteControllerFactory
import com.rnd.remoto.network.androidtv.AndroidTvPairingClient
import com.rnd.remoto.network.VizioPairingClient
import com.rnd.remoto.premium.PremiumRepository
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    deviceId: String,
    repository: DeviceRepository,
    irController: IrController,
    controllerFactory: RemoteControllerFactory,
    premiumRepository: PremiumRepository,
    onBack: () -> Unit
) {
    val devices by repository.devices.collectAsState(initial = emptyList())
    val device = devices.find { it.id == deviceId }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(deviceId) { premiumRepository.setLastUsedDeviceId(deviceId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device?.name ?: "Control") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (device == null) {
            Text("Dispositivo no encontrado", modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }

        fun report(result: Result<Unit>) {
            val error = result.exceptionOrNull() ?: return
            scope.launch { snackbarHostState.showSnackbar(error.message ?: "Error al enviar el comando") }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (device.type) {
                DeviceType.IR -> IrRemoteBody(
                    device = device,
                    irController = irController,
                    repository = repository,
                    onError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                )
                DeviceType.WOL -> WolRemoteBody(
                    controller = controllerFactory.create(device),
                    onResult = ::report
                )
                DeviceType.ANDROID_TV -> if (!device.androidTvPaired) {
                    AndroidTvPairingBody(
                        device = device,
                        repository = repository,
                        onError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
                } else {
                    val controller = remember(device.id) { controllerFactory.create(device) }
                    DisposableEffect(controller) {
                        onDispose { controller?.close() }
                    }
                    NetworkRemoteBody(
                        device = device,
                        controller = controller,
                        irController = irController,
                        repository = repository,
                        onResult = ::report,
                        showAppShortcuts = true,
                        // Si el TV ya no reconoce este celular (p. ej. se vinculó otro equipo o se
                        // reseteó la lista de emparejados), hay que volver a emparejar.
                        onRepair = {
                            scope.launch { repository.updateDevice(device.id) { it.copy(androidTvPaired = false) } }
                        }
                    )
                }
                DeviceType.VIZIO -> if (device.vizioAuthToken == null) {
                    VizioPairingBody(
                        device = device,
                        repository = repository,
                        onError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
                } else {
                    val controller = remember(device.id) { controllerFactory.create(device) }
                    DisposableEffect(controller) {
                        onDispose { controller?.close() }
                    }
                    NetworkRemoteBody(
                        device = device,
                        controller = controller,
                        irController = irController,
                        repository = repository,
                        onResult = ::report
                    )
                }
                else -> {
                    val controller = remember(device.id) { controllerFactory.create(device) }
                    DisposableEffect(controller) {
                        onDispose { controller?.close() }
                    }
                    NetworkRemoteBody(
                        device = device,
                        controller = controller,
                        irController = irController,
                        repository = repository,
                        onResult = ::report
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkRemoteBody(
    device: RemoteDevice,
    controller: RemoteController?,
    irController: IrController,
    repository: DeviceRepository,
    onResult: (Result<Unit>) -> Unit,
    showAppShortcuts: Boolean = false,
    onRepair: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var showIrPowerDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Mantener la conexión abierta mientras se ve el control: así el TV puede avisar cuando se
    // enfoca un cuadro de texto o se toca el micrófono, aunque todavía no se haya tocado ningún botón.
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        if (!showAppShortcuts) return@LaunchedEffect
        while (isActive) {
            val ok = c.connect()
            delay(if (ok) 5_000 else 20_000)
        }
    }

    fun press(command: RemoteCommand) {
        scope.launch { onResult(controller?.send(command) ?: Result.failure(IllegalStateException("Dispositivo no configurado"))) }
    }
    fun launchApp(appLink: String) {
        scope.launch { onResult(controller?.launchApp(appLink) ?: Result.failure(IllegalStateException("Dispositivo no configurado"))) }
    }
    fun pressPower() {
        scope.launch {
            // Con código IR cargado, si el equipo no responde en la red (apagado del todo) mandamos
            // el IR de una vez, sin esperar a que la conexión Wi-Fi agote sus reintentos.
            val storedIr = device.irCodes["POWER"]
            if (!storedIr.isNullOrBlank() && irController.hasIrEmitter()) {
                val ip = device.ip
                val port = controlPortFor(device)
                val reachable = ip != null && port != null && NetworkScanner(context).isReachable(ip, port, 800)
                if (!reachable) {
                    onResult(irController.sendCode(storedIr))
                    return@launch
                }
            }
            val wifiResult = controller?.send(RemoteCommand.POWER)
                ?: Result.failure(IllegalStateException("Dispositivo no configurado"))
            val irPowerCode = device.irCodes["POWER"]
            // Muchos TV box/smart TV no responden por Wi-Fi cuando están totalmente apagados
            // (el chip de red también se apaga); si falló y hay un código IR de encendido
            // cargado, lo mandamos como respaldo - una vez prendido, todo lo demás sigue por Wi-Fi.
            if (wifiResult.isFailure && !irPowerCode.isNullOrBlank()) {
                onResult(irController.sendCode(irPowerCode))
            } else {
                onResult(wifiResult)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showAppShortcuts) TvTextBar(controller = controller, onResult = onResult)

        RemoteIconButton(
            icon = Icons.Filled.PowerSettingsNew,
            contentDescription = "Encender/Apagar",
            size = 76.dp,
            onClick = { pressPower() }
        )

        if (irController.hasIrEmitter() || !device.irCodes["POWER"].isNullOrBlank()) TextButton(onClick = { showIrPowerDialog = true }) {
            Text(
                if (device.irCodes["POWER"].isNullOrBlank()) "¿No prende por Wi-Fi? Configurar encendido por IR"
                else "Encendido por IR configurado (editar)"
            )
        }

        BigDPad(
            onUp = { press(RemoteCommand.UP) }, onDown = { press(RemoteCommand.DOWN) },
            onLeft = { press(RemoteCommand.LEFT) }, onRight = { press(RemoteCommand.RIGHT) },
            onCenter = { press(RemoteCommand.SELECT) }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            RemoteTextButton(text = "Volver", onClick = { press(RemoteCommand.BACK) })
            RemoteIconButton(Icons.Filled.Home, "Inicio", onClick = { press(RemoteCommand.HOME) })
            RemoteIconButton(Icons.Filled.Pause, "Play/Pausa", onClick = { press(RemoteCommand.PLAY_PAUSE) })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            RemoteIconButton(Icons.Filled.VolumeDown, "Bajar volumen", onClick = { press(RemoteCommand.VOLUME_DOWN) })
            RemoteIconButton(Icons.Filled.VolumeOff, "Silencio", onClick = { press(RemoteCommand.MUTE) })
            RemoteIconButton(Icons.Filled.VolumeUp, "Subir volumen", onClick = { press(RemoteCommand.VOLUME_UP) })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            RemoteTextButton(text = "CH -", onClick = { press(RemoteCommand.CHANNEL_DOWN) })
            RemoteTextButton(text = "CH +", onClick = { press(RemoteCommand.CHANNEL_UP) })
        }

        if (showAppShortcuts) {
            JellyfinButton(onClick = { scope.launch { onResult(controller?.openApp(JELLYFIN_PACKAGE) ?: Result.failure(IllegalStateException("Dispositivo no configurado"))) } })
        }

        if (onRepair != null) {
            TextButton(onClick = onRepair) { Text("¿No responde? Volver a emparejar") }
        }
    }


    if (showIrPowerDialog) {
        IrSearchDialog(
            device = device,
            repository = repository,
            irController = irController,
            onResult = onResult,
            onDismiss = { showIrPowerDialog = false }
        )
    }
}

internal fun controlPortFor(device: RemoteDevice): Int? = when (device.type) {
    DeviceType.ANDROID_TV -> 6466
    DeviceType.ROKU -> device.port ?: 8060
    DeviceType.SAMSUNG -> device.port ?: 8002
    DeviceType.LG_WEBOS -> device.port ?: 3000
    DeviceType.VIZIO -> device.port ?: 7345
    DeviceType.SONY_BRAVIA -> 80
    DeviceType.PHILIPS -> 1925
    DeviceType.PANASONIC -> 55000
    else -> null
}

@Composable
internal fun RemoteIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 64.dp,
    repeatable: Boolean = false
) {
    if (!repeatable) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(size)) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(size / 2))
        }
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val containerColor = if (isPressed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
            .repeatingClickable(interactionSource = interactionSource, scope = scope, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(size / 2)
        )
    }
}

/**
 * Like a plain clickable, but holding it down keeps firing [onClick] on an accelerating
 * interval instead of only once — mimics a physical remote's fast-forward/rewind behavior.
 */
private fun Modifier.repeatingClickable(
    interactionSource: MutableInteractionSource,
    scope: CoroutineScope,
    maxDelayMillis: Long = 400,
    minDelayMillis: Long = 80,
    delayDecayFactor: Float = 0.25f,
    onClick: () -> Unit
): Modifier = this.pointerInput(interactionSource) {
    detectTapGestures(
        onPress = { offset ->
            val press = PressInteraction.Press(offset)
            val heldButtonJob = scope.launch {
                interactionSource.emit(press)
                var currentDelayMillis = maxDelayMillis
                onClick()
                while (isActive) {
                    delay(currentDelayMillis)
                    onClick()
                    currentDelayMillis = (currentDelayMillis - (currentDelayMillis * delayDecayFactor).toLong())
                        .coerceAtLeast(minDelayMillis)
                }
            }
            tryAwaitRelease()
            heldButtonJob.cancel()
            interactionSource.emit(PressInteraction.Release(press))
        }
    )
}

@Composable
internal fun RemoteTextButton(text: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.height(64.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun BigDPad(onUp: () -> Unit, onDown: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit, onCenter: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RemoteIconButton(Icons.Filled.KeyboardArrowUp, "Arriba", onClick = onUp, size = 72.dp, repeatable = true)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            RemoteIconButton(Icons.Filled.KeyboardArrowLeft, "Izquierda", onClick = onLeft, size = 72.dp, repeatable = true)
            FilledIconButton(onClick = onCenter, modifier = Modifier.size(84.dp)) {
                Text("OK", style = MaterialTheme.typography.titleLarge)
            }
            RemoteIconButton(Icons.Filled.KeyboardArrowRight, "Derecha", onClick = onRight, size = 72.dp, repeatable = true)
        }
        RemoteIconButton(Icons.Filled.KeyboardArrowDown, "Abajo", onClick = onDown, size = 72.dp, repeatable = true)
    }
}

@Composable
private fun WolRemoteBody(controller: RemoteController?, onResult: (Result<Unit>) -> Unit) {
    val scope = rememberCoroutineScope()
    Text("Wake-on-LAN solo permite encender el equipo por red.")
    Button(onClick = {
        scope.launch {
            onResult(controller?.send(RemoteCommand.POWER) ?: Result.failure(IllegalStateException("Falta la MAC")))
        }
    }) {
        Icon(Icons.Filled.PowerSettingsNew, contentDescription = null)
        Spacer(Modifier.height(0.dp))
        Text(" Encender")
    }
}

@Composable
private fun AndroidTvPairingBody(
    device: RemoteDevice,
    repository: DeviceRepository,
    onError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var pairingClient by remember { mutableStateOf<AndroidTvPairingClient?>(null) }
    var awaitingPin by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }

    Text("Este Android TV / TV Box todavía no está emparejado.", style = MaterialTheme.typography.titleSmall)
    Text(
        "Al tocar \"Emparejar\" va a aparecer un código de 6 dígitos en la pantalla del TV. Escribilo acá abajo.",
        style = MaterialTheme.typography.bodySmall
    )

    if (!awaitingPin) {
        Button(
            enabled = !isBusy,
            onClick = {
                val ip = device.ip
                if (ip == null) {
                    onError("Este dispositivo no tiene una IP guardada")
                    return@Button
                }
                scope.launch {
                    isBusy = true
                    val client = AndroidTvPairingClient(ip)
                    val result = client.connectAndRequestPin("Control Remoto")
                    isBusy = false
                    result.onSuccess {
                        pairingClient = client
                        awaitingPin = true
                    }.onFailure { onError(it.message ?: "No se pudo conectar para emparejar") }
                }
            }
        ) {
            Text(if (isBusy) "Conectando..." else "Emparejar")
        }
    } else {
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it },
            label = { Text("Código de 6 dígitos en el TV") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            enabled = !isBusy && pin.length == 6,
            onClick = {
                val client = pairingClient ?: return@Button
                scope.launch {
                    isBusy = true
                    val result = client.submitPin(pin.trim())
                    if (result.isSuccess) {
                        repository.updateDevice(device.id) { it.copy(androidTvPaired = true) }
                        awaitingPin = false
                    } else {
                        onError(result.exceptionOrNull()?.message ?: "El PIN no coincide")
                    }
                    isBusy = false
                }
            }
        ) {
            Text(if (isBusy) "Verificando..." else "Confirmar código")
        }
    }
}

@Composable
private fun VizioPairingBody(
    device: RemoteDevice,
    repository: DeviceRepository,
    onError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var pairingClient by remember { mutableStateOf<VizioPairingClient?>(null) }
    var pairingDeviceId by remember { mutableStateOf<String?>(null) }
    var awaitingPin by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }

    Text("Este TV Vizio todavía no está emparejado.", style = MaterialTheme.typography.titleSmall)
    Text(
        "Al tocar \"Emparejar\" va a aparecer un código en la pantalla del TV. Escribilo acá abajo.",
        style = MaterialTheme.typography.bodySmall
    )

    if (!awaitingPin) {
        Button(
            enabled = !isBusy,
            onClick = {
                val ip = device.ip
                if (ip == null) {
                    onError("Este dispositivo no tiene una IP guardada")
                    return@Button
                }
                scope.launch {
                    isBusy = true
                    val client = VizioPairingClient(ip, device.port ?: 7345)
                    val deviceId = device.vizioDeviceId ?: UUID.randomUUID().toString()
                    val result = client.startPairing(deviceId)
                    isBusy = false
                    result.onSuccess {
                        pairingClient = client
                        pairingDeviceId = deviceId
                        awaitingPin = true
                    }.onFailure { onError(it.message ?: "No se pudo conectar para emparejar") }
                }
            }
        ) {
            Text(if (isBusy) "Conectando..." else "Emparejar")
        }
    } else {
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it },
            label = { Text("Código mostrado en el TV") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            enabled = !isBusy && pin.isNotBlank(),
            onClick = {
                val client = pairingClient ?: return@Button
                val deviceId = pairingDeviceId ?: return@Button
                scope.launch {
                    isBusy = true
                    val result = client.submitPin(deviceId, pin)
                    result.onSuccess { authToken ->
                        repository.saveDevice(
                            device.copy(vizioDeviceId = deviceId, vizioAuthToken = authToken)
                        )
                        awaitingPin = false
                    }.onFailure { onError(it.message ?: "El código no coincide") }
                    isBusy = false
                }
            }
        ) {
            Text(if (isBusy) "Verificando..." else "Confirmar código")
        }
    }
}
