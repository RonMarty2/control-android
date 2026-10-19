package com.rnd.remoto.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rnd.remoto.network.ImeEvent
import com.rnd.remoto.network.RemoteController
import kotlinx.coroutines.launch
import java.net.URLEncoder

private const val TAG = "TvTextBar"

/**
 * Buscar en YouTube del TV desde el celular, sin depender del teclado en pantalla del TV (que
 * no avisa cuando se enfoca un cuadro de texto y no acepta teclas sueltas).
 *
 * - Tocar la barra abre al instante el teclado del celular; al buscar, el TV abre YouTube
 *   directamente en los resultados.
 * - El micrófono (o el micrófono de la pantalla del TV) abre directo el reconocimiento de voz
 *   del celular y busca lo dictado.
 */
@Composable
internal fun TvTextBar(controller: RemoteController?, onResult: (Result<Unit>) -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val url = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(q, "UTF-8")
        Log.d(TAG, "Buscando en YouTube del TV: $url")
        scope.launch {
            val result = controller?.launchApp(url) ?: Result.failure(IllegalStateException("Dispositivo no configurado"))
            Log.d(TAG, "Resultado de la búsqueda: $result")
            onResult(result)
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        listening = false
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        Log.d(TAG, "Voz: resultCode=${result.resultCode} texto=$spoken")
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrBlank()) {
            text = spoken
            search(spoken)
        }
    }

    fun startVoice() {
        if (listening) return
        listening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Decí qué querés buscar")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            listening = false
            Log.e(TAG, "Sin reconocimiento de voz", e)
            onResult(Result.failure(IllegalStateException("Este celular no tiene reconocimiento de voz disponible")))
        }
    }

    // Tocaron el micrófono en la pantalla del TV: el celular escucha en su lugar.
    LaunchedEffect(controller) {
        controller?.imeEvents?.collect { event ->
            Log.d(TAG, "Evento del TV: $event")
            if (event is ImeEvent.VoiceRequested) startVoice()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Buscar en YouTube del TV") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search(text) }),
            trailingIcon = {
                IconButton(onClick = { search(text) }) {
                    Icon(Icons.Filled.Search, contentDescription = "Buscar")
                }
            },
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { startVoice() }) {
            Icon(Icons.Filled.Mic, contentDescription = "Hablar")
        }
    }
}
