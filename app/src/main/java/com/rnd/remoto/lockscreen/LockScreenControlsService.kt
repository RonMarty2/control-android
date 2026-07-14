package com.rnd.remoto.lockscreen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rnd.remoto.R
import com.rnd.remoto.data.DeviceRepository
import com.rnd.remoto.data.DeviceType
import com.rnd.remoto.ir.IrController
import com.rnd.remoto.network.RemoteCommand
import com.rnd.remoto.network.RemoteControllerFactory
import com.rnd.remoto.premium.PremiumRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "LockScreenControls"

/**
 * Foreground service whose only job is to keep an ongoing notification alive so Android shows
 * its action buttons on the lock screen, and to execute the tapped command against whichever
 * device was last opened in the app.
 *
 * This intentionally does NOT use MediaSessionCompat/MediaStyle. That API is meant for apps that
 * are actually playing audio/video, and declaring a fake "active" session here made some OEM
 * skins (confirmed on MIUI) treat this service as a real media player — its dedicated lock-screen
 * media layer would occasionally fail to tear down on unlock, leaving a stuck frame with the
 * clock and a notification ghosted over the home screen. A plain ongoing notification with action
 * buttons avoids that system layer entirely while still showing the same buttons on the lock screen.
 */
class LockScreenControlsService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repository: DeviceRepository
    private lateinit var premiumRepository: PremiumRepository
    private lateinit var controllerFactory: RemoteControllerFactory

    override fun onCreate() {
        super.onCreate()
        repository = DeviceRepository(applicationContext)
        premiumRepository = PremiumRepository(applicationContext)
        controllerFactory = RemoteControllerFactory(repository, scope)

        ensureChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { handleCommand(it) }
        return START_STICKY
    }

    private fun handleCommand(action: String) {
        val command = actionToCommand(action) ?: return
        scope.launch {
            val deviceId = premiumRepository.state.first().lastUsedDeviceId ?: return@launch
            val device = repository.devices.first().find { it.id == deviceId } ?: return@launch

            if (device.type == DeviceType.IR) {
                val code = device.irCodes[command.name] ?: return@launch
                IrController(applicationContext).sendHexPair(code).onFailure {
                    Log.e(TAG, "Comando IR desde pantalla de bloqueo falló", it)
                }
                return@launch
            }

            val controller = controllerFactory.create(device) ?: return@launch
            controller.send(command).onFailure {
                Log.e(TAG, "Comando desde pantalla de bloqueo falló", it)
            }
        }
    }

    private fun buildNotification(): Notification {
        fun action(actionName: String, title: String, icon: Int): NotificationCompat.Action {
            val intent = Intent(this, LockScreenControlsService::class.java).setAction(actionName)
            val pendingIntent = PendingIntent.getService(
                this,
                actionName.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return NotificationCompat.Action(icon, title, pendingIntent)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_power)
            .setContentTitle("Control Remoto")
            .setContentText("Controles rápidos del último dispositivo usado")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColorized(true)
            .setColor(BRAND_COLOR)
            .addAction(action(ACTION_POWER, "Power", R.drawable.ic_notif_power))
            .addAction(action(ACTION_VOLUME_DOWN, "Vol -", R.drawable.ic_notif_vol_down))
            .addAction(action(ACTION_VOLUME_UP, "Vol +", R.drawable.ic_notif_vol_up))
            .addAction(action(ACTION_CHANNEL_DOWN, "Canal -", R.drawable.ic_notif_ch_down))
            .addAction(action(ACTION_CHANNEL_UP, "Canal +", R.drawable.ic_notif_ch_up))
            .addAction(action(ACTION_PLAY_PAUSE, "Play/Pausa", R.drawable.ic_notif_play_pause))
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            // IMPORTANCE_DEFAULT en vez de LOW: con LOW, Android agrupa la notificación detrás de
            // un "deslizar para ver más" en la pantalla de bloqueo en vez de mostrarla directamente.
            // Se silencia sonido/vibración a mano para que igual sea silenciosa.
            val channel = NotificationChannel(CHANNEL_ID, "Controles rápidos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun actionToCommand(action: String): RemoteCommand? = when (action) {
        ACTION_POWER -> RemoteCommand.POWER
        ACTION_VOLUME_UP -> RemoteCommand.VOLUME_UP
        ACTION_VOLUME_DOWN -> RemoteCommand.VOLUME_DOWN
        ACTION_CHANNEL_UP -> RemoteCommand.CHANNEL_UP
        ACTION_CHANNEL_DOWN -> RemoteCommand.CHANNEL_DOWN
        ACTION_PLAY_PAUSE -> RemoteCommand.PLAY_PAUSE
        else -> null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        // v2: los canales de notificación son inmutables una vez creados (Android ignora cambios
        // de importancia sobre un canal ya existente), así que un ID nuevo fuerza a crear uno con
        // la importancia correcta en los celulares que ya tenían instalada una versión vieja.
        private const val CHANNEL_ID = "lock_screen_controls_v2"
        private const val NOTIFICATION_ID = 42
        private const val BRAND_COLOR = 0xFF0F766E.toInt()
        const val ACTION_POWER = "com.rnd.remoto.action.POWER"
        const val ACTION_VOLUME_UP = "com.rnd.remoto.action.VOLUME_UP"
        const val ACTION_VOLUME_DOWN = "com.rnd.remoto.action.VOLUME_DOWN"
        const val ACTION_CHANNEL_UP = "com.rnd.remoto.action.CHANNEL_UP"
        const val ACTION_CHANNEL_DOWN = "com.rnd.remoto.action.CHANNEL_DOWN"
        const val ACTION_PLAY_PAUSE = "com.rnd.remoto.action.PLAY_PAUSE"
    }
}
