package com.rnd.remoto.widget

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.rnd.remoto.data.DeviceRepository
import com.rnd.remoto.data.DeviceType
import com.rnd.remoto.data.RemoteDevice
import com.rnd.remoto.ir.IrController
import com.rnd.remoto.network.NetworkScanner
import com.rnd.remoto.network.RemoteCommand
import com.rnd.remoto.network.RemoteController
import com.rnd.remoto.network.RemoteControllerFactory
import com.rnd.remoto.premium.PremiumRepository
import com.rnd.remoto.ui.screens.controlPortFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap

/**
 * Envío de comandos fuera de la pantalla de la app (widgets y notificación de bloqueo).
 * Mantiene un controlador por dispositivo mientras viva el proceso, así el segundo toque no
 * paga de nuevo la conexión; y repite la regla del botón de encendido: si el equipo no
 * responde en la red y hay un código IR guardado, prende por infrarrojo.
 */
object RemoteActions {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val controllers = ConcurrentHashMap<String, Pair<String, RemoteController>>()

    /** El dispositivo elegido, o si no hay (o se borró) el último usado, o el primero guardado. */
    suspend fun resolveDevice(context: Context, deviceId: String?): RemoteDevice? {
        val app = context.applicationContext
        val devices = DeviceRepository(app).devices.first()
        devices.firstOrNull { it.id == deviceId }?.let { return it }
        val lastUsed = PremiumRepository(app).state.first().lastUsedDeviceId
        return devices.firstOrNull { it.id == lastUsed } ?: devices.firstOrNull()
    }

    private fun fingerprint(d: RemoteDevice) = "${d.type}|${d.ip}|${d.port}|${d.androidTvPaired}|${d.vizioAuthToken}"

    private fun controllerFor(context: Context, device: RemoteDevice): RemoteController? {
        val fp = fingerprint(device)
        controllers[device.id]?.let { (oldFp, c) -> if (oldFp == fp) return c else c.close() }
        val app = context.applicationContext
        val created = RemoteControllerFactory(app, DeviceRepository(app), scope).create(device) ?: return null
        controllers[device.id] = fp to created
        return created
    }

    /** Si el equipo responde ahora en su puerto de control (sin mandarle nada). */
    suspend fun isOnline(context: Context, device: RemoteDevice): Boolean {
        val ip = device.ip ?: return false
        val port = controlPortFor(device) ?: return false
        return NetworkScanner(context.applicationContext).isReachable(ip, port, 600)
    }

    suspend fun send(context: Context, device: RemoteDevice, command: RemoteCommand): Result<Unit> {
        val app = context.applicationContext
        val ir = IrController(app)

        if (device.type == DeviceType.IR) {
            val code = device.irCodes[command.name]
                ?: return Result.failure(IllegalStateException("Sin código IR para ${command.name}"))
            return ir.sendCode(code)
        }

        val irPower = device.irCodes["POWER"]
        val canIrPower = command == RemoteCommand.POWER && !irPower.isNullOrBlank() && ir.hasIrEmitter()
        // Apagado del todo (no responde en la red): mandar el IR directo, sin esperar reintentos de Wi-Fi.
        if (canIrPower && !isOnline(app, device)) return ir.sendCode(irPower!!)

        val controller = controllerFor(app, device)
            ?: return Result.failure(IllegalStateException("Dispositivo no configurado"))
        val result = controller.send(command)
        if (result.isFailure && canIrPower) return ir.sendCode(irPower!!)
        return result
    }

    /** Vibración corta de confirmación al tocar un botón del widget. */
    fun haptic(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
