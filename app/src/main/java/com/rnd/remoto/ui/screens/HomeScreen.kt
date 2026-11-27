package com.rnd.remoto.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import com.rnd.remoto.update.GithubAutoUpdater
import com.rnd.remoto.update.GithubReleaseUpdate
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import com.rnd.remoto.network.NetworkScanner
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.widget.Toast
import com.rnd.remoto.widget.ControlWidgetReceiver
import com.rnd.remoto.data.DeviceCategory
import com.rnd.remoto.data.DeviceRepository
import com.rnd.remoto.data.DeviceType
import com.rnd.remoto.data.RemoteDevice
import com.rnd.remoto.data.category
import kotlinx.coroutines.launch

private val CATEGORY_ORDER = listOf(
    DeviceCategory.SMART_TV,
    DeviceCategory.TV_BOX,
    DeviceCategory.INFRARROJO,
    DeviceCategory.OTRO
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: DeviceRepository,
    onAddDevice: () -> Unit,
    onOpenDevice: (String) -> Unit,
    onOpenWallpaper: () -> Unit
) {
    val devices by repository.devices.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pendingEdit by remember { mutableStateOf<RemoteDevice?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var availableUpdate by remember { mutableStateOf<GithubReleaseUpdate?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }

    // Al abrir la app, verifica en segundo plano si hay una nueva versión publicada en GitHub
    LaunchedEffect(Unit) {
        availableUpdate = GithubAutoUpdater.checkForUpdate(context)
    }

    fun triggerUpdate(update: GithubReleaseUpdate) {
        if (isDownloadingUpdate) return
        scope.launch {
            isDownloadingUpdate = true
            downloadProgress = 0
            val res = GithubAutoUpdater.downloadAndInstall(context, update) { pct ->
                downloadProgress = pct
            }
            isDownloadingUpdate = false
            res.onFailure { err ->
                snackbarHostState.showSnackbar(err.message ?: "No se pudo descargar la actualización")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Control Remoto")
                        Text(
                            "v${GithubAutoUpdater.currentVersionName(context)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val update = GithubAutoUpdater.checkForUpdate(context)
                            if (update != null) {
                                availableUpdate = update
                            } else {
                                snackbarHostState.showSnackbar("Ya tenés la última versión instalada (v${GithubAutoUpdater.currentVersionName(context)})")
                            }
                        }
                    }) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = "Buscar actualizaciones en GitHub")
                    }
                    IconButton(onClick = {
                        // Pide al launcher colocar el widget (el sistema muestra una confirmación).
                        val manager = AppWidgetManager.getInstance(context)
                        if (manager.isRequestPinAppWidgetSupported) {
                            manager.requestPinAppWidget(ComponentName(context, ControlWidgetReceiver::class.java), null, null)
                        } else {
                            Toast.makeText(context, "Mantené apretado el escritorio y elegí Widgets > Control Remoto", Toast.LENGTH_LONG).show()
                        }
                    }) {
                        Icon(Icons.Default.Widgets, contentDescription = "Agregar widget al escritorio")
                    }
                    IconButton(onClick = onOpenWallpaper) {
                        Icon(Icons.Default.Wallpaper, contentDescription = "Fondo de pantalla")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDevice) {
                Icon(Icons.Default.Add, contentDescription = "Agregar dispositivo")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            availableUpdate?.let { update ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Nueva versión v${update.versionName} disponible",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (update.notes.isNotBlank()) {
                                    Text(
                                        update.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Button(
                                enabled = !isDownloadingUpdate,
                                onClick = { triggerUpdate(update) }
                            ) {
                                Text(if (isDownloadingUpdate) "$downloadProgress%" else "Actualizar")
                            }
                        }
                        if (isDownloadingUpdate) {
                            LinearProgressIndicator(
                                progress = { (downloadProgress / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            if (devices.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(
                            Icons.Default.SettingsRemote,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Todavía no agregaste ningún dispositivo",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Agrega tu TV, TV box o equipo infrarrojo para controlarlo desde acá",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = onAddDevice) { Text("Agregar dispositivo") }
                    }
                }
            } else {
                val grouped = devices.groupBy { it.type.category() }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CATEGORY_ORDER.forEach { category ->
                        val devicesInCategory = grouped[category] ?: return@forEach
                        item(key = "header_$category") {
                            Text(
                                categoryLabel(category),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(devicesInCategory, key = { it.id }) { device ->
                            DeviceRow(
                                device = device,
                                onOpen = { onOpenDevice(device.id) },
                                onEdit = { pendingEdit = device },
                                onDelete = {
                                    scope.launch {
                                        repository.deleteDevice(device.id)
                                        val result = snackbarHostState.showSnackbar(
                                            message = "\"${device.name}\" eliminado",
                                            actionLabel = "Deshacer",
                                            duration = SnackbarDuration.Long
                                        )
                                        if (result == SnackbarResult.ActionPerformed) repository.saveDevice(device)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingEdit?.let { device ->
        EditDeviceDialog(
            device = device,
            onDismiss = { pendingEdit = null },
            onSave = { transform ->
                scope.launch { repository.updateDevice(device.id, transform) }
                pendingEdit = null
            }
        )
    }
}

@Composable
private fun EditDeviceDialog(
    device: RemoteDevice,
    onDismiss: () -> Unit,
    onSave: ((RemoteDevice) -> RemoteDevice) -> Unit
) {
    var name by remember { mutableStateOf(device.name) }
    var ip by remember { mutableStateOf(device.ip.orEmpty()) }
    var mac by remember { mutableStateOf(device.mac.orEmpty()) }
    var clearIr by remember { mutableStateOf(false) }
    val hasIr = !device.irCodes["POWER"].isNullOrBlank()
    val macOk = mac.isBlank() || Regex("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$").matches(mac.trim())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar dispositivo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth()
                )
                if (device.ip != null) {
                    OutlinedTextField(
                        value = ip, onValueChange = { ip = it }, singleLine = true,
                        label = { Text("Dirección IP") }, modifier = Modifier.fillMaxWidth()
                    )
                }
                if (device.type != DeviceType.IR) {
                    OutlinedTextField(
                        value = mac, onValueChange = { mac = it }, singleLine = true, isError = !macOk,
                        label = { Text("MAC (opcional, para encender por red)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (hasIr) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = clearIr, onCheckedChange = { clearIr = it })
                        Text("Quitar el código IR guardado", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && macOk, onClick = {
                onSave { d ->
                    d.copy(
                        name = name.trim(),
                        ip = if (d.ip != null) ip.trim().ifBlank { d.ip } else null,
                        mac = mac.trim().ifBlank { null },
                        irCodes = if (clearIr) emptyMap() else d.irCodes
                    )
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun DeviceRow(device: RemoteDevice, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    // Estado en línea (solo para equipos de red): se consulta cada 15 s mientras la fila está visible.
    val online by produceState<Boolean?>(initialValue = null, device.id, device.ip) {
        val ip = device.ip
        val port = controlPortFor(device)
        if (ip == null || port == null) return@produceState
        val scanner = NetworkScanner(context)
        while (true) {
            value = scanner.isReachable(ip, port, 800)
            delay(15_000)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = onOpen
    ) {
        ListItem(
            headlineContent = { Text(device.name) },
            supportingContent = {
                val status = when (online) {
                    true -> " · En línea"
                    false -> " · Sin respuesta"
                    null -> ""
                }
                val ip = device.ip?.let { " · $it" } ?: ""
                Text(typeLabel(device.type) + ip + status)
            },
            leadingContent = {
                Box {
                    Icon(typeIcon(device.type), contentDescription = null)
                    online?.let { isOnline ->
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) Color(0xFF2E9E5B) else Color(0xFFD69E2E))
                        )
                    }
                }
            },
            trailingContent = {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Editar") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menuOpen = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; onDelete() }
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun categoryLabel(category: DeviceCategory): String = when (category) {
    DeviceCategory.SMART_TV -> "Smart TV"
    DeviceCategory.TV_BOX -> "TV Box / Streaming"
    DeviceCategory.INFRARROJO -> "Infrarrojo"
    DeviceCategory.OTRO -> "Otro"
}

private fun typeLabel(type: DeviceType): String = when (type) {
    DeviceType.IR -> "Infrarrojo"
    DeviceType.ROKU -> "Roku (Wi-Fi)"
    DeviceType.LG_WEBOS -> "LG Smart TV (Wi-Fi)"
    DeviceType.SAMSUNG -> "Samsung Smart TV (Wi-Fi)"
    DeviceType.SONY_BRAVIA -> "Sony Bravia (Wi-Fi)"
    DeviceType.VIZIO -> "Vizio SmartCast (Wi-Fi)"
    DeviceType.PHILIPS -> "Philips (Wi-Fi)"
    DeviceType.PANASONIC -> "Panasonic Viera (Wi-Fi)"
    DeviceType.ANDROID_TV -> "Android TV / TV Box (Wi-Fi)"
    DeviceType.WOL -> "Encender por red (Wake-on-LAN)"
}

private fun typeIcon(type: DeviceType) = when (type) {
    DeviceType.IR -> Icons.Default.SettingsRemote
    DeviceType.WOL -> Icons.Default.Router
    else -> Icons.Default.Wifi
}
