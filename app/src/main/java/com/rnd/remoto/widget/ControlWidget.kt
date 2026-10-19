package com.rnd.remoto.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.rnd.remoto.MainActivity
import com.rnd.remoto.R
import com.rnd.remoto.data.RemoteDevice
import com.rnd.remoto.network.RemoteCommand

/** Clave del dispositivo elegido para cada widget colocado. */
internal val WIDGET_DEVICE_KEY = stringPreferencesKey("device_id")

private val CMD_PARAM = ActionParameters.Key<String>("command")
private val DEVICE_PARAM = ActionParameters.Key<String>("device")

/**
 * Widget "Control" (4×2): encendido grande, cruceta con OK, Volver/Inicio y volumen.
 * Usa los colores del fondo de pantalla (Material You) y muestra si el equipo está en la red.
 */
class ControlWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState<Preferences>(context, id)
        val device = RemoteActions.resolveDevice(context, prefs[WIDGET_DEVICE_KEY])
        val online = device?.let { RemoteActions.isOnline(context, it) } ?: false
        provideContent {
            GlanceTheme { ControlContent(device, online) }
        }
    }
}

class ControlWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ControlWidget()
}

/** Toque de un botón del widget: manda el comando al dispositivo del widget. */
class CommandAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val command = parameters[CMD_PARAM]?.let { name -> RemoteCommand.values().firstOrNull { it.name == name } } ?: return
        RemoteActions.haptic(context)
        val device = RemoteActions.resolveDevice(context, parameters[DEVICE_PARAM]?.takeIf { it.isNotEmpty() }) ?: return
        RemoteActions.send(context, device, command)
        // Refrescar el punto de estado (p. ej. después de prender la caja).
        ControlWidget().updateAll(context)
    }
}

@Composable
private fun ControlContent(device: RemoteDevice?, online: Boolean) {
    val id = device?.id.orEmpty()
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(28.dp)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Izquierda: nombre, encendido, atrás/inicio y volumen.
        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(9.dp)
                        .background(if (online) ColorProvider(Color(0xFF2ECC71)) else GlanceTheme.colors.outline)
                        .cornerRadius(5.dp)
                ) {}
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = device?.name ?: "Elegí un dispositivo",
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                )
            }
            Spacer(GlanceModifier.size(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                KeyButton(R.drawable.ic_notif_power, "Encender o apagar", RemoteCommand.POWER, id, 52.dp,
                    GlanceTheme.colors.primary, GlanceTheme.colors.onPrimary)
                Spacer(GlanceModifier.width(8.dp))
                KeyButton(R.drawable.ic_notif_back, "Volver", RemoteCommand.BACK, id, 40.dp)
                Spacer(GlanceModifier.width(8.dp))
                KeyButton(R.drawable.ic_notif_home, "Inicio", RemoteCommand.HOME, id, 40.dp)
            }
            Spacer(GlanceModifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                KeyButton(R.drawable.ic_notif_vol_down, "Bajar volumen", RemoteCommand.VOLUME_DOWN, id, 38.dp)
                Spacer(GlanceModifier.width(8.dp))
                KeyButton(R.drawable.ic_notif_mute, "Silencio", RemoteCommand.MUTE, id, 38.dp)
                Spacer(GlanceModifier.width(8.dp))
                KeyButton(R.drawable.ic_notif_vol_up, "Subir volumen", RemoteCommand.VOLUME_UP, id, 38.dp)
            }
        }

        Spacer(GlanceModifier.width(10.dp))

        // Derecha: cruceta.
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            KeyButton(R.drawable.ic_notif_up, "Arriba", RemoteCommand.UP, id, 36.dp)
            Spacer(GlanceModifier.size(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                KeyButton(R.drawable.ic_notif_left, "Izquierda", RemoteCommand.LEFT, id, 36.dp)
                Spacer(GlanceModifier.width(4.dp))
                KeyButton(R.drawable.ic_notif_select, "OK", RemoteCommand.SELECT, id, 44.dp,
                    GlanceTheme.colors.primaryContainer, GlanceTheme.colors.onPrimaryContainer)
                Spacer(GlanceModifier.width(4.dp))
                KeyButton(R.drawable.ic_notif_right, "Derecha", RemoteCommand.RIGHT, id, 36.dp)
            }
            Spacer(GlanceModifier.size(4.dp))
            KeyButton(R.drawable.ic_notif_down, "Abajo", RemoteCommand.DOWN, id, 36.dp)
        }
    }
}

/** Botón redondo con ícono que dispara [command] sobre el dispositivo [deviceId]. */
@Composable
private fun KeyButton(
    icon: Int,
    description: String,
    command: RemoteCommand,
    deviceId: String,
    size: Dp,
    background: ColorProvider = GlanceTheme.colors.secondaryContainer,
    tint: ColorProvider = GlanceTheme.colors.onSecondaryContainer
) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(background)
            .cornerRadius(size / 2)
            .clickable(
                actionRunCallback<CommandAction>(
                    actionParametersOf(CMD_PARAM to command.name, DEVICE_PARAM to deviceId)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = description,
            modifier = GlanceModifier.size(size * 0.5f),
            colorFilter = ColorFilter.tint(tint)
        )
    }
}
