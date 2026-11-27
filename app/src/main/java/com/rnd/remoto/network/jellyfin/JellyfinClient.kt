package com.rnd.remoto.network.jellyfin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val TAG = "JellyfinClient"
private const val DISCOVERY_PORT = 7359
private const val DISCOVERY_MESSAGE = "Who is JellyfinServer?"

data class JellyfinServerInfo(
    val name: String,
    val address: String,
    val id: String
)

data class JellyfinAuthResult(
    val serverUrl: String,
    val serverName: String,
    val userId: String,
    val username: String,
    val accessToken: String
)

data class JellyfinMediaItem(
    val id: String,
    val name: String,
    val type: String, // "Movie", "Series", "Episode", "Season"
    val seriesName: String? = null,
    val seriesId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val productionYear: Int? = null,
    val overview: String? = null,
    val runTimeTicks: Long = 0L,
    val playbackPositionTicks: Long = 0L,
    val playedPercentage: Double = 0.0,
    val hasPrimaryImage: Boolean = true
) {
    val displayTitle: String
        get() = when (type) {
            "Episode" -> seriesName ?: name
            else -> name
        }

    val displaySubtitle: String
        get() = when (type) {
            "Episode" -> {
                val se = if (seasonNumber != null && episodeNumber != null) {
                    "T$seasonNumber:E$episodeNumber · "
                } else ""
                "$se$name"
            }
            "Series" -> listOfNotNull("Serie", productionYear?.toString()).joinToString(" · ")
            "Movie" -> listOfNotNull("Película", productionYear?.toString()).joinToString(" · ")
            else -> productionYear?.toString().orEmpty()
        }

    /** ID del item del cual conviene mostrar el póster (para capítulos, el póster de la serie o del capítulo). */
    val posterItemId: String
        get() = if (type == "Episode" && !seriesId.isNullOrBlank()) seriesId else id
}

data class JellyfinSessionInfo(
    val id: String,
    val client: String,
    val deviceName: String,
    val userName: String?,
    val supportsRemoteControl: Boolean,
    val nowPlayingItemId: String? = null,
    val nowPlayingItemName: String? = null,
    val isPaused: Boolean = false,
    val positionTicks: Long = 0L,
    val isActive: Boolean = false
)

/**
 * Cliente para el servidor Jellyfin:
 * - Auto-descubrimiento en la red Wi-Fi local por UDP 7359.
 * - Inicio de sesión con usuario y contraseña.
 * - Consulta de Continuar viendo, Próximo capítulo (Next Up), Recién agregados, Películas, Series,
 *   Temporadas/Capítulos y Búsqueda directa.
 * - Envío de orden de reproducción directa al TV Box (/Sessions/{id}/Playing).
 */
class JellyfinClient(
    val baseUrl: String,
    val userId: String,
    val accessToken: String,
    private val deviceId: String = "control-remoto-android"
) {
    private val normalizedUrl = baseUrl.trimEnd('/')

    private fun authHeader(): String =
        "MediaBrowser Client=\"ControlRemotoAndroid\", Device=\"AndroidPhone\", DeviceId=\"$deviceId\", Version=\"1.0.0\", Token=\"$accessToken\""

    private fun getJson(path: String): JSONObject {
        val req = Request.Builder()
            .url("$normalizedUrl$path")
            .header("X-Emby-Authorization", authHeader())
            .header("Accept", "application/json")
            .get()
            .build()
        sharedHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Jellyfin respondió HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            return if (body.isBlank()) JSONObject() else JSONObject(body)
        }
    }

    private fun getJsonArray(path: String): JSONArray {
        val req = Request.Builder()
            .url("$normalizedUrl$path")
            .header("X-Emby-Authorization", authHeader())
            .header("Accept", "application/json")
            .get()
            .build()
        sharedHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Jellyfin respondió HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            return if (body.isBlank()) JSONArray() else JSONArray(body)
        }
    }

    private fun postCommand(path: String, jsonBody: JSONObject? = null) {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = (jsonBody?.toString() ?: "").toRequestBody(mediaType)
        val req = Request.Builder()
            .url("$normalizedUrl$path")
            .header("X-Emby-Authorization", authHeader())
            .post(body)
            .build()
        sharedHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Error de Jellyfin (HTTP ${resp.code})")
        }
    }

    /** 1. Continuar viendo (películas y capítulos a mitad de ver). */
    suspend fun getResumeItems(limit: Int = 15): Result<List<JellyfinMediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val json = getJson(
                "/Users/$userId/Items/Resume?Limit=$limit&Recursive=true" +
                    "&Fields=Overview,PrimaryImageAspectRatio,ProductionYear" +
                    "&MediaTypes=Video"
            )
            parseItemsArray(json.optJSONArray("Items"))
        }
    }

    /** 2. Próximo capítulo de las series que estás siguiendo (Next Up). */
    suspend fun getNextUpItems(limit: Int = 15): Result<List<JellyfinMediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val json = getJson(
                "/Shows/NextUp?UserId=$userId&Limit=$limit" +
                    "&Fields=Overview,PrimaryImageAspectRatio,ProductionYear"
            )
            parseItemsArray(json.optJSONArray("Items"))
        }
    }

    /** 3. Recién agregados al servidor. */
    suspend fun getLatestItems(limit: Int = 16): Result<List<JellyfinMediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val arr = getJsonArray(
                "/Users/$userId/Items/Latest?Limit=$limit" +
                    "&Fields=Overview,PrimaryImageAspectRatio,ProductionYear" +
                    "&IncludeItemTypes=Movie,Series"
            )
            parseItemsArray(arr)
        }
    }

    /** 4. Catálogo de Películas o Series (sin colapsar en colecciones/BoxSets para que aparezcan todas las películas reales). */
    suspend fun getItemsByType(type: String, limit: Int = 80): Result<List<JellyfinMediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val json = getJson(
                "/Users/$userId/Items?IncludeItemTypes=$type&Recursive=true" +
                    "&CollapseBoxSetItems=false" +
                    "&SortBy=DateCreated,SortName&SortOrder=Descending" +
                    "&Limit=$limit&Fields=Overview,ProductionYear"
            )
            parseItemsArray(json.optJSONArray("Items")).filter { it.type == type }
        }
    }

    /** 5. Buscador directo (Películas, Series y Episodios). */
    suspend fun search(query: String, limit: Int = 40): Result<List<JellyfinMediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val json = getJson(
                "/Users/$userId/Items?searchTerm=$encoded&Recursive=true" +
                    "&CollapseBoxSetItems=false" +
                    "&IncludeItemTypes=Movie,Series,Episode&Limit=$limit" +
                    "&Fields=Overview,ProductionYear"
            )
            parseItemsArray(json.optJSONArray("Items"))
        }
    }

    /** 6. Temporadas de una Serie. */
    suspend fun getSeasons(seriesId: String): Result<List<JellyfinMediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val json = getJson("/Shows/$seriesId/Seasons?UserId=$userId")
            parseItemsArray(json.optJSONArray("Items"))
        }
    }

    /** 7. Capítulos de una Serie (o de una Temporada específica). */
    suspend fun getEpisodes(seriesId: String, seasonId: String? = null): Result<List<JellyfinMediaItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val seasonParam = if (!seasonId.isNullOrBlank()) "&SeasonId=$seasonId" else ""
            val json = getJson("/Shows/$seriesId/Episodes?UserId=$userId$seasonParam&Fields=Overview")
            parseItemsArray(json.optJSONArray("Items"))
        }
    }

    /** Obtiene los datos actualizados de un item (incluyendo su PlaybackPositionTicks real). */
    suspend fun getItem(itemId: String): Result<JellyfinMediaItem> = withContext(Dispatchers.IO) {
        runCatching {
            val obj = getJson("/Users/$userId/Items/$itemId")
            if (obj.optString("Id").isBlank()) error("Item no encontrado en Jellyfin")
            parseItem(obj)
        }
    }

    /**
     * Si el usuario toca "Reproducir ya" sobre una Serie entera, busca el próximo capítulo sin terminar
     * (o el primero de la serie) para reproducirlo directamente.
     */
    suspend fun resolvePlayableItemForSeries(seriesId: String): Result<JellyfinMediaItem> = withContext(Dispatchers.IO) {
        runCatching {
            // Primero miramos si hay algún capítulo en NextUp para esta serie
            val nextUp = getJson("/Shows/NextUp?UserId=$userId&SeriesId=$seriesId&Limit=1")
            val nextUpItems = parseItemsArray(nextUp.optJSONArray("Items"))
            if (nextUpItems.isNotEmpty()) return@runCatching nextUpItems.first()

            // Si no, buscamos los episodios de la serie y elegimos el primero con progreso o el primero
            val episodes = getEpisodes(seriesId).getOrThrow()
            episodes.firstOrNull { it.playbackPositionTicks > 0L }
                ?: episodes.firstOrNull()
                ?: error("Esta serie no tiene capítulos disponibles")
        }
    }

    /** Lista las sesiones activas que aceptan control remoto (ej. Jellyfin Android TV). */
    suspend fun getControllableSessions(): Result<List<JellyfinSessionInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val arr = getJsonArray("/Sessions")
            val list = mutableListOf<JellyfinSessionInfo>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("Id")
                if (id.isBlank()) continue
                val client = obj.optString("Client")
                val deviceName = obj.optString("DeviceName")
                val sessionDeviceId = obj.optString("DeviceId")
                // Ignorar nuestras propias sesiones de control remoto (celular/tablet)
                if (
                    sessionDeviceId == deviceId ||
                    sessionDeviceId.startsWith("control-remoto-") ||
                    client.equals("ControlRemotoAndroid", ignoreCase = true)
                ) continue
                val supportsRemote = obj.optBoolean("SupportsRemoteControl", false)
                val nowPlayingObj = obj.optJSONObject("NowPlayingItem")
                val nowPlayingId = nowPlayingObj?.optString("Id")?.takeIf { it.isNotBlank() }
                val nowPlaying = nowPlayingObj?.optString("Name")?.takeIf { it.isNotBlank() }
                val playState = obj.optJSONObject("PlayState")
                val isPaused = playState?.optBoolean("IsPaused", false) ?: false
                val positionTicks = playState?.optLong("PositionTicks", 0L) ?: 0L
                val isActive = obj.optBoolean("IsActive", false)
                val userName = obj.optString("UserName").takeIf { it.isNotBlank() }
                // Solo sesiones que realmente soporten control remoto o sean clientes de TV
                if (supportsRemote || client.contains("TV", ignoreCase = true)) {
                    list.add(
                        JellyfinSessionInfo(
                            id = id,
                            client = client,
                            deviceName = deviceName,
                            userName = userName,
                            supportsRemoteControl = supportsRemote,
                            nowPlayingItemId = nowPlayingId,
                            nowPlayingItemName = nowPlaying,
                            isPaused = isPaused,
                            positionTicks = positionTicks,
                            isActive = isActive
                        )
                    )
                }
            }
            // Priorizar Android TV / TV Box activo primero
            list.sortedByDescending {
                val score = (if (it.client.contains("Android TV", ignoreCase = true)) 4 else 0) +
                    (if (it.supportsRemoteControl) 2 else 0) +
                    (if (it.isActive) 2 else 0) +
                    (if (it.nowPlayingItemId != null) 1 else 0) +
                    (if (it.userName != null) 1 else 0)
                score
            }
        }
    }

    /**
     * Ordena reproducir [item] en el TV Box estando en cualquier pantalla o app del TV Box (ej. YouTube, Netflix, inicio o ya dentro de Jellyfin):
     * 1. Refresca la posición real guardada del item en el servidor.
     * 2. Solo abre/trae Jellyfin al frente cuando sabemos que otra app está en primer plano o no hay sesión activa
     *    (evita pasar por Play Store y reabrir Jellyfin cuando ya estás dentro de Jellyfin).
     * 3. Envía PlayNow con startPositionTicks explícito (0 para iniciar desde el principio, o la posición guardada para continuar).
     * 4. Verifica que haya arrancado el item pedido y en la posición correcta; si hace falta, despierta Jellyfin o corrige con Seek.
     */
    suspend fun playOnTvBox(
        item: JellyfinMediaItem,
        preferredDeviceName: String? = null,
        resumeFromSavedPosition: Boolean = true,
        foregroundAppPackage: String? = null,
        wakeTvApp: (suspend () -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val basePlayable = if (item.type == "Series") {
                resolvePlayableItemForSeries(item.id).getOrThrow()
            } else {
                item
            }

            val playable = getItem(basePlayable.id).getOrDefault(basePlayable)
            val targetTicks = if (resumeFromSavedPosition && playable.playbackPositionTicks > 0L) {
                playable.playbackPositionTicks
            } else {
                0L
            }

            val isJellyfinKnownForeground = foregroundAppPackage?.startsWith("org.jellyfin", ignoreCase = true) == true
            val isOtherAppKnownForeground = !foregroundAppPackage.isNullOrBlank() && !isJellyfinKnownForeground

            var wokenUp = false

            // Solo traemos Jellyfin al frente antes de enviar PlayNow si SABEMOS que hay otra app en primer plano
            if (isOtherAppKnownForeground && wakeTvApp != null) {
                Log.d(TAG, "Otra app en primer plano ($foregroundAppPackage); trayendo Jellyfin al frente...")
                wakeTvApp.invoke()
                wokenUp = true
                delay(1500)
            }

            var targetSession = findBestTvSession(preferredDeviceName)
            if (targetSession == null && wakeTvApp != null && !wokenUp) {
                Log.d(TAG, "No hay sesión activa de Jellyfin; abriendo Jellyfin en el TV Box...")
                wakeTvApp.invoke()
                wokenUp = true
                delay(1500)
                targetSession = findBestTvSession(preferredDeviceName)
            }

            if (targetSession == null) {
                // Esperamos hasta ~10 segundos a que la app de Jellyfin en el TV Box se reporte al servidor
                for (attempt in 1..15) {
                    delay(650)
                    targetSession = findBestTvSession(preferredDeviceName)
                    if (targetSession != null) break
                }
            }

            val session = targetSession
                ?: error("No se detectó Jellyfin activo en el TV Box. Revisá que la app Jellyfin esté instalada en el TV.")

            // Si este mismo título ya se está reproduciendo (o pausado en una sesión activa) en Jellyfin,
            // basta con Seek directo (+ Unpause si estaba pausado) sin reiniciar el player.
            if (!wokenUp && session.nowPlayingItemId == playable.id && (isJellyfinKnownForeground || !session.isPaused || session.isActive)) {
                sendSeek(session.id, targetTicks)
                if (session.isPaused) {
                    runCatching { postCommand("/Sessions/${session.id}/Playing/Unpause") }
                }
                return@runCatching if (targetTicks == 0L) {
                    "Reiniciando \"${playable.displayTitle}\" desde el principio en ${session.deviceName}"
                } else {
                    "Continuando \"${playable.displayTitle}\" en ${session.deviceName}"
                }
            }

            val playQuery = "playCommand=PlayNow&itemIds=${playable.id}&startPositionTicks=$targetTicks"
            postCommand("/Sessions/${session.id}/Playing?$playQuery")

            // Verificamos si arrancó a reproducir el item pedido
            var startedSession = waitForPlaybackStart(preferredDeviceName, playable.id, maxWaitMs = 2200L)

            if (startedSession == null) {
                if (!wokenUp && !isJellyfinKnownForeground && wakeTvApp != null) {
                    // foregroundApp era null y Jellyfin probablemente estaba en segundo plano
                    Log.d(TAG, "PlayNow no arrancó; trayendo Jellyfin al frente y reintentando...")
                    wakeTvApp.invoke()
                    wokenUp = true
                    delay(1800)
                } else {
                    delay(800)
                }
                val refreshed = findBestTvSession(preferredDeviceName) ?: session
                runCatching { postCommand("/Sessions/${refreshed.id}/Playing?$playQuery") }
                startedSession = waitForPlaybackStart(preferredDeviceName, playable.id, maxWaitMs = 2500L)
            }

            // Garantizar que la posición real coincida con "desde el principio" (0) o "continuar" (targetTicks)
            if (startedSession != null) {
                val checked = if (targetTicks > 0L && startedSession.positionTicks == 0L) {
                    delay(700)
                    findBestTvSession(preferredDeviceName)?.takeIf { it.nowPlayingItemId == playable.id } ?: startedSession
                } else {
                    startedSession
                }
                val currentPos = checked.positionTicks
                val needsSeek = if (targetTicks == 0L) {
                    currentPos > 80_000_000L // > 8s cuando se pidió iniciar desde 0
                } else {
                    kotlin.math.abs(currentPos - targetTicks) > 120_000_000L // desfase > 12s
                }
                if (needsSeek) {
                    Log.d(TAG, "Ajustando posición con Seek: actual=$currentPos, objetivo=$targetTicks")
                    runCatching { sendSeek(checked.id, targetTicks) }
                }
            }

            if (targetTicks == 0L) {
                "Iniciando \"${playable.displayTitle}\" desde el principio en ${session.deviceName}"
            } else {
                "Continuando \"${playable.displayTitle}\" en ${session.deviceName}"
            }
        }
    }

    private fun sendSeek(sessionId: String, positionTicks: Long) {
        postCommand("/Sessions/$sessionId/Playing/Seek?seekPositionTicks=${positionTicks.coerceAtLeast(0L)}")
    }

    private suspend fun waitForPlaybackStart(
        preferredDeviceName: String?,
        expectedItemId: String,
        maxWaitMs: Long
    ): JellyfinSessionInfo? {
        val deadline = System.currentTimeMillis() + maxWaitMs
        while (System.currentTimeMillis() < deadline) {
            delay(550)
            val s = findBestTvSession(preferredDeviceName)
            if (s != null && s.nowPlayingItemId == expectedItemId) {
                return s
            }
        }
        return null
    }

    /** Envía Playpause, Stop, NextTrack, etc. a la sesión activa de Jellyfin en el TV Box. */
    suspend fun sendPlaybackCommand(command: String, preferredDeviceName: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = findBestTvSession(preferredDeviceName)
                ?: error("No hay reproducción activa en Jellyfin del TV")
            postCommand("/Sessions/${session.id}/Playing/$command")
        }
    }

    private suspend fun findBestTvSession(preferredDeviceName: String?): JellyfinSessionInfo? {
        val sessions = getControllableSessions().getOrDefault(emptyList())
        if (sessions.isEmpty()) return null
        if (!preferredDeviceName.isNullOrBlank()) {
            sessions.firstOrNull {
                it.deviceName.equals(preferredDeviceName, ignoreCase = true) ||
                    it.deviceName.contains(preferredDeviceName, ignoreCase = true)
            }?.let { return it }
        }
        return sessions.firstOrNull { it.supportsRemoteControl } ?: sessions.firstOrNull()
    }

    /** URL para cargar el póster ligero desde el servidor Jellyfin. */
    fun posterUrl(itemId: String, maxWidth: Int = 260): String =
        "$normalizedUrl/Items/$itemId/Images/Primary?maxWidth=$maxWidth&quality=75"

    private fun parseItem(obj: JSONObject): JellyfinMediaItem {
        val userData = obj.optJSONObject("UserData")
        val imageTags = obj.optJSONObject("ImageTags")
        val hasPrimary = (imageTags != null && imageTags.has("Primary")) || obj.has("SeriesPrimaryImageTag")
        return JellyfinMediaItem(
            id = obj.optString("Id"),
            name = obj.optString("Name", "Sin título"),
            type = obj.optString("Type", "Movie"),
            seriesName = obj.optString("SeriesName").takeIf { it.isNotBlank() },
            seriesId = obj.optString("SeriesId").takeIf { it.isNotBlank() },
            seasonNumber = obj.optInt("ParentIndexNumber", -1).takeIf { it >= 0 },
            episodeNumber = obj.optInt("IndexNumber", -1).takeIf { it >= 0 },
            productionYear = obj.optInt("ProductionYear", -1).takeIf { it > 0 },
            overview = obj.optString("Overview").takeIf { it.isNotBlank() },
            runTimeTicks = obj.optLong("RunTimeTicks", 0L),
            playbackPositionTicks = userData?.optLong("PlaybackPositionTicks", 0L) ?: 0L,
            playedPercentage = userData?.optDouble("PlayedPercentage", 0.0) ?: 0.0,
            hasPrimaryImage = hasPrimary
        )
    }

    private fun parseItemsArray(arr: JSONArray?): List<JellyfinMediaItem> {
        if (arr == null) return emptyList()
        val result = mutableListOf<JellyfinMediaItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            result.add(parseItem(obj))
        }
        return result
    }

    companion object {
        private val sharedHttp = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        private val bitmapCache = LruCache<String, Bitmap>(80)

        /** Descarga y cachea en memoria una miniatura de póster de Jellyfin. */
        suspend fun loadPosterBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
            bitmapCache.get(url)?.let { return@withContext it }
            runCatching {
                val req = Request.Builder().url(url).get().build()
                sharedHttp.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val bytes = resp.body?.bytes() ?: return@use null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { bmp ->
                        bitmapCache.put(url, bmp)
                    }
                }
            }.getOrNull()
        }

        /**
         * Busca automáticamente servidores Jellyfin en la red local enviando un broadcast UDP al puerto 7359.
         */
        suspend fun discoverServers(timeoutMs: Int = 1800): List<JellyfinServerInfo> = withContext(Dispatchers.IO) {
            val found = mutableMapOf<String, JellyfinServerInfo>()
            runCatching {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.soTimeout = 450
                    val data = DISCOVERY_MESSAGE.toByteArray(Charsets.UTF_8)
                    val packet = DatagramPacket(
                        data,
                        data.size,
                        InetAddress.getByName("255.255.255.255"),
                        DISCOVERY_PORT
                    )
                    socket.send(packet)

                    val start = System.currentTimeMillis()
                    val buf = ByteArray(2048)
                    while (System.currentTimeMillis() - start < timeoutMs) {
                        val recv = DatagramPacket(buf, buf.size)
                        val received = runCatching { socket.receive(recv); true }.getOrDefault(false)
                        if (!received) continue
                        val text = String(recv.data, 0, recv.length, Charsets.UTF_8)
                        val json = runCatching { JSONObject(text) }.getOrNull() ?: continue
                        val address = json.optString("Address").trimEnd('/')
                        val name = json.optString("Name", "Jellyfin")
                        val id = json.optString("Id", address)
                        if (address.isNotBlank()) {
                            found[address] = JellyfinServerInfo(name = name, address = address, id = id)
                        }
                    }
                }
            }
            found.values.toList()
        }

        /**
         * Inicia sesión con usuario y contraseña en un servidor Jellyfin.
         * Si el usuario escribió solo la IP (ej: "192.168.1.50"), autocompleta "http://192.168.1.50:8096".
         */
        suspend fun authenticate(
            rawServerUrl: String,
            username: String,
            password: String,
            deviceId: String = "control-remoto-android"
        ): Result<JellyfinAuthResult> = withContext(Dispatchers.IO) {
            runCatching {
                val baseUrl = normalizeServerUrl(rawServerUrl)
                val authHeader =
                    "MediaBrowser Client=\"ControlRemotoAndroid\", Device=\"AndroidPhone\", DeviceId=\"$deviceId\", Version=\"1.0.0\""
                val payload = JSONObject().apply {
                    put("Username", username.trim())
                    put("Pw", password)
                }
                val req = Request.Builder()
                    .url("$baseUrl/Users/AuthenticateByName")
                    .header("X-Emby-Authorization", authHeader)
                    .header("Accept", "application/json")
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                sharedHttp.newCall(req).execute().use { resp ->
                    if (resp.code == 401) error("Usuario o contraseña de Jellyfin incorrectos")
                    if (!resp.isSuccessful) error("No se pudo conectar a Jellyfin en $baseUrl (HTTP ${resp.code})")
                    val body = JSONObject(resp.body?.string().orEmpty())
                    val token = body.optString("AccessToken")
                    val user = body.optJSONObject("User")
                    val userId = user?.optString("Id").orEmpty()
                    val resolvedUser = user?.optString("Name").orEmpty().ifBlank { username.trim() }
                    if (token.isBlank() || userId.isBlank()) error("Respuesta inválida del servidor Jellyfin")

                    val serverName = runCatching {
                        val infoReq = Request.Builder()
                            .url("$baseUrl/System/Info/Public")
                            .get()
                            .build()
                        sharedHttp.newCall(infoReq).execute().use { infoResp ->
                            JSONObject(infoResp.body?.string().orEmpty()).optString("ServerName", "Jellyfin")
                        }
                    }.getOrDefault("Jellyfin")

                    JellyfinAuthResult(
                        serverUrl = baseUrl,
                        serverName = serverName,
                        userId = userId,
                        username = resolvedUser,
                        accessToken = token
                    )
                }
            }
        }

        fun normalizeServerUrl(input: String): String {
            var clean = input.trim().trimEnd('/')
            if (clean.isEmpty()) error("Ingresá la dirección de tu servidor Jellyfin")
            if (!clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true)) {
                clean = "http://$clean"
            }
            // Si no especificó puerto ni ruta (ej. http://192.168.1.100), agregamos :8096 por defecto
            val afterScheme = clean.substringAfter("://")
            if (!afterScheme.contains(':') && !afterScheme.contains('/')) {
                clean = "$clean:8096"
            }
            return clean
        }
    }
}