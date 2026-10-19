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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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

/** Tiempo entre un código y el siguiente en el modo manual (margen para ver si reaccionó). */
private const val STEP_MS = 1800L

/** Si el equipo aparece en la red, el código correcto se mandó hace hasta ~1 min (tiempo de arranque). */
private const val BOOT_WINDOW_CODES = 25

/** Modo automático: segundos que se espera, tras cada código, a que el equipo prendido se apague. */
private const val AUTO_WAIT_S = 7

/** Modo automático: segundos que se espera a que el equipo vuelva a la red tras re-prenderlo. */
private const val CONFIRM_WAIT_S = 70

private enum class Phase { SETUP, SWEEPING, CONFIRMING, STEPPING, DONE, FINISHED }

/**
 * Control universal por infrarrojo: prueba una a una las señales de encendido de una base de
 * datos pública hasta dar con la que maneja el equipo.
 *
 * Modo automático (equipo prendido y con IP): tras cada código espera a que el equipo salga de
 * la red; cuando se apaga, manda el mismo código otra vez, y si vuelve a aparecer lo da por
 * confirmado y lo guarda, sin que el usuario haga nada.
 * Modo manual (equipo apagado, o sin IP): el usuario toca "reaccionó" y confirma probando de a uno.
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

    // La búsqueda dura minutos: que no se apague la pantalla mientras el diálogo está abierto.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var searchScope by remember { mutableStateOf(if (device.type == DeviceType.IR) IrScope.ALL else IrScope.BOX) }
    var brand by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(device.irCodes["POWER"] ?: "") }
    var advanced by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(Phase.SETUP) }
    var candidates by remember { mutableStateOf<List<IrEntry>>(emptyList()) }
    var index by remember { mutableStateOf(0) }
    var windowStart by remember { mutableStateOf(0) }
    var windowEnd by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }
    var autoMode by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    val availableCount = remember(searchScope, brand) { IrDatabase.candidates(context, searchScope, brand).size }
    val hasNetworkTarget = device.ip != null && controlPortFor(device) != null

    fun stop() {
        job?.cancel()
        job = null
    }

    fun close() {
        stop()
        onDismiss()
    }

    fun persist(code: String, entry: IrEntry?) {
        scope.launch {
            repository.updateDevice(device.id) { d ->
                // En un dispositivo IR puro el resto de botones del mismo control también sirven.
                val extras = if (d.type == DeviceType.IR && entry != null) entry.extras else emptyMap()
                d.copy(irCodes = d.irCodes + extras + ("POWER" to code))
            }
        }
    }

    fun save(code: String, entry: IrEntry?) {
        persist(code, entry)
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
            // Equipo prendido ahora: lo esperable es que el código correcto lo apague (sale de la
            // red). Apagado: que lo prenda (aparece). Es el mismo botón de encendido.
            val wasOn = target != null && scanner != null && scanner.isReachable(target.first, target.second)
            val auto = wasOn && target != null && scanner != null
            autoMode = auto

            var i = start
            while (isActive && i < candidates.size) {
                index = i
                val result = irController.sendCode(candidates[i].code)
                if (result.isFailure) {
                    onResult(result)
                    phase = Phase.SETUP
                    return@launch
                }

                if (auto) {
                    var gone = false
                    var waited = 0
                    while (!gone && waited < AUTO_WAIT_S) {
                        delay(1000)
                        waited++
                        if (!scanner!!.isReachable(target!!.first, target.second)) gone = true
                    }
                    if (gone) {
                        // Se apagó: lo volvemos a prender con el mismo código para confirmar.
                        phase = Phase.CONFIRMING
                        delay(2000)
                        irController.sendCode(candidates[i].code)
                        var back = false
                        var w = 0
                        while (!back && w < CONFIRM_WAIT_S) {
                            delay(1000)
                            w++
                            if (scanner!!.isReachable(target!!.first, target.second)) back = true
                        }
                        if (back) {
                            persist(candidates[i].code, candidates[i])
                            phase = Phase.DONE
                        } else {
                            windowStart = i
                            windowEnd = i
                            note = "El equipo salió de la red con este código pero no volvió a aparecer solo. " +
                                "Probalo vos: tocá Enviar y mirá si se prende o se apaga."
                            phase = Phase.STEPPING
                        }
                        return@launch
                    }
                    i++
                    continue
                }

                delay(STEP_MS)
                if (scanner != null && target != null && scanner.isReachable(target.first, target.second)) {
                    windowStart = maxOf(start, i - BOOT_WINDOW_CODES)
                    windowEnd = i
                    index = windowStart
                    note = "El equipo apareció en tu red Wi-Fi: el código correcto es uno de los " +
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
                                    "Igual podés cargar un código a mano en opciones avanzadas.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                if (hasNetworkTarget)
                                    "1. Dejá el equipo PRENDIDO.\n" +
                                        "2. Apuntale la parte de arriba del celular, a menos de 3 metros.\n" +
                                        "3. Tocá Buscar y esperá: la app prueba códigos hasta que el equipo se " +
                                        "apague, lo vuelve a prender y guarda el código sola."
                                else
                                    "Apuntale la parte de arriba del celular al equipo, a menos de 3 metros, " +
                                        "y tocá Buscar. Cuando el equipo reaccione (se prenda o se apague), " +
                                        "tocá el botón que aparece.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                enabled = availableCount > 0,
                                onClick = { startSearch() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (availableCount > 0) "Buscar automáticamente" else "No hay códigos para esa marca")
                            }
                        }
                        TextButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (advanced) "Ocultar opciones avanzadas" else "Opciones avanzadas")
                        }
                        if (advanced) {
                            if (status.available) {
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
                                Text("$availableCount códigos para probar", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedTextField(
                                value = manual,
                                onValueChange = { manual = it },
                                label = { Text("Código a mano (ej: 07,02)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
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
                            if (autoMode) "Dejá el celular apuntando al equipo. Se detiene sola cuando el equipo se apague."
                            else "Mirá el equipo. Apenas se prenda o reaccione, tocá el botón.",
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
                        ) { Text("Reaccionó (se prendió o apagó)") }
                        OutlinedButton(onClick = { stop(); phase = Phase.SETUP }, modifier = Modifier.fillMaxWidth()) {
                            Text("Detener")
                        }
                    }

                    Phase.CONFIRMING -> {
                        Text("El equipo se apagó. Lo estoy volviendo a prender para confirmar...", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Esperá unos segundos, puede tardar hasta un minuto en volver a la red.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Phase.DONE -> {
                        Text("¡Listo! Código encontrado y guardado.", style = MaterialTheme.typography.titleMedium)
                        val e = candidates.getOrNull(index)
                        if (e != null && e.brands.isNotEmpty()) {
                            Text("Coincide con: ${e.brands.take(4).joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "Ahora el botón de encendido de la app prende el equipo por infrarrojo cuando no " +
                                "responde en la red.",
                            style = MaterialTheme.typography.bodySmall
                        )
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
                            "Revisá que el emisor IR apunte al equipo. Si el equipo queda en espera con el " +
                                "Wi-Fi conectado, la app no nota que se apagó: probá apagándolo del todo y " +
                                "tocando el botón cuando reaccione. También podés indicar la marca o elegir " +
                                "\"Todo\" en opciones avanzadas.",
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
            when {
                phase == Phase.SETUP && advanced -> Button(
                    enabled = manual.isNotBlank(),
                    onClick = { save(manual.trim(), null) }
                ) { Text("Guardar código") }
                phase == Phase.DONE -> Button(onClick = { close() }) { Text("Listo") }
            }
        },
        dismissButton = {
            if (phase != Phase.DONE) {
                TextButton(onClick = { close() }) { Text(if (phase == Phase.SETUP) "Cancelar" else "Cerrar") }
            }
        }
    )
}
