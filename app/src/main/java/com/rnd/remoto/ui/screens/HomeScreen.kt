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
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    onOpenDevice: (String) -> Unit
) {
    val devices by repository.devices.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Control Remoto") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDevice) {
                Icon(Icons.Default.Add, contentDescription = "Agregar dispositivo")
            }
        }
    ) { padding ->
        if (devices.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Todavía no agregaste ningún dispositivo",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Tocá el botón + para agregar tu primer control",
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                            onDelete = { scope.launch { repository.deleteDevice(device.id) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: RemoteDevice, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = onOpen
    ) {
        ListItem(
            headlineContent = { Text(device.name) },
            supportingContent = { Text(typeLabel(device.type)) },
            leadingContent = { Icon(typeIcon(device.type), contentDescription = null) },
            trailingContent = {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar")
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
