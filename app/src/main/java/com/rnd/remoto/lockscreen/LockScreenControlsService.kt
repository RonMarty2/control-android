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
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
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
 * Foreground service whose only job is to keep a MediaStyle notification alive so Android shows
 * its actions on the lock screen (the same mechanism music apps use), and to execute the tapped
 * command against whichever device was last opened in the app.
 */
class LockScreenControlsService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var repository: DeviceRepository
    private lateinit var premiumRepository: PremiumRepository
    private lateinit var controllerFactory: RemoteControllerFactory

    override fun onCreate() {
        super.onCreate()
        repository = DeviceRepository(applicationContext)
        premiumRepository = PremiumRepository(applicationContext)
        controllerFactory = RemoteControllerFactory(repository, scope)

        mediaSession = MediaSessionCompat(this, "ControlRemotoLockScreen").apply {
            isActive = true
            // No declaramos ACTION_PLAY_PAUSE ni un estado de reproducción real: esto no es un
            // reproductor. Si lo hiciéramos, Android dibuja un botón de pausa gigante y una barra
            // de progreso falsa (00:00 a 00:00) encima de nuestros botones, que es justo lo que
            // se veía feo. Con STATE_NONE y sin acciones, sólo se muestran nuestros propios botones.
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_NONE, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                    .build()
            )
        }

        ensureChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
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
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColorized(true)
            .setColor(BRAND_COLOR)
            .addAction(action(ACTION_POWER, "Power", R.drawable.ic_notif_power))
            .addAction(action(ACTION_VOLUME_DOWN, "Vol -", R.drawable.ic_notif_vol_down))
            .addAction(action(ACTION_VOLUME_UP, "Vol +", R.drawable.ic_notif_vol_up))
            .addAction(action(ACTION_CHANNEL_DOWN, "Canal -", R.drawable.ic_notif_ch_down))
            .addAction(action(ACTION_CHANNEL_UP, "Canal +", R.drawable.ic_notif_ch_up))
            .addAction(action(ACTION_PLAY_PAUSE, "Play/Pausa", R.drawable.ic_notif_play_pause))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_ID, "Controles rápidos", NotificationManager.IMPORTANCE_LOW)
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

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.isActive = false
        mediaSession.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "lock_screen_controls"
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
