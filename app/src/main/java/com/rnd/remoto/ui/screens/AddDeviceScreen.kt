package com.rnd.remoto.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rnd.remoto.data.DeviceRepository
import com.rnd.remoto.data.DeviceType
import com.rnd.remoto.data.RemoteDevice
import com.rnd.remoto.network.NetworkScanner
import com.rnd.remoto.network.ScanResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(
    repository: DeviceRepository,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { NetworkScanner(context) }

    var selectedType by rememberSaveable { mutableStateOf(DeviceType.ROKU) }
    var name by rememberSaveable { mutableStateOf("") }
    var ip by rememberSaveable { mutableStateOf("") }
    var mac by rememberSaveable { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var scanResults by remember { mutableStateOf(listOf<ScanResult>()) }

    val isValid = name.isNotBlank() && when (selectedType) {
        DeviceType.IR -> true
        DeviceType.WOL -> mac.isNotBlank()
        else -> ip.isNotBlank()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agregar dispositivo") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Tipo de dispositivo", style = MaterialTheme.typography.titleSmall)
            Column(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeviceType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type; scanResults = emptyList() },
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

            when (selectedType) {
                DeviceType.IR -> {
                    Text(
                        "El infrarrojo solo funciona si tu celular tiene un emisor IR físico " +
                            "(algunos Xiaomi, Samsung y Huawei más antiguos). Después de guardar, " +
                            "vas a poder cargar el código de cada botón dentro de la pantalla del control.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                DeviceType.WOL -> {
                    OutlinedTextField(
                        value = mac,
                        onValueChange = { mac = it },
                        label = { Text("Dirección MAC (AA:BB:CC:DD:EE:FF)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Wake-on-LAN solo permite ENCENDER el equipo por red y requiere que " +
                            "esa función esté habilitada en el dispositivo de destino.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                else -> {
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it },
                        label = { Text("Dirección IP del TV") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        scope.launch {
                            isScanning = true
                            scanResults = scanner.scanLocalNetwork()
                            isScanning = false
                        }
                    }) {
                        Text(if (isScanning) "Buscando en tu red Wi-Fi..." else "Buscar en mi red Wi-Fi")
                    }

                    scanResults.forEach { result ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                ip = result.ip
                                selectedType = result.guessedType
                            }
                        ) {
                            ListItem(
                                headlineContent = { Text(result.ip) },
                                supportingContent = { Text("Parece ser: ${shortLabel(result.guessedType)}") }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        repository.saveDevice(buildDevice(selectedType, name, ip, mac))
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
}

private fun buildDevice(type: DeviceType, name: String, ip: String, mac: String): RemoteDevice = when (type) {
    DeviceType.IR -> RemoteDevice(name = name, type = type)
    DeviceType.WOL -> RemoteDevice(name = name, type = type, mac = mac.trim())
    DeviceType.ROKU -> RemoteDevice(name = name, type = type, ip = ip.trim(), port = 8060)
    DeviceType.LG_WEBOS -> RemoteDevice(name = name, type = type, ip = ip.trim())
    DeviceType.SAMSUNG -> RemoteDevice(name = name, type = type, ip = ip.trim())
}

private fun shortLabel(type: DeviceType): String = when (type) {
    DeviceType.IR -> "Infrarrojo"
    DeviceType.ROKU -> "Roku"
    DeviceType.LG_WEBOS -> "LG Smart TV"
    DeviceType.SAMSUNG -> "Samsung Smart TV"
    DeviceType.WOL -> "Encender por red"
}
