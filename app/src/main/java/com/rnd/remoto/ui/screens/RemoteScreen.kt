package com.rnd.remoto.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.res.Configuration
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rnd.remoto.data.DeviceRepository
import com.rnd.remoto.data.DeviceType
import com.rnd.remoto.data.RemoteDevice
import com.rnd.remoto.ir.IrController
import com.rnd.remoto.network.NetworkScanner
import com.rnd.remoto.network.RemoteCommand
import com.rnd.remoto.network.WolClient
import com.rnd.remoto.network.RemoteController
import com.rnd.remoto.network.RemoteControllerFactory
import com.rnd.remoto.network.androidtv.AndroidTvIdentity
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
    var connection by remember(device.id) { mutableStateOf<ConnectionState?>(null) }
    var selectedTab by remember(device.id) { mutableStateOf(0) } // 0 = Control, 1 = Jellyfin
    val activePackage by (controller?.currentForegroundApp ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()

    // Auto-sincronización pasiva: cada vez que el TV Box avisa qué app tiene en primer plano,
    // la guardamos en discoveredApps (y si no es una app del sistema, queda disponible para usar).
    LaunchedEffect(activePackage, device.id) {
        val pkg = activePackage ?: return@LaunchedEffect
        if (pkg.isNotBlank() && pkg !in IGNORED_SYSTEM_PACKAGES && pkg !in device.discoveredApps) {
            repository.updateDevice(device.id) { current ->
                val updatedDiscovered = (current.discoveredApps + pkg).distinct()
                val updatedFavorites = if (pkg !in current.favoriteApps) {
                    (current.favoriteApps + pkg).distinct()
                } else current.favoriteApps
                current.copy(
                    discoveredApps = updatedDiscovered,
                    favoriteApps = updatedFavorites
                )
            }
        }
    }

    // Mantener la conexión abierta mientras se ve el control: así el TV puede avisar cuando se
    // enfoca un cuadro de texto o se toca el micrófono, aunque todavía no se haya tocado ningún botón.
    // De paso sabemos si el equipo responde, está apagado, o ya no nos reconoce (hay que re-emparejar).
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        if (!showAppShortcuts) return@LaunchedEffect
        val scanner = NetworkScanner(context)
        var failures = 0
        while (isActive) {
            val ok = c.connect()
            if (ok) {
                failures = 0
                connection = ConnectionState.ONLINE
            } else {
                failures++
                val ip = device.ip
                val port = controlPortFor(device)
                val reachable = ip != null && port != null && scanner.isReachable(ip, port, 800)
                // El puerto responde pero el handshake falla repetido: el TV rechaza nuestro certificado.
                connection = if (reachable && failures >= 2) ConnectionState.NEEDS_PAIRING else ConnectionState.OFFLINE
            }
            delay(if (ok) 5_000 else 20_000)
        }
    }

    // Equipos que no mantienen conexión abierta: solo comprobamos si contestan.
    LaunchedEffect(device.id, device.ip) {
        if (showAppShortcuts) return@LaunchedEffect
        val ip = device.ip ?: return@LaunchedEffect
        val port = controlPortFor(device) ?: return@LaunchedEffect
        val scanner = NetworkScanner(context)
        while (isActive) {
            connection = if (scanner.isReachable(ip, port, 800)) ConnectionState.ONLINE else ConnectionState.OFFLINE
            delay(10_000)
        }
    }

    fun press(command: RemoteCommand) {
        scope.launch { onResult(controller?.send(command) ?: Result.failure(IllegalStateException("Dispositivo no configurado"))) }
    }
    fun openTvApp(packageName: String) {
        scope.launch { onResult(controller?.openApp(packageName) ?: Result.failure(IllegalStateException("Dispositivo no configurado"))) }
    }
    fun notifyMessage(msg: String) {
        onResult(Result.failure(IllegalStateException(msg)))
    }
    var lastPowerAt by remember { mutableStateOf(0L) }
    fun pressPower() {
        val now = System.currentTimeMillis()
        if (now - lastPowerAt < 3_000) {
            onResult(Result.failure(IllegalStateException("Ya se envió el encendido: espera unos segundos.")))
            return
        }
        lastPowerAt = now
        scope.launch {
            // Con código IR cargado, si el equipo no responde en la red (apagado del todo) mandamos
            // el IR de una vez, sin esperar a que la conexión Wi-Fi agote sus reintentos.
            val storedIr = device.irCodes["POWER"]
            val wolMac = device.mac?.takeIf { it.isNotBlank() }
            val canIr = !storedIr.isNullOrBlank() && irController.hasIrEmitter()
            if (canIr || wolMac != null) {
                val ip = device.ip
                val port = controlPortFor(device)
                val reachable = ip != null && port != null && NetworkScanner(context).isReachable(ip, port, 800)
                if (!reachable) {
                    // Apagado del todo: Wake-on-LAN (si hay MAC) despierta equipos en espera con red;
                    // el IR cubre los que no. Se mandan ambos: si uno no aplica, el otro no estorba.
                    val wol = wolMac?.let { WolClient(it).send(RemoteCommand.POWER) }
                    val ir = if (canIr) irController.sendCode(storedIr!!) else null
                    onResult(ir ?: wol ?: Result.success(Unit))
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

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Botón superior para cambiar rápido entre [Teclas del Control] y [Catálogo de Jellyfin] (Opción 1B)
    if (showAppShortcuts) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val tabModifier = Modifier.weight(1f)
            if (selectedTab == 0) {
                Button(onClick = { selectedTab = 0 }, modifier = tabModifier) {
                    Text("Teclas del Control")
                }
            } else {
                TextButton(onClick = { selectedTab = 0 }, modifier = tabModifier) {
                    Text("Teclas del Control")
                }
            }
            if (selectedTab == 1) {
                Button(onClick = { selectedTab = 1 }, modifier = tabModifier) {
                    JellyfinLogo(Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Jellyfin")
                }
            } else {
                TextButton(onClick = { selectedTab = 1 }, modifier = tabModifier) {
                    Text("Catálogo Jellyfin")
                }
            }
        }
    }

    if (showAppShortcuts && selectedTab == 1) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ConnectionBanner(
                state = connection,
                hasIrFallback = !device.irCodes["POWER"].isNullOrBlank(),
                onRepair = onRepair
            )
            // Controles rápidos de volumen/pausa siempre a mano arriba del catálogo de Jellyfin
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RemoteIconButton(Icons.Filled.VolumeDown, "Bajar volumen", onClick = { press(RemoteCommand.VOLUME_DOWN) }, size = 48.dp, repeatable = true)
                RemoteIconButton(Icons.Filled.VolumeUp, "Subir volumen", onClick = { press(RemoteCommand.VOLUME_UP) }, size = 48.dp, repeatable = true)
                RemoteIconButton(Icons.Filled.Pause, "Play/Pausa", onClick = { press(RemoteCommand.PLAY_PAUSE) }, size = 48.dp)
                RemoteIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Volver en TV", onClick = { press(RemoteCommand.BACK) }, size = 48.dp)
                RemoteIconButton(Icons.Filled.Home, "Inicio TV", onClick = { press(RemoteCommand.HOME) }, size = 48.dp)
            }
            JellyfinTabContent(
                device = device,
                repository = repository,
                controller = controller,
                onMessage = ::notifyMessage
            )
        }
        return
    }

    val top: @Composable ColumnScope.() -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConnectionBanner(
                state = connection,
                hasIrFallback = !device.irCodes["POWER"].isNullOrBlank(),
                onRepair = onRepair
            )
            RemoteIconButton(
                icon = Icons.Filled.PowerSettingsNew,
                contentDescription = "Encender/Apagar",
                size = 56.dp,
                onClick = { pressPower() }
            )
        }
    }

    val keypad: @Composable ColumnScope.() -> Unit = {
        RemoteKeypad(
            onUp = { press(RemoteCommand.UP) }, onDown = { press(RemoteCommand.DOWN) },
            onLeft = { press(RemoteCommand.LEFT) }, onRight = { press(RemoteCommand.RIGHT) },
            onCenter = { press(RemoteCommand.SELECT) },
            onBack = { press(RemoteCommand.BACK) }, onHome = { press(RemoteCommand.HOME) },
            onPlayPause = { press(RemoteCommand.PLAY_PAUSE) }, onMute = { press(RemoteCommand.MUTE) },
            onVolumeUp = { press(RemoteCommand.VOLUME_UP) }, onVolumeDown = { press(RemoteCommand.VOLUME_DOWN) },
            onChannelUp = { press(RemoteCommand.CHANNEL_UP) }, onChannelDown = { press(RemoteCommand.CHANNEL_DOWN) }
        )
    }

    val bottom: @Composable ColumnScope.() -> Unit = {
        if (showAppShortcuts) {
            TvAppsShortcutBar(
                favoritePackages = device.favoriteApps,
                discoveredPackages = device.discoveredApps,
                activePackage = activePackage,
                onOpenApp = ::openTvApp,
                onUpdateFavorites = { updated ->
                    scope.launch {
                        repository.updateDevice(device.id) { it.copy(favoriteApps = updated) }
                    }
                }
            )
            TvTextBar(controller = controller, onResult = onResult)
        }

        if (irController.hasIrEmitter() || !device.irCodes["POWER"].isNullOrBlank()) TextButton(onClick = { showIrPowerDialog = true }) {
            Text(
                if (device.irCodes["POWER"].isNullOrBlank()) "¿No prende por Wi-Fi? Configurar encendido por IR"
                else "Encendido por IR configurado (editar)"
            )
        }

        if (onRepair != null) {
            TextButton(onClick = onRepair) { Text("¿No responde? Volver a emparejar") }
        }
    }

    if (landscape) {
        // De lado: estado, encendido y apps a la izquierda; el teclado completo a la derecha.
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) { top(); bottom() }
            Column(
                modifier = Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
            ) { keypad() }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) { top(); keypad(); bottom() }
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
    val haptic = LocalHapticFeedback.current
    val tap = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
    if (!repeatable) {
        FilledTonalIconButton(onClick = tap, modifier = Modifier.size(size)) {
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
            .repeatingClickable(interactionSource = interactionSource, scope = scope, onClick = tap),
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
    val haptic = LocalHapticFeedback.current
    FilledTonalButton(
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        modifier = Modifier.height(64.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun BigDPad(onUp: () -> Unit, onDown: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit, onCenter: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RemoteIconButton(Icons.Filled.KeyboardArrowUp, "Arriba", onClick = onUp, size = 60.dp, repeatable = true)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteIconButton(Icons.Filled.KeyboardArrowLeft, "Izquierda", onClick = onLeft, size = 60.dp, repeatable = true)
            FilledIconButton(
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onCenter() },
                modifier = Modifier.size(68.dp)
            ) {
                Text("OK", style = MaterialTheme.typography.titleLarge)
            }
            RemoteIconButton(Icons.Filled.KeyboardArrowRight, "Derecha", onClick = onRight, size = 60.dp, repeatable = true)
        }
        RemoteIconButton(Icons.Filled.KeyboardArrowDown, "Abajo", onClick = onDown, size = 60.dp, repeatable = true)
    }
}

/** Pastilla vertical estilo control real: "+" arriba, etiqueta al medio y "-" abajo. */
@Composable
private fun Rocker(
    label: String,
    upIcon: ImageVector,
    downIcon: ImageVector,
    upDescription: String,
    downDescription: String,
    onUp: () -> Unit,
    onDown: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.width(56.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onUp() },
                modifier = Modifier.size(56.dp)
            ) { Icon(upIcon, contentDescription = upDescription) }
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onDown() },
                modifier = Modifier.size(56.dp)
            ) { Icon(downIcon, contentDescription = downDescription) }
        }
    }
}

/** Cuerpo del control: volumen | cruceta | canal, y debajo Volver / Inicio / Play / Silencio. */
@Composable
internal fun RemoteKeypad(
    onUp: () -> Unit, onDown: () -> Unit, onLeft: () -> Unit, onRight: () -> Unit, onCenter: () -> Unit,
    onBack: () -> Unit, onHome: () -> Unit, onPlayPause: () -> Unit, onMute: () -> Unit,
    onVolumeUp: () -> Unit, onVolumeDown: () -> Unit, onChannelUp: () -> Unit, onChannelDown: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Rocker(
            "VOL", Icons.Filled.VolumeUp, Icons.Filled.VolumeDown,
            "Subir volumen", "Bajar volumen", onVolumeUp, onVolumeDown
        )
        BigDPad(onUp, onDown, onLeft, onRight, onCenter)
        Rocker(
            "CH", Icons.Filled.KeyboardArrowUp, Icons.Filled.KeyboardArrowDown,
            "Canal siguiente", "Canal anterior", onChannelUp, onChannelDown
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        RemoteIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onClick = onBack)
        RemoteIconButton(Icons.Filled.Home, "Inicio", onClick = onHome)
        RemoteIconButton(Icons.Filled.Pause, "Play/Pausa", onClick = onPlayPause)
        RemoteIconButton(Icons.Filled.VolumeOff, "Silencio", onClick = onMute)
    }
}

private fun isVpnActive(context: Context): Boolean {
    return runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        } else {
            @Suppress("DEPRECATION")
            cm.allNetworks.any { net ->
                cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        }
    }.getOrDefault(false)
}

/** Estado de la conexión con un equipo de red, para mostrarlo en el control. */
internal enum class ConnectionState { ONLINE, OFFLINE, NEEDS_PAIRING }

@Composable
internal fun ConnectionBanner(state: ConnectionState?, hasIrFallback: Boolean, onRepair: (() -> Unit)?) {
    if (state == null) return
    val context = LocalContext.current
    // Una VPN encendida manda también el tráfico local por internet y el TV deja de ser alcanzable.
    val vpn = remember(state) { isVpnActive(context) }
    val (color, text) = when (state) {
        ConnectionState.ONLINE -> Color(0xFF2E9E5B) to "Conectado"
        ConnectionState.OFFLINE -> Color(0xFFD69E2E) to when {
            vpn -> "Sin respuesta: tenés una VPN encendida. Apagala para llegar al equipo."
            hasIrFallback -> "Sin respuesta: apagado o fuera de la red. El botón de encendido usará IR."
            else -> "Sin respuesta: el equipo está apagado o fuera de la red."
        }
        ConnectionState.NEEDS_PAIRING -> Color(0xFFE53E3E) to "El equipo ya no reconoce este celular"
    }
    if (state == ConnectionState.NEEDS_PAIRING && onRepair != null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    "Suele pasar tras actualizar o resetear el TV. Hay que emparejar de nuevo con el código que muestra.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Button(onClick = onRepair) { Text("Volver a emparejar") }
            }
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.labelMedium)
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
                    val result = client.connectAndRequestPin(AndroidTvIdentity.clientName())
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
