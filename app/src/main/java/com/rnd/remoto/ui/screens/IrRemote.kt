package com.rnd.remoto.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rnd.remoto.data.DeviceRepository
import com.rnd.remoto.data.RemoteDevice
import com.rnd.remoto.ir.IrController
import kotlinx.coroutines.launch

/** Botones de un control IR, con la clave con que se guardan en [RemoteDevice.irCodes]. */
private val IR_KEY_LABELS = linkedMapOf(
    "POWER" to "Encender/Apagar",
    "UP" to "Arriba",
    "DOWN" to "Abajo",
    "LEFT" to "Izquierda",
    "RIGHT" to "Derecha",
    "SELECT" to "OK",
    "BACK" to "Volver",
    "HOME" to "Inicio",
    "PLAY_PAUSE" to "Play/Pausa",
    "VOLUME_UP" to "Volumen +",
    "VOLUME_DOWN" to "Volumen -",
    "MUTE" to "Silencio",
    "CHANNEL_UP" to "Canal +",
    "CHANNEL_DOWN" to "Canal -"
)

/**
 * Control remoto infrarrojo completo. Detecta (sin emitir nada) si el celular tiene emisor IR;
 * si lo tiene, permite buscar automáticamente el código del equipo en la base de datos, lo que
 * también rellena el resto de los botones cuando el control de origen los trae.
 */
@Composable
internal fun IrRemoteBody(
    device: RemoteDevice,
    irController: IrController,
    repository: DeviceRepository,
    onError: (String) -> Unit
) {
    val status = remember { irController.status() }
    var showSearch by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }

    if (!status.available) {
        Text(status.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
        Text(
            "Un control infrarrojo necesita un celular con emisor IR. Los equipos con Wi-Fi " +
                "(Smart TV, TV Box) se pueden agregar como dispositivo Wi-Fi y funcionan en cualquier celular.",
            style = MaterialTheme.typography.bodySmall
        )
        return
    }

    fun press(key: String) {
        val code = device.irCodes[key]
        if (code.isNullOrBlank()) {
            onError("Sin código para \"${IR_KEY_LABELS[key]}\". Usá \"Buscar código\" o \"Editar códigos\".")
            return
        }
        irController.sendCode(code).onFailure { onError(it.message ?: "No se pudo enviar la señal IR") }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Emisor infrarrojo detectado", style = MaterialTheme.typography.labelMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showSearch = true }) { Text("Buscar código") }
            OutlinedButton(onClick = { showEditor = true }) { Text("Editar códigos") }
        }

        RemoteIconButton(
            icon = Icons.Filled.PowerSettingsNew,
            contentDescription = "Encender/Apagar",
            size = 76.dp,
            onClick = { press("POWER") }
        )

        BigDPad(
            onUp = { press("UP") }, onDown = { press("DOWN") },
            onLeft = { press("LEFT") }, onRight = { press("RIGHT") },
            onCenter = { press("SELECT") }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            RemoteTextButton(text = "Volver", onClick = { press("BACK") })
            RemoteIconButton(Icons.Filled.Home, "Inicio", onClick = { press("HOME") })
            RemoteIconButton(Icons.Filled.Pause, "Play/Pausa", onClick = { press("PLAY_PAUSE") })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            RemoteIconButton(Icons.Filled.VolumeDown, "Bajar volumen", onClick = { press("VOLUME_DOWN") })
            RemoteIconButton(Icons.Filled.VolumeOff, "Silencio", onClick = { press("MUTE") })
            RemoteIconButton(Icons.Filled.VolumeUp, "Subir volumen", onClick = { press("VOLUME_UP") })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            RemoteTextButton(text = "CH -", onClick = { press("CHANNEL_DOWN") })
            RemoteTextButton(text = "CH +", onClick = { press("CHANNEL_UP") })
        }
    }

    if (showSearch) {
        IrSearchDialog(
            device = device,
            repository = repository,
            irController = irController,
            onResult = { r -> r.exceptionOrNull()?.let { onError(it.message ?: "Error al enviar la señal IR") } },
            onDismiss = { showSearch = false }
        )
    }

    if (showEditor) {
        IrCodesEditorDialog(device = device, repository = repository, onDismiss = { showEditor = false })
    }
}

@Composable
private fun IrCodesEditorDialog(device: RemoteDevice, repository: DeviceRepository, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var editingKey by remember { mutableStateOf<String?>(null) }

    val key = editingKey
    if (key != null) {
        var text by remember(key) { mutableStateOf(device.irCodes[key] ?: "") }
        AlertDialog(
            onDismissRequest = { editingKey = null },
            title = { Text("Código para ${IR_KEY_LABELS[key]}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Formato NEC: direccion,comando en hexadecimal (ej: 07,02). También se aceptan " +
                            "los códigos que guarda la búsqueda automática (ej: nec:00df1ce3).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        repository.updateDevice(device.id) { d ->
                            val cleaned = text.trim()
                            d.copy(irCodes = if (cleaned.isEmpty()) d.irCodes - key else d.irCodes + (key to cleaned))
                        }
                    }
                    editingKey = null
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editingKey = null }) { Text("Cancelar") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Códigos del control") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                IR_KEY_LABELS.forEach { (k, label) ->
                    val code = device.irCodes[k]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (code.isNullOrBlank()) "sin código" else code,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        TextButton(onClick = { editingKey = k }) { Text("Editar") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } }
    )
}
