package com.rnd.remoto.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    deviceId: String,
    repository: DeviceRepository,
    irController: IrController,
    controllerFactory: RemoteControllerFactory,
    onBack: () -> Unit
) {
    val devices by repository.devices.collectAsState(initial = emptyList())
    val device = devices.find { it.id == deviceId }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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

    IconButton(onClick = { press(RemoteCommand.POWER) }) {
        Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Encender/Apagar", modifier = Modifier.height(40.dp))
    }

    DPad(onUp = { press(RemoteCommand.UP) }, onDown = { press(RemoteCommand.DOWN) },
        onLeft = { press(RemoteCommand.LEFT) }, onRight = { press(RemoteCommand.RIGHT) },
        onCenter = { press(RemoteCommand.SELECT) })

    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        IconButton(onClick = { press(RemoteCommand.BACK) }) { Text("Volver") }
        IconButton(onClick = { press(RemoteCommand.HOME) }) { Icon(Icons.Filled.Home, contentDescription = "Inicio") }
        IconButton(onClick = { press(RemoteCommand.PLAY_PAUSE) }) { Icon(Icons.Filled.Pause, contentDescription = "Play/Pausa") }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        IconButton(onClick = { press(RemoteCommand.VOLUME_DOWN) }) { Icon(Icons.Filled.VolumeDown, contentDescription = "Bajar volumen") }
        IconButton(onClick = { press(RemoteCommand.MUTE) }) { Icon(Icons.Filled.VolumeOff, contentDescription = "Silencio") }
        IconButton(onClick = { press(RemoteCommand.VOLUME_UP) }) { Icon(Icons.Filled.VolumeUp, contentDescription = "Subir volumen") }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        OutlinedButton(onClick = { press(RemoteCommand.CHANNEL_DOWN) }) { Text("CH -") }
        OutlinedButton(onClick = { press(RemoteCommand.CHANNEL_UP) }) { Text("CH +") }
    }
}

@Composable
private fun DPad(onUp: () -> Unit, onDown: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit, onCenter: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onUp) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Arriba") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            IconButton(onClick = onLeft) { Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Izquierda") }
            OutlinedButton(onClick = onCenter) { Text("OK") }
            IconButton(onClick = onRight) { Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Derecha") }
        }
        IconButton(onClick = onDown) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Abajo") }
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
