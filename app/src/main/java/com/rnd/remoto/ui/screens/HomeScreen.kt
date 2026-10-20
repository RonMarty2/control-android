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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
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
    var pendingDelete by remember { mutableStateOf<RemoteDevice?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Control Remoto") },
                actions = {
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
        if (devices.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
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
                modifier = Modifier.fillMaxSize().padding(padding),
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
                            onDelete = { pendingDelete = device }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { device ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("¿Eliminar \"${device.name}\"?") },
            text = {
                Text(
                    if (device.irCodes.isNotEmpty())
                        "Se pierde el emparejamiento y los ${device.irCodes.size} código(s) IR guardados de este dispositivo. No se puede deshacer."
                    else
                        "Se pierde el emparejamiento de este dispositivo."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deleteDevice(device.id) }
                    pendingDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun DeviceRow(device: RemoteDevice, onOpen: () -> Unit, onDelete: () -> Unit) {
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
