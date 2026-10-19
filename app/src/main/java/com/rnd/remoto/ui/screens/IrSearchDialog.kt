package com.rnd.remoto.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rnd.remoto.data.DeviceRepository
import com.rnd.remoto.data.DeviceType
import com.rnd.remoto.data.RemoteDevice
import com.rnd.remoto.ir.IrController
import com.rnd.remoto.ir.IrDatabase
import com.rnd.remoto.ir.IrEntry
import com.rnd.remoto.ir.IrScope
import com.rnd.remoto.network.NetworkScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Tiempo entre un código y el siguiente durante la búsqueda (da margen para ver si reaccionó). */
private const val STEP_MS = 1800L

/** Si el equipo aparece en la red, el código correcto se mandó hace hasta ~1 min (tiempo de arranque). */
private const val BOOT_WINDOW_CODES = 25

private enum class Phase { SETUP, SWEEPING, STEPPING, FINISHED }

/**
 * Control universal por infrarrojo: prueba una a una las señales de encendido de una base de
 * datos pública (miles de controles de TVs, TV boxes y decodificadores) hasta dar con la que
 * enciende el equipo. Se detiene sola si el equipo aparece en la red Wi-Fi, o cuando el usuario
 * toca "Prendió". Al confirmar guarda el código (y, en dispositivos IR, el resto de botones).
 */
@Composable
fun IrSearchDialog(
    device: RemoteDevice,
    repository: DeviceRepository,
    irController: IrController,
    onResult: (Result<Unit>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status = remember { irController.status() }

    var searchScope by remember { mutableStateOf(if (device.type == DeviceType.IR) IrScope.ALL else IrScope.BOX) }
    var brand by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(device.irCodes["POWER"] ?: "") }
    var phase by remember { mutableStateOf(Phase.SETUP) }
    var candidates by remember { mutableStateOf<List<IrEntry>>(emptyList()) }
    var index by remember { mutableStateOf(0) }
    var windowStart by remember { mutableStateOf(0) }
    var windowEnd by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    val availableCount = remember(searchScope, brand) { IrDatabase.candidates(context, searchScope, brand).size }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun close() {
        stop()
        onDismiss()
    }

    fun save(code: String, entry: IrEntry?) {
        scope.launch {
            repository.updateDevice(device.id) { d ->
                // En un dispositivo IR puro el resto de botones del mismo control también sirven.
                val extras = if (d.type == DeviceType.IR && entry != null) entry.extras else emptyMap()
                d.copy(irCodes = d.irCodes + extras + ("POWER" to code))
            }
        }
        close()
    }

    fun sweepFrom(start: Int) {
        stop()
        phase = Phase.SWEEPING
        note = null
        val ip = device.ip
        val port = controlPortFor(device)
        val target = if (ip != null && port != null) ip to port else null
        val scanner = if (target != null) NetworkScanner(context) else null

        job = scope.launch {
            // Solo tiene sentido vigilar la red si el equipo está apagado (no responde) ahora.
            val watchNetwork = target != null && scanner != null && !scanner.isReachable(target.first, target.second)
            var i = start
            while (isActive && i < candidates.size) {
                index = i
                val result = irController.sendCode(candidates[i].code)
                if (result.isFailure) {
                    onResult(result)
                    phase = Phase.SETUP
                    return@launch
                }
                delay(STEP_MS)
                if (watchNetwork && scanner!!.isReachable(target!!.first, target.second)) {
                    windowStart = maxOf(start, i - BOOT_WINDOW_CODES)
                    windowEnd = i
                    index = windowStart
                    note = "El equipo apareció en tu red Wi-Fi: el código que lo prendió es uno de los " +
                        "${windowStart + 1} a ${windowEnd + 1}. Probalos de a uno: el correcto cambia " +
                        "el estado del equipo (lo apaga o lo prende)."
                    phase = Phase.STEPPING
                    return@launch
                }
                i++
            }
            phase = Phase.FINISHED
        }
    }

    fun startSearch() {
        val list = IrDatabase.candidates(context, searchScope, brand)
        if (list.isEmpty()) return
        candidates = list
        sweepFrom(0)
    }

    fun sendAt(i: Int) {
        val r = irController.sendCode(candidates[i].code)
        if (r.isFailure) onResult(r)
    }

    AlertDialog(
        onDismissRequest = { close() },
        title = { Text("Buscar código de encendido (IR)") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (phase) {
                    Phase.SETUP -> {
                        if (!status.available) {
                            Text(status.message, color = MaterialTheme.colorScheme.error)
                            Text(
                                "La búsqueda automática necesita un celular con emisor infrarrojo. " +
                                    "Igual podés cargar un código a mano abajo.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                "Apagá el equipo del todo y apuntale la parte de arriba del celular " +
                                    "(donde está el emisor IR), a menos de 3 metros. La app va probando " +
                                    "códigos de una base pública de controles remotos, uno cada " +
                                    "${STEP_MS / 1000.0} s. Tocá \"¡Prendió!\" apenas reaccione" +
                                    if (device.ip != null) ", o dejala sola: si el equipo aparece en tu Wi-Fi, para sola." else ".",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IrScope.values().forEach { s ->
                                    FilterChip(
                                        selected = searchScope == s,
                                        onClick = { searchScope = s },
                                        label = { Text(s.label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = brand,
                                onValueChange = { brand = it },
                                label = { Text("Marca (opcional, ej: Samsung)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                enabled = availableCount > 0,
                                onClick = { startSearch() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (availableCount > 0) "Buscar automáticamente ($availableCount códigos)"
                                    else "No hay códigos para esa marca"
                                )
                            }
                        }
                        OutlinedTextField(
                            value = manual,
                            onValueChange = { manual = it },
                            label = { Text("O cargá un código a mano (ej: 07,02)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Phase.SWEEPING -> {
                        val e = candidates.getOrNull(index)
                        Text(
                            "Probando código ${index + 1} de ${candidates.size}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (e != null && e.brands.isNotEmpty()) {
                            Text("Marcas: ${e.brands.take(4).joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "Mirá el equipo. Apenas se prenda o reaccione, tocá el botón.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = {
                                stop()
                                windowEnd = index
                                windowStart = maxOf(0, index - 3)
                                index = windowStart
                                note = "Probá estos últimos códigos de a uno hasta encontrar el que cambia " +
                                    "el estado del equipo (lo apaga o lo prende)."
                                phase = Phase.STEPPING
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("¡Prendió / reaccionó!") }
                        OutlinedButton(onClick = { stop(); phase = Phase.SETUP }, modifier = Modifier.fillMaxWidth()) {
                            Text("Detener")
                        }
                    }

                    Phase.STEPPING -> {
                        val e = candidates.getOrNull(index)
                        note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Text(
                            "Código ${index + 1} de ${candidates.size}" +
                                " (rango ${windowStart + 1}–${windowEnd + 1})",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (e != null && e.brands.isNotEmpty()) {
                            Text("Marcas: ${e.brands.take(4).joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = index > windowStart,
                                onClick = { index-- },
                                modifier = Modifier.weight(1f)
                            ) { Text("◀") }
                            Button(onClick = { sendAt(index) }, modifier = Modifier.weight(2f)) { Text("Enviar") }
                            OutlinedButton(
                                enabled = index < windowEnd,
                                onClick = { index++ },
                                modifier = Modifier.weight(1f)
                            ) { Text("▶") }
                        }
                        Button(
                            onClick = { candidates.getOrNull(index)?.let { save(it.code, it) } },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Este es el correcto: guardar") }
                        TextButton(onClick = { sweepFrom(windowEnd + 1) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Ninguno sirvió: seguir buscando")
                        }
                    }

                    Phase.FINISHED -> {
                        Text("Se probaron todos los códigos y el equipo no reaccionó.", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Revisá que el emisor IR apunte al equipo y que esté apagado del todo (no en " +
                                "espera). Probá con la opción \"Todo\" o indicando la marca. Si tiene control " +
                                "remoto original, podés cargar su código a mano.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(
                            onClick = { searchScope = IrScope.ALL; phase = Phase.SETUP },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Volver") }
                    }
                }
            }
        },
        confirmButton = {
            if (phase == Phase.SETUP) {
                Button(
                    enabled = manual.isNotBlank(),
                    onClick = { save(manual.trim(), null) }
                ) { Text("Guardar código") }
            }
        },
        dismissButton = {
            TextButton(onClick = { close() }) { Text(if (phase == Phase.SETUP) "Cancelar" else "Cerrar") }
        }
    )
}
