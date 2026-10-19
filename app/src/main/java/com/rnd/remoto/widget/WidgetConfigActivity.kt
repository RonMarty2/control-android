package com.rnd.remoto.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.rnd.remoto.data.DeviceRepository
import com.rnd.remoto.ui.theme.ControlRemotoTheme
import kotlinx.coroutines.launch

/** Pantalla que se abre al colocar un widget: elegir a qué dispositivo controla. */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Si el usuario sale sin elegir, el sistema cancela la colocación del widget.
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val repository = DeviceRepository(applicationContext)
        setContent {
            ControlRemotoTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val devices by repository.devices.collectAsState(initial = emptyList())
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("¿Qué dispositivo controla este widget?", style = MaterialTheme.typography.titleLarge)
                        if (devices.isEmpty()) {
                            Text("Primero agregá un dispositivo en la app.", style = MaterialTheme.typography.bodyMedium)
                        }
                        devices.forEach { device ->
                            Card(onClick = { choose(device.id) }, modifier = Modifier.fillMaxWidth()) {
                                ListItem(
                                    headlineContent = { Text(device.name) },
                                    supportingContent = { Text(device.type.name.replace('_', ' ')) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun choose(deviceId: String) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { it[WIDGET_DEVICE_KEY] = deviceId }
            ControlWidget().update(this@WidgetConfigActivity, glanceId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}
