package com.rnd.remoto.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rnd.remoto.data.DeviceCategory
import com.rnd.remoto.data.DeviceRepository
import com.rnd.remoto.data.DeviceType
import com.rnd.remoto.data.RemoteDevice
import com.rnd.remoto.data.category
import com.rnd.remoto.network.NetworkScanner
import com.rnd.remoto.network.ScanResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class AddStep { CHOOSE, IR, WIFI }

/** If a device of the same type and name already exists, reuse its id so saving updates it in
 * place instead of leaving a stale duplicate behind (e.g. re-adding a TV whose IP changed). */
private suspend fun existingIdFor(repository: DeviceRepository, type: DeviceType, name: String): String? =
    repository.devices.first().firstOrNull { it.type == type && it.name.equals(name, ignoreCase = true) }?.id

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(
    repository: DeviceRepository,
    onDone: () -> Unit
) {
    var step by rememberSaveable { mutableStateOf(AddStep.CHOOSE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (step == AddStep.CHOOSE) "Agregar dispositivo" else "Configurar") },
                navigationIcon = {
                    IconButton(onClick = { if (step == AddStep.CHOOSE) onDone() else step = AddStep.CHOOSE }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxWidth()) {
            when (step) {
                AddStep.CHOOSE -> ConnectionTypeChooser(
                    onSelectIr = { step = AddStep.IR },
                    onSelectWifi = { step = AddStep.WIFI }
                )
                AddStep.IR -> IrSetupStep(repository = repository, onDone = onDone)
                AddStep.WIFI -> WifiDiscoverStep(repository = repository, onDone = onDone)
            }
        }
    }
}

@Composable
private fun ConnectionTypeChooser(onSelectIr: () -> Unit, onSelectWifi: () -> Unit) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "¿Cómo se controla tu dispositivo?",
            style = MaterialTheme.typography.titleMedium
        )

        ConnectionOptionCard(
            icon = Icons.Filled.SettingsRemote,
            title = "Infrarrojo",
            subtitle = "TVs viejos, aires acondicionados, equipos de audio. Necesita que tu celular tenga emisor IR.",
            onClick = onSelectIr
        )

        ConnectionOptionCard(
            icon = Icons.Filled.Wifi,
            title = "Wi-Fi (red local)",
            subtitle = "Smart TV, TV box, o cualquier equipo conectado a tu Wi-Fi. La app lo busca sola.",
            onClick = onSelectWifi
        )
    }
}

@Composable
private fun ConnectionOptionCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun IrSetupStep(repository: DeviceRepository, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "El infrarrojo solo funciona si tu celular tiene un emisor IR físico " +
                "(algunos Xiaomi, Samsung y Huawei más antiguos). Después de guardar, " +
                "vas a poder cargar el código de cada botón dentro de la pantalla del control.",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre (ej: Aire acondicionado)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                scope.launch {
                    repository.saveDevice(RemoteDevice(name = name, type = DeviceType.IR))
                    onDone()
                }
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar")
        }
    }
}

@Composable
private fun WifiDiscoverStep(repository: DeviceRepository, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { NetworkScanner(context) }

    var isScanning by remember { mutableStateOf(false) }
    var scanResults by remember { mutableStateOf(listOf<ScanResult>()) }
    var hasScannedOnce by remember { mutableStateOf(false) }
    var pendingResult by remember { mutableStateOf<ScanResult?>(null) }
    var manualMode by remember { mutableStateOf(false) }

    suspend fun runScan() {
        isScanning = true
        scanResults = scanner.scanLocalNetwork()
        isScanning = false
        hasScannedOnce = true
    }

    LaunchedEffect(Unit) { runScan() }

    Column(
        modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isScanning) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text("Buscando dispositivos en tu red Wi-Fi...")
            }
        } else if (scanResults.isNotEmpty()) {
            Text("Encontramos esto en tu red:", style = MaterialTheme.typography.titleSmall)
            scanResults.forEach { result ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { pendingResult = result }
                ) {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.Tv, contentDescription = null) },
                        headlineContent = { Text(shortLabel(result.guessedType)) },
                        supportingContent = { Text(result.ip) }
                    )
                }
            }
        } else if (hasScannedOnce) {
            Text("No encontramos nada automáticamente en tu red.")
        }

        OutlinedButton(
            onClick = { scope.launch { runScan() } },
            enabled = !isScanning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.height(0.dp))
            Text(" Buscar de nuevo")
        }

        Spacer(Modifier.height(0.dp))

        TextButton(onClick = { manualMode = !manualMode }) {
            Text(if (manualMode) "Ocultar carga manual" else "¿No aparece tu dispositivo? Agregar manualmente")
        }

        if (manualMode) {
            ManualWifiForm(repository = repository, onDone = onDone)
        }
    }

    val result = pendingResult
    if (result != null) {
        NameAndSaveDialog(
            title = "Nombrá tu ${shortLabel(result.guessedType)}",
            onDismiss = { pendingResult = null },
            onConfirm = { name ->
                scope.launch {
                    val existingId = existingIdFor(repository, result.guessedType, name)
                    repository.saveDevice(
                        RemoteDevice(
                            id = existingId ?: java.util.UUID.randomUUID().toString(),
                            name = name,
                            type = result.guessedType,
                            ip = result.ip,
                            port = result.port
                        )
                    )
                    pendingResult = null
                    onDone()
                }
            }
        )
    }
}

@Composable
private fun NameAndSaveDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre (ej: TV del living)") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

private val WIFI_BRANDS = listOf(
    DeviceType.SAMSUNG,
    DeviceType.LG_WEBOS,
    DeviceType.SONY_BRAVIA,
    DeviceType.VIZIO,
    DeviceType.PHILIPS,
    DeviceType.PANASONIC,
    DeviceType.ROKU,
    DeviceType.ANDROID_TV,
    DeviceType.WOL
)

private fun categoryLabel(category: DeviceCategory): String = when (category) {
    DeviceCategory.SMART_TV -> "Smart TV"
    DeviceCategory.TV_BOX -> "TV Box / Streaming"
    DeviceCategory.OTRO -> "Otro"
    DeviceCategory.INFRARROJO -> "Infrarrojo"
}

@Composable
private fun ManualWifiForm(repository: DeviceRepository, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var selectedType by rememberSaveable { mutableStateOf(DeviceType.SAMSUNG) }
    var name by rememberSaveable { mutableStateOf("") }
    var ip by rememberSaveable { mutableStateOf("") }
    var mac by rememberSaveable { mutableStateOf("") }
    var psk by rememberSaveable { mutableStateOf("") }

    val isValid = name.isNotBlank() && when (selectedType) {
        DeviceType.WOL -> mac.isNotBlank()
        DeviceType.SONY_BRAVIA -> ip.isNotBlank() && psk.isNotBlank()
        else -> ip.isNotBlank()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WIFI_BRANDS.groupBy { it.category() }.forEach { (category, types) ->
            Text(categoryLabel(category), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                types.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(shortLabel(type)) }
                    )
                }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre (ej: TV del living)") },
            modifier = Modifier.fillMaxWidth()
        )

        if (selectedType == DeviceType.WOL) {
            OutlinedTextField(
                value = mac,
                onValueChange = { mac = it },
                label = { Text("Dirección MAC (AA:BB:CC:DD:EE:FF)") },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("Dirección IP del equipo") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (selectedType == DeviceType.SONY_BRAVIA) {
            OutlinedTextField(
                value = psk,
                onValueChange = { psk = it },
                label = { Text("Clave precompartida (PSK)") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "En el TV: Ajustes → Red → Config. de red doméstica → Control IP → " +
                    "activá el control IP y anotá/definí la clave precompartida.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Button(
            onClick = {
                scope.launch {
                    val existingId = existingIdFor(repository, selectedType, name)
                    repository.saveDevice(buildManualDevice(selectedType, name, ip, mac, psk, existingId))
                    onDone()
                }
            },
            enabled = isValid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar")
        }
    }
}

private fun buildManualDevice(
    type: DeviceType,
    name: String,
    ip: String,
    mac: String,
    psk: String,
    existingId: String?
): RemoteDevice {
    val id = existingId ?: java.util.UUID.randomUUID().toString()
    return when (type) {
        DeviceType.WOL -> RemoteDevice(id = id, name = name, type = type, mac = mac.trim())
        DeviceType.ROKU -> RemoteDevice(id = id, name = name, type = type, ip = ip.trim(), port = 8060)
        DeviceType.SONY_BRAVIA -> RemoteDevice(id = id, name = name, type = type, ip = ip.trim(), sonyPsk = psk.trim())
        else -> RemoteDevice(id = id, name = name, type = type, ip = ip.trim())
    }
}

private fun shortLabel(type: DeviceType): String = when (type) {
    DeviceType.IR -> "Infrarrojo"
    DeviceType.ROKU -> "Roku"
    DeviceType.LG_WEBOS -> "LG Smart TV"
    DeviceType.SAMSUNG -> "Samsung Smart TV"
    DeviceType.SONY_BRAVIA -> "Sony Bravia"
    DeviceType.VIZIO -> "Vizio SmartCast"
    DeviceType.PHILIPS -> "Philips (no Android)"
    DeviceType.PANASONIC -> "Panasonic Viera"
    DeviceType.ANDROID_TV -> "Android TV / TV Box"
    DeviceType.WOL -> "Encender por red"
}
