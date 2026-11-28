package com.rnd.remoto.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rnd.remoto.data.DeviceRepository
import com.rnd.remoto.data.RemoteDevice
import com.rnd.remoto.network.RemoteController
import com.rnd.remoto.network.jellyfin.JellyfinClient
import com.rnd.remoto.network.jellyfin.JellyfinMediaItem
import com.rnd.remoto.network.jellyfin.JellyfinServerInfo
import com.rnd.remoto.network.jellyfin.JellyfinSessionInfo
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class JellyfinFilter {
    TODO,
    PELICULAS,
    SERIES
}

/**
 * Pestaña completa de Jellyfin integrada en el control remoto:
 * - Si no se configuró el servidor, lo auto-descubre en la red Wi-Fi y permite iniciar sesión.
 * - Muestra barra de búsqueda (por texto o voz), Continuar viendo, Próximo capítulo, Recién agregado,
 *   Películas y Series.
 * - Tocar cualquier película o capítulo lo reproduce directamente en el TV Box (y si la app de Jellyfin
 *   estaba cerrada en el TV Box, la abre sola primero y luego inicia la reproducción).
 * - Tocar una Serie permite reproducir el siguiente capítulo al instante o elegir Temporada y Capítulo.
 */
@Composable
internal fun JellyfinTabContent(
    device: RemoteDevice,
    repository: DeviceRepository,
    controller: RemoteController?,
    onMessage: (String) -> Unit
) {
    val hasCredentials = !device.jellyfinUrl.isNullOrBlank() &&
        !device.jellyfinUserId.isNullOrBlank() &&
        !device.jellyfinAccessToken.isNullOrBlank()

    if (!hasCredentials) {
        JellyfinSetupCard(
            device = device,
            repository = repository,
            onMessage = onMessage
        )
        return
    }

    val client = remember(device.jellyfinUrl, device.jellyfinUserId, device.jellyfinAccessToken, device.id) {
        JellyfinClient(
            baseUrl = device.jellyfinUrl!!,
            userId = device.jellyfinUserId!!,
            accessToken = device.jellyfinAccessToken!!,
            deviceId = "control-remoto-${device.id}"
        )
    }

    JellyfinBrowserView(
        device = device,
        client = client,
        repository = repository,
        controller = controller,
        onMessage = onMessage
    )
}

@Composable
private fun JellyfinSetupCard(
    device: RemoteDevice,
    repository: DeviceRepository,
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf(device.jellyfinUrl ?: device.ip?.let { "http://$it:8096" }.orEmpty()) }
    var username by remember { mutableStateOf(device.jellyfinUsername.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var isDiscovering by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var discoveredServers by remember { mutableStateOf<List<JellyfinServerInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        isDiscovering = true
        val found = JellyfinClient.discoverServers()
        discoveredServers = found
        if (found.isNotEmpty() && serverUrl.isBlank()) {
            serverUrl = found.first().address
        }
        isDiscovering = false
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(Color(0xFFAA5CC3), Color(0xFF00A4DC)))),
                    contentAlignment = Alignment.Center
                ) {
                    JellyfinLogo(Modifier.size(24.dp))
                }
                Column {
                    Text(
                        "Conectar tu Jellyfin",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Mirá Continuar viendo, Recién agregado y buscá desde el control para reproducir directo en el TV Box.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (discoveredServers.isNotEmpty()) {
                Text(
                    "Servidores encontrados en tu Wi-Fi:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    discoveredServers.forEach { srv ->
                        FilledTonalButton(onClick = { serverUrl = srv.address }) {
                            Text("${srv.name} (${srv.address})")
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("IP o URL de Jellyfin (ej: 192.168.1.50)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    enabled = !isDiscovering,
                    onClick = {
                        scope.launch {
                            isDiscovering = true
                            val found = JellyfinClient.discoverServers()
                            discoveredServers = found
                            if (found.isNotEmpty()) {
                                serverUrl = found.first().address
                                onMessage("Servidor encontrado: ${found.first().name}")
                            } else {
                                onMessage("No se detectó el servidor automáticamente, escribí su IP")
                            }
                            isDiscovering = false
                        }
                    }
                ) {
                    if (isDiscovering) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.WifiFind, contentDescription = "Buscar servidor en Wi-Fi")
                    }
                }
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuario de Jellyfin") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña (dejá vacío si no tiene)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                enabled = !isLoggingIn && serverUrl.isNotBlank() && username.isNotBlank(),
                onClick = {
                    scope.launch {
                        isLoggingIn = true
                        val res = JellyfinClient.authenticate(
                            rawServerUrl = serverUrl,
                            username = username,
                            password = password,
                            deviceId = "control-remoto-${device.id}"
                        )
                        isLoggingIn = false
                        res.onSuccess { auth ->
                            repository.updateDevice(device.id) {
                                it.copy(
                                    jellyfinUrl = auth.serverUrl,
                                    jellyfinServerName = auth.serverName,
                                    jellyfinUserId = auth.userId,
                                    jellyfinUsername = auth.username,
                                    jellyfinAccessToken = auth.accessToken
                                )
                            }
                            onMessage("Conectado a ${auth.serverName} como ${auth.username}")
                        }.onFailure { err ->
                            onMessage(err.message ?: "No se pudo conectar a Jellyfin")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isLoggingIn) "Conectando..." else "Vincular Jellyfin")
            }
        }
    }
}

@Composable
private fun JellyfinBrowserView(
    device: RemoteDevice,
    client: JellyfinClient,
    repository: DeviceRepository,
    controller: RemoteController?,
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    var resumeItems by remember { mutableStateOf<List<JellyfinMediaItem>>(emptyList()) }
    var nextUpItems by remember { mutableStateOf<List<JellyfinMediaItem>>(emptyList()) }
    var latestItems by remember { mutableStateOf<List<JellyfinMediaItem>>(emptyList()) }
    var moviesItems by remember { mutableStateOf<List<JellyfinMediaItem>>(emptyList()) }
    var seriesItems by remember { mutableStateOf<List<JellyfinMediaItem>>(emptyList()) }
    var activeSessions by remember { mutableStateOf<List<JellyfinSessionInfo>>(emptyList()) }

    var isLoading by remember { mutableStateOf(false) }
    var isSendingPlay by remember { mutableStateOf(false) }
    var isCheckingItem by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(JellyfinFilter.TODO) }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<JellyfinMediaItem>?>(null) }
    var isSearching by remember { mutableStateOf(false) }

    var selectedSeriesForEpisodes by remember { mutableStateOf<JellyfinMediaItem?>(null) }
    var selectedItemForResumePrompt by remember { mutableStateOf<JellyfinMediaItem?>(null) }

    fun loadAll() {
        scope.launch {
            isLoading = true
            val resumeDef = async { client.getResumeItems() }
            val nextUpDef = async { client.getNextUpItems() }
            val latestDef = async { client.getLatestItems() }
            val moviesDef = async { client.getItemsByType("Movie", limit = 80) }
            val seriesDef = async { client.getItemsByType("Series", limit = 80) }
            val sessionsDef = async { client.getControllableSessions() }

            val moviesRes = moviesDef.await()
            val resumeRes = resumeDef.await()
            // Si el token expiró (401) porque se inició sesión en otro equipo con el mismo DeviceId previo
            val authFailed = listOf(moviesRes, resumeRes).any {
                it.exceptionOrNull()?.message?.contains("401") == true
            }
            if (authFailed) {
                isLoading = false
                onMessage("Tu sesión de Jellyfin venció. Volvé a vincular tu cuenta.")
                repository.updateDevice(device.id) {
                    it.copy(jellyfinAccessToken = null, jellyfinUserId = null)
                }
                return@launch
            }

            resumeItems = resumeRes.getOrDefault(emptyList())
            nextUpItems = nextUpDef.await().getOrDefault(emptyList())
            latestItems = latestDef.await().getOrDefault(emptyList())
            moviesItems = moviesRes.getOrDefault(emptyList())
            seriesItems = seriesDef.await().getOrDefault(emptyList())
            activeSessions = sessionsDef.await().getOrDefault(emptyList())
            isLoading = false
        }
    }

    LaunchedEffect(client) {
        loadAll()
    }

    // Búsqueda automática a medida que escribís o cambiás el filtro (con pequeño debounce de 350ms)
    LaunchedEffect(searchQuery, filter, client) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            searchResults = null
            isSearching = false
            return@LaunchedEffect
        }
        val includeTypes = when (filter) {
            JellyfinFilter.TODO -> "Movie,Series,Episode"
            JellyfinFilter.PELICULAS -> "Movie"
            JellyfinFilter.SERIES -> "Series"
        }
        isSearching = true
        delay(350)
        searchResults = client.search(q, includeItemTypes = includeTypes).getOrDefault(emptyList())
        isSearching = false
    }

    fun playItemOnTv(item: JellyfinMediaItem, resume: Boolean = true) {
        if (isSendingPlay) return
        scope.launch {
            isSendingPlay = true
            val fgApp = controller?.currentForegroundApp?.value
            val isOtherAppKnown = !fgApp.isNullOrBlank() && !fgApp.startsWith("org.jellyfin", ignoreCase = true)
            onMessage(
                when {
                    isOtherAppKnown -> "Abriendo Jellyfin y reproduciendo \"${item.displayTitle}\"..."
                    resume && item.playbackPositionTicks > 0L -> "Continuando \"${item.displayTitle}\" en TV Box..."
                    !resume -> "Iniciando \"${item.displayTitle}\" desde el principio..."
                    else -> "Reproduciendo \"${item.displayTitle}\" en TV Box..."
                }
            )
            val result = client.playOnTvBox(
                item = item,
                preferredDeviceName = device.name,
                resumeFromSavedPosition = resume,
                foregroundAppPackage = fgApp,
                wakeTvApp = {
                    controller?.openApp(JELLYFIN_PACKAGE)
                }
            )
            isSendingPlay = false
            result.onSuccess { msg ->
                onMessage(msg)
                delay(1500)
                activeSessions = client.getControllableSessions().getOrDefault(emptyList())
                resumeItems = client.getResumeItems().getOrDefault(resumeItems)
            }.onFailure { err ->
                onMessage(err.message ?: "No se pudo reproducir en el TV Box")
            }
        }
    }

    /**
     * Lógica inteligente al tocar un título:
     * - Si es Serie: abre el selector de temporadas/capítulos (donde puede Continuar o elegir desde S01E01).
     * - Si es Película o Capítulo: consulta la posición actual en el servidor y, si tiene progreso guardado,
     *   pregunta si quiere "Continuar desde donde lo dejó" o "Iniciar desde el principio".
     * - Si no tiene progreso (0% visto): lo reproduce directamente desde el inicio sin preguntar.
     */
    fun onSelectPlayableItem(item: JellyfinMediaItem) {
        if (item.type == "Series") {
            selectedSeriesForEpisodes = item
            return
        }
        if (isSendingPlay || isCheckingItem) return
        scope.launch {
            isCheckingItem = true
            val fresh = client.getItem(item.id).getOrDefault(item)
            isCheckingItem = false
            if (fresh.playbackPositionTicks > 0L) {
                selectedItemForResumePrompt = fresh
            } else {
                playItemOnTv(fresh, resume = false)
            }
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrBlank()) {
            searchQuery = spoken
        }
    }

    fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Decí qué película o serie querés ver en Jellyfin")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            onMessage("Este celular no tiene reconocimiento de voz disponible")
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Cabecera del servidor conectado + reproductor en curso si el TV Box está reproduciendo algo
        val activePlaying = activeSessions.firstOrNull { !it.nowPlayingItemName.isNullOrBlank() }
        if (activePlaying != null) {
            NowPlayingBanner(
                session = activePlaying,
                onPlayPause = {
                    scope.launch {
                        client.sendPlaybackCommand("PlayPause", device.name)
                        delay(500)
                        activeSessions = client.getControllableSessions().getOrDefault(emptyList())
                    }
                },
                onStop = {
                    scope.launch {
                        client.sendPlaybackCommand("Stop", device.name)
                        delay(700)
                        activeSessions = client.getControllableSessions().getOrDefault(emptyList())
                        resumeItems = client.getResumeItems().getOrDefault(resumeItems)
                    }
                },
                onNext = {
                    scope.launch {
                        client.sendPlaybackCommand("NextTrack", device.name)
                    }
                }
            )
        }

        // Buscador compacto de 1 sola línea (no ocupa media pantalla en el Huawei)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Buscar en Jellyfin...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Limpiar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                }
            }
            FilledTonalIconButton(
                onClick = { startVoiceSearch() },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Buscar por voz en Jellyfin", modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { loadAll() },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar catálogo", modifier = Modifier.size(18.dp))
            }
        }

        // Filtros rápidos + Botón abrir app en TV / desconectar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = filter == JellyfinFilter.TODO,
                onClick = { filter = JellyfinFilter.TODO },
                label = { Text("Todo") }
            )
            FilterChip(
                selected = filter == JellyfinFilter.PELICULAS,
                onClick = { filter = JellyfinFilter.PELICULAS },
                label = { Text("Películas") }
            )
            FilterChip(
                selected = filter == JellyfinFilter.SERIES,
                onClick = { filter = JellyfinFilter.SERIES },
                label = { Text("Series") }
            )
            Spacer(Modifier.width(4.dp))
            TextButton(
                onClick = {
                    scope.launch {
                        repository.updateDevice(device.id) {
                            it.copy(
                                jellyfinAccessToken = null,
                                jellyfinUserId = null
                            )
                        }
                    }
                }
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(device.jellyfinUsername ?: "Cambiar cuenta", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (isLoading || isSendingPlay) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Resultados de búsqueda si hay texto en el buscador
        val currentSearchResults = searchResults
        if (currentSearchResults != null) {
            val filteredSearchResults = when (filter) {
                JellyfinFilter.TODO -> currentSearchResults
                JellyfinFilter.PELICULAS -> currentSearchResults.filter { it.type == "Movie" }
                JellyfinFilter.SERIES -> currentSearchResults.filter { it.type == "Series" }
            }
            val filterLabel = when (filter) {
                JellyfinFilter.TODO -> ""
                JellyfinFilter.PELICULAS -> " en Películas"
                JellyfinFilter.SERIES -> " en Series"
            }
            Text(
                text = if (isSearching) "Buscando \"$searchQuery\"$filterLabel..."
                else "Resultados$filterLabel para \"$searchQuery\" (${filteredSearchResults.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (filteredSearchResults.isEmpty() && !isSearching) {
                val emptyText = when (filter) {
                    JellyfinFilter.TODO -> "No se encontraron películas ni series con ese nombre."
                    JellyfinFilter.PELICULAS -> "No se encontraron películas con ese nombre."
                    JellyfinFilter.SERIES -> "No se encontraron series con ese nombre."
                }
                Text(
                    emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                MediaHorizontalRow(
                    items = filteredSearchResults,
                    client = client,
                    onTapItem = { item -> onSelectPlayableItem(item) },
                    onQuickPlaySeries = { series -> playItemOnTv(series) }
                )
            }
        } else {
            // Secciones principales según el filtro elegido
            when (filter) {
                JellyfinFilter.TODO -> {
                    if (resumeItems.isNotEmpty()) {
                        MediaSection(
                            title = "Continuar viendo",
                            subtitle = "Tocá para elegir Continuar o Reiniciar (o el botón ▶ para continuar directo)",
                            items = resumeItems,
                            client = client,
                            onTapItem = { onSelectPlayableItem(it) },
                            onQuickPlaySeries = { playItemOnTv(it) }
                        )
                    }
                    if (nextUpItems.isNotEmpty()) {
                        MediaSection(
                            title = "Próximo capítulo",
                            subtitle = "Series que estás siguiendo",
                            items = nextUpItems,
                            client = client,
                            onTapItem = { onSelectPlayableItem(it) },
                            onQuickPlaySeries = { playItemOnTv(it) }
                        )
                    }
                    if (latestItems.isNotEmpty()) {
                        MediaSection(
                            title = "Recién agregados",
                            subtitle = "Lo último subido a Jellyfin",
                            items = latestItems,
                            client = client,
                            onTapItem = { onSelectPlayableItem(it) },
                            onQuickPlaySeries = { playItemOnTv(it) }
                        )
                    }
                    if (moviesItems.isNotEmpty()) {
                        MediaSection(
                            title = "Películas",
                            subtitle = null,
                            items = moviesItems,
                            client = client,
                            onTapItem = { onSelectPlayableItem(it) },
                            onQuickPlaySeries = { playItemOnTv(it) }
                        )
                    }
                    if (seriesItems.isNotEmpty()) {
                        MediaSection(
                            title = "Series",
                            subtitle = "Tocá una serie para elegir capítulo o ▶ para reproducir el siguiente",
                            items = seriesItems,
                            client = client,
                            onTapItem = { selectedSeriesForEpisodes = it },
                            onQuickPlaySeries = { playItemOnTv(it) }
                        )
                    }
                }
                JellyfinFilter.PELICULAS -> {
                    MediaGridSection(
                        title = "Todas las Películas (${moviesItems.size})",
                        subtitle = "Tocá cualquier película para reproducirla directo en el TV Box",
                        items = moviesItems,
                        client = client,
                        onTapItem = { onSelectPlayableItem(it) },
                        onQuickPlaySeries = { playItemOnTv(it) }
                    )
                }
                JellyfinFilter.SERIES -> {
                    MediaGridSection(
                        title = "Todas las Series (${seriesItems.size})",
                        subtitle = "Tocá para elegir temporada/capítulo o el botón ▶ para seguir viendo",
                        items = seriesItems,
                        client = client,
                        onTapItem = { selectedSeriesForEpisodes = it },
                        onQuickPlaySeries = { playItemOnTv(it) }
                    )
                }
            }
        }
    }

    selectedItemForResumePrompt?.let { item ->
        ResumeOrRestartDialog(
            item = item,
            onDismiss = { selectedItemForResumePrompt = null },
            onResume = {
                selectedItemForResumePrompt = null
                playItemOnTv(item, resume = true)
            },
            onRestart = {
                selectedItemForResumePrompt = null
                playItemOnTv(item, resume = false)
            }
        )
    }

    selectedSeriesForEpisodes?.let { series ->
        SeriesEpisodesDialog(
            series = series,
            client = client,
            onDismiss = { selectedSeriesForEpisodes = null },
            onPlayItem = { ep, resume ->
                selectedSeriesForEpisodes = null
                playItemOnTv(ep, resume = resume)
            },
            onPlayNext = {
                selectedSeriesForEpisodes = null
                playItemOnTv(series, resume = true)
            }
        )
    }
}

@Composable
private fun NowPlayingBanner(
    session: JellyfinSessionInfo,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Reproduciendo en ${session.deviceName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = session.nowPlayingItemName.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (session.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "Play/Pausa"
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Siguiente")
                }
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = "Detener")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaGridSection(
    title: String,
    subtitle: String?,
    items: List<JellyfinMediaItem>,
    client: JellyfinClient,
    onTapItem: (JellyfinMediaItem) -> Unit,
    onQuickPlaySeries: (JellyfinMediaItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (items.isEmpty()) {
            Text(
                "Cargando catálogo...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items.forEach { item ->
                    JellyfinMediaCard(
                        item = item,
                        client = client,
                        onClick = { onTapItem(item) },
                        onQuickPlay = { onQuickPlaySeries(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaSection(
    title: String,
    subtitle: String?,
    items: List<JellyfinMediaItem>,
    client: JellyfinClient,
    onTapItem: (JellyfinMediaItem) -> Unit,
    onQuickPlaySeries: (JellyfinMediaItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        MediaHorizontalRow(
            items = items,
            client = client,
            onTapItem = onTapItem,
            onQuickPlaySeries = onQuickPlaySeries
        )
    }
}

@Composable
private fun MediaHorizontalRow(
    items: List<JellyfinMediaItem>,
    client: JellyfinClient,
    onTapItem: (JellyfinMediaItem) -> Unit,
    onQuickPlaySeries: (JellyfinMediaItem) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(items, key = { it.id }) { item ->
            JellyfinMediaCard(
                item = item,
                client = client,
                onClick = { onTapItem(item) },
                onQuickPlay = { onQuickPlaySeries(item) }
            )
        }
    }
}

@Composable
private fun JellyfinMediaCard(
    item: JellyfinMediaItem,
    client: JellyfinClient,
    onClick: () -> Unit,
    onQuickPlay: () -> Unit
) {
    val posterUrl = remember(item.posterItemId) { client.posterUrl(item.posterItemId) }
    val bitmap by produceState<Bitmap?>(initialValue = null, posterUrl) {
        value = JellyfinClient.loadPosterBitmap(posterUrl)
    }

    Card(
        modifier = Modifier
            .width(136.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(185.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF2B2D42), Color(0xFF1A1B26))
                        )
                    )
            ) {
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = item.displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (item.type == "Series" || item.type == "Episode") Icons.Default.Tv else Icons.Default.Movie,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = item.displayTitle,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Botón flotante de Play directo en la esquina inferior derecha
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00A4DC).copy(alpha = 0.92f))
                        .clickable(onClick = onQuickPlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Reproducir ahora en TV Box",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Barra de progreso si estaba a mitad de ver
                if (item.playedPercentage > 0.0) {
                    LinearProgressIndicator(
                        progress = { (item.playedPercentage.toFloat() / 100f).coerceIn(0.05f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .align(Alignment.BottomCenter),
                        color = Color(0xFFAA5CC3),
                        trackColor = Color.Black.copy(alpha = 0.6f)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = item.displayTitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.displaySubtitle.ifBlank { item.type },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatTicksToTime(ticks: Long): String {
    val totalSeconds = (ticks / 10_000_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

/**
 * Diálogo cuando el usuario toca una película o capítulo que ya tiene progreso guardado:
 * le permite elegir entre Continuar desde el minuto exacto o Iniciar desde el principio.
 */
@Composable
private fun ResumeOrRestartDialog(
    item: JellyfinMediaItem,
    onDismiss: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit
) {
    val positionText = remember(item.playbackPositionTicks) {
        formatTicksToTime(item.playbackPositionTicks)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(item.displayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (item.displaySubtitle.isNotBlank()) {
                    Text(
                        item.displaySubtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!item.overview.isNullOrBlank()) {
                    Text(
                        item.overview,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Continuar desde $positionText")
                }
                FilledTonalButton(
                    onClick = onRestart,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Iniciar desde el principio")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/**
 * Modal para elegir Temporada y Capítulo de una serie, o darle a "Reproducir siguiente capítulo".
 */
@Composable
private fun SeriesEpisodesDialog(
    series: JellyfinMediaItem,
    client: JellyfinClient,
    onDismiss: () -> Unit,
    onPlayItem: (JellyfinMediaItem, Boolean) -> Unit,
    onPlayNext: () -> Unit
) {
    var seasons by remember { mutableStateOf<List<JellyfinMediaItem>>(emptyList()) }
    var selectedSeasonId by remember { mutableStateOf<String?>(null) }
    var episodes by remember { mutableStateOf<List<JellyfinMediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(series.id) {
        isLoading = true
        val loadedSeasons = client.getSeasons(series.id).getOrDefault(emptyList())
        seasons = loadedSeasons
        val firstSeason = loadedSeasons.firstOrNull()?.id
        if (firstSeason != null) {
            selectedSeasonId = firstSeason
        } else {
            episodes = client.getEpisodes(series.id, null).getOrDefault(emptyList())
            isLoading = false
        }
    }

    LaunchedEffect(series.id, selectedSeasonId) {
        val seasonId = selectedSeasonId ?: return@LaunchedEffect
        isLoading = true
        episodes = client.getEpisodes(series.id, seasonId).getOrDefault(emptyList())
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(series.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!series.overview.isNullOrBlank()) {
                    Text(
                        series.overview,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPlayNext,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Continuar próximo capítulo en TV")
                }

                val firstEpisode = episodes.firstOrNull()
                if (firstEpisode != null) {
                    OutlinedButton(
                        onClick = { onPlayItem(firstEpisode, false) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Empezar temporada desde el Cap. 1")
                    }
                }

                if (seasons.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        seasons.forEach { season ->
                            FilterChip(
                                selected = selectedSeasonId == season.id,
                                onClick = { selectedSeasonId = season.id },
                                label = { Text(season.name) }
                            )
                        }
                    }
                }

                if (isLoading) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(episodes, key = { it.id }) { ep ->
                            val hasProgress = ep.playbackPositionTicks > 0L
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayItem(ep, true) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Reproducir",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        val epPrefix = ep.episodeNumber?.let { "E$it · " }.orEmpty()
                                        Text(
                                            text = "$epPrefix${ep.name}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (hasProgress) {
                                            Text(
                                                text = "Continuar en ${formatTicksToTime(ep.playbackPositionTicks)} (${ep.playedPercentage.toInt()}%)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else if (ep.playedPercentage >= 99.0) {
                                            Text(
                                                text = "Ya visto",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (hasProgress) {
                                        IconButton(
                                            onClick = { onPlayItem(ep, false) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Refresh,
                                                contentDescription = "Reiniciar capítulo desde el principio",
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}