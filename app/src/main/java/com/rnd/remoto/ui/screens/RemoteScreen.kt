package com.rnd.remoto.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rnd.remoto.data.DeviceRepository
import com.rnd.remoto.data.DeviceType
import com.rnd.remoto.data.RemoteDevice
import com.rnd.remoto.ir.IrController
import com.rnd.remoto.network.RemoteCommand
import com.rnd.remoto.network.RemoteController
import com.rnd.remoto.network.RemoteControllerFactory
import com.rnd.remoto.network.androidtv.AndroidTvPairingClient
import com.rnd.remoto.network.VizioPairingClient
import com.rnd.remoto.premium.PremiumRepository
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
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
                    NetworkRemoteBody(controller = controller, onResult = ::report)
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
                    NetworkRemoteBody(controller = controller, onResult = ::report)
                }
                else -> {
                    val controller = remember(device.id) { controllerFactory.create(device) }
                    DisposableEffect(controller) {
                        onDispose { controller?.close() }
                    }
                    NetworkRemoteBody(controller = controller, onResult = ::report)
                }
            }
        }
    }
}

@Composable
private fun NetworkRemoteBody(controller: RemoteController?, onResult: (Result<Unit>) -> Unit) {
    val scope = rememberCoroutineScope()
    fun press(command: RemoteCommand) {
        scope.launch { onResult(controller?.send(command) ?: Result.failure(IllegalStateException("Dispositivo no configurado"))) }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        RemoteIconButton(
            icon = Icons.Filled.PowerSettingsNew,
            contentDescription = "Encender/Apagar",
            size = 76.dp,
            onClick = { press(RemoteCommand.POWER) }
        )

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
    }
}

@Composable
private fun RemoteIconButton(
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
private fun RemoteTextButton(text: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.height(64.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun BigDPad(onUp: () -> Unit, onDown: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit, onCenter: () -> Unit) {
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

private val IR_BUTTONS = listOf(
    "POWER" to "Encender/Apagar",
    "VOLUME_UP" to "Volumen +",
    "VOLUME_DOWN" to "Volumen -",
    "MUTE" to "Silencio",
    "CHANNEL_UP" to "Canal +",
    "CHANNEL_DOWN" to "Canal -"
)

@Composable
private fun IrRemoteBody(
    device: RemoteDevice,
    irController: IrController,
    repository: DeviceRepository,
    onError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var editingKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!irController.hasIrEmitter()) {
            onError("Este celular no tiene emisor infrarrojo. Esta función no va a funcionar en este equipo.")
        }
    }

    Text(
        if (irController.hasIrEmitter()) "Emisor IR detectado en este celular"
        else "⚠ Este celular no tiene emisor IR físico"
    )

    IR_BUTTONS.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            row.forEach { (key, label) ->
                val code = device.irCodes[key]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = {
                        if (code.isNullOrBlank()) {
                            editingKey = key
                        } else {
                            irController.sendHexPair(code).onFailure {
                                onError(it.message ?: "No se pudo enviar la señal IR")
                            }.onSuccess {
                                onError("Señal IR enviada (código $code)")
                            }
                        }
                    }) {
                        Text(label)
                    }
                    Text(
                        if (code.isNullOrBlank()) "sin código" else code,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                    )
                    androidx.compose.material3.TextButton(onClick = { editingKey = key }) {
                        Text("Editar código")
                    }
                }
            }
        }
    }

    val currentEditingKey = editingKey
    if (currentEditingKey != null) {
        var text by remember(currentEditingKey) { mutableStateOf(device.irCodes[currentEditingKey] ?: "") }
        AlertDialog(
            onDismissRequest = { editingKey = null },
            title = { Text("Código NEC para ${IR_BUTTONS.toMap()[currentEditingKey]}") },
            text = {
                Column {
                    Text("Formato: direccion,comando en hexadecimal. Ej: 07,02")
                    OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val updated = device.irCodes + (currentEditingKey to text.trim())
                    scope.launch { repository.saveDevice(device.copy(irCodes = updated)) }
                    editingKey = null
                }) { Text("Guardar") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { editingKey = null }) { Text("Cancelar") }
            }
        )
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
                        repository.saveDevice(device.copy(androidTvPaired = true))
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
