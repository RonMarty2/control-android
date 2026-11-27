package com.rnd.remoto.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Paquete de la app de Jellyfin en Android TV. */
internal const val JELLYFIN_PACKAGE = "org.jellyfin.androidtv"

data class TvAppInfo(
    val packageName: String,
    val name: String,
    val primaryColor: Color,
    val secondaryColor: Color = primaryColor,
    val badgeText: String
)

/** Catálogo de apps populares en Android TV / TV Box. */
internal val KNOWN_TV_APPS: List<TvAppInfo> = listOf(
    TvAppInfo(
        packageName = "com.netflix.ninja",
        name = "Netflix",
        primaryColor = Color(0xFFE50914),
        secondaryColor = Color(0xFFB20710),
        badgeText = "N"
    ),
    TvAppInfo(
        packageName = "com.google.android.youtube.tv",
        name = "YouTube",
        primaryColor = Color(0xFFFF0000),
        secondaryColor = Color(0xFFCC0000),
        badgeText = "▶"
    ),
    TvAppInfo(
        packageName = JELLYFIN_PACKAGE,
        name = "Jellyfin",
        primaryColor = Color(0xFFAA5CC3),
        secondaryColor = Color(0xFF00A4DC),
        badgeText = "JF"
    ),
    TvAppInfo(
        packageName = "com.disney.disneyplus",
        name = "Disney+",
        primaryColor = Color(0xFF113CCF),
        secondaryColor = Color(0xFF0A1E6E),
        badgeText = "D+"
    ),
    TvAppInfo(
        packageName = "com.amazon.amazonvideo.livingroom",
        name = "Prime Video",
        primaryColor = Color(0xFF00A8E1),
        secondaryColor = Color(0xFF007399),
        badgeText = "PV"
    ),
    TvAppInfo(
        packageName = "com.wbd.stream",
        name = "Max",
        primaryColor = Color(0xFF002BE7),
        secondaryColor = Color(0xFF001A8C),
        badgeText = "M"
    ),
    TvAppInfo(
        packageName = "com.spotify.tv.android",
        name = "Spotify",
        primaryColor = Color(0xFF1DB954),
        secondaryColor = Color(0xFF14833B),
        badgeText = "♪"
    ),
    TvAppInfo(
        packageName = "com.teamsmart.videomanager.tv",
        name = "SmartTube",
        primaryColor = Color(0xFFD32F2F),
        secondaryColor = Color(0xFF9A0007),
        badgeText = "ST"
    ),
    TvAppInfo(
        packageName = "com.stremio.one",
        name = "Stremio",
        primaryColor = Color(0xFF8A5AAB),
        secondaryColor = Color(0xFF5D3A7A),
        badgeText = "S"
    ),
    TvAppInfo(
        packageName = "org.xbmc.kodi",
        name = "Kodi",
        primaryColor = Color(0xFF17B2E7),
        secondaryColor = Color(0xFF0F7CA1),
        badgeText = "K"
    ),
    TvAppInfo(
        packageName = "tv.pluto.android",
        name = "Pluto TV",
        primaryColor = Color(0xFF222222),
        secondaryColor = Color(0xFFE5A00D),
        badgeText = "P"
    ),
    TvAppInfo(
        packageName = "tv.twitch.android.app",
        name = "Twitch",
        primaryColor = Color(0xFF9146FF),
        secondaryColor = Color(0xFF6421C8),
        badgeText = "Tw"
    ),
    TvAppInfo(
        packageName = "com.crunchyroll.crunchyroid",
        name = "Crunchyroll",
        primaryColor = Color(0xFFF47521),
        secondaryColor = Color(0xFFC85A11),
        badgeText = "CR"
    ),
    TvAppInfo(
        packageName = "com.google.android.youtube.tvkids",
        name = "YT Kids",
        primaryColor = Color(0xFFFF3D00),
        secondaryColor = Color(0xFFDD2C00),
        badgeText = "YK"
    ),
    TvAppInfo(
        packageName = "ar.tvplayer.tv",
        name = "TiviMate",
        primaryColor = Color(0xFF1E88E5),
        secondaryColor = Color(0xFF1565C0),
        badgeText = "TV"
    ),
    TvAppInfo(
        packageName = "com.plexapp.android",
        name = "Plex",
        primaryColor = Color(0xFFE5A00D),
        secondaryColor = Color(0xFFB37B00),
        badgeText = "Px"
    )
)

/** Paquetes del sistema del TV Box que no son apps para mostrar como botón. */
internal val IGNORED_SYSTEM_PACKAGES = setOf(
    "com.google.android.tvlauncher",
    "com.google.android.apps.tv.launcherx",
    "com.android.vending",
    "com.android.tv.settings",
    "com.google.android.inputmethod.latin",
    "com.google.android.katniss",
    "com.android.systemui"
)

internal fun appInfoFor(packageName: String): TvAppInfo {
    KNOWN_TV_APPS.firstOrNull { it.packageName == packageName }?.let { return it }
    val cleanName = packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    return TvAppInfo(
        packageName = packageName,
        name = cleanName.ifBlank { packageName },
        primaryColor = Color(0xFF455A64),
        secondaryColor = Color(0xFF263238),
        badgeText = cleanName.take(2).uppercase()
    )
}

/**
 * Fila horizontal de botones dedicados a abrir Netflix, YouTube, Jellyfin, Disney+ y cualquier
 * otra app sincronizada o agregada del TV Box.
 */
@Composable
internal fun TvAppsShortcutBar(
    favoritePackages: List<String>,
    discoveredPackages: List<String>,
    activePackage: String?,
    onOpenApp: (String) -> Unit,
    onUpdateFavorites: (List<String>) -> Unit
) {
    var showManageDialog by remember { mutableStateOf(false) }
    val displayedApps = remember(favoritePackages) {
        favoritePackages.ifEmpty {
            listOf(
                "com.netflix.ninja",
                "com.google.android.youtube.tv",
                JELLYFIN_PACKAGE,
                "com.disney.disneyplus"
            )
        }.map { appInfoFor(it) }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Apps del TV Box",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (activePackage != null && activePackage !in IGNORED_SYSTEM_PACKAGES) {
                    val activeApp = appInfoFor(activePackage)
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "En pantalla: ${activeApp.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            TextButton(onClick = { showManageDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Más apps / Sincronizar", style = MaterialTheme.typography.labelMedium)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            displayedApps.forEach { app ->
                TvAppButton(
                    app = app,
                    isActive = activePackage == app.packageName,
                    onClick = { onOpenApp(app.packageName) }
                )
            }
        }
    }

    if (showManageDialog) {
        ManageTvAppsDialog(
            favoritePackages = favoritePackages,
            discoveredPackages = discoveredPackages,
            onDismiss = { showManageDialog = false },
            onSave = { updated ->
                onUpdateFavorites(updated)
                showManageDialog = false
            }
        )
    }
}

@Composable
private fun TvAppButton(
    app: TvAppInfo,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val gradient = Brush.horizontalGradient(listOf(app.primaryColor, app.secondaryColor))
    val borderMod = if (isActive) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
    } else Modifier

    Row(
        modifier = borderMod
            .clip(RoundedCornerShape(24.dp))
            .background(gradient)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (app.packageName == JELLYFIN_PACKAGE) {
            JellyfinLogo(Modifier.size(22.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = app.badgeText,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp
                )
            }
        }
        Text(
            text = app.name,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ManageTvAppsDialog(
    favoritePackages: List<String>,
    discoveredPackages: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var selected by remember { mutableStateOf(favoritePackages.toList()) }
    var customPackage by remember { mutableStateOf("") }

    // Unimos las apps del catálogo + cualquier app que el TV Box haya reportado abierta (auto-sincronizada)
    val allAvailablePackages = remember(discoveredPackages, selected) {
        (KNOWN_TV_APPS.map { it.packageName } + discoveredPackages + selected)
            .distinct()
            .filterNot { it in IGNORED_SYSTEM_PACKAGES }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apps del TV Box") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Marcá las apps que querés tener como botón directo en tu control. Además, cada vez que abras una app nueva en el TV Box, aparecerá sincronizada acá automáticamente.",
                    style = MaterialTheme.typography.bodySmall
                )

                if (discoveredPackages.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                "Detectadas en tu TV Box: ${discoveredPackages.size}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(allAvailablePackages, key = { it }) { pkg ->
                        val info = appInfoFor(pkg)
                        val isChecked = pkg in selected
                        val isDiscovered = pkg in discoveredPackages
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selected = if (isChecked) selected - pkg else selected + pkg
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + pkg else selected - pkg
                                }
                            )
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(info.primaryColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(info.badgeText, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(info.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (isDiscovered) "$pkg · Detectada en TV" else pkg,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = customPackage,
                        onValueChange = { customPackage = it },
                        label = { Text("Otra app (ej: com.magistv.app)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        enabled = customPackage.contains('.'),
                        onClick = {
                            val pkg = customPackage.trim()
                            if (pkg.isNotEmpty() && pkg !in selected) {
                                selected = selected + pkg
                                customPackage = ""
                            }
                        }
                    ) {
                        Text("Añadir")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected) }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/** Logo de Jellyfin simplificado: un triángulo redondeado con otro más chico adentro. */
@Composable
internal fun JellyfinLogo(modifier: Modifier) {
    Canvas(modifier) {
        fun triangle(scale: Float): Path {
            val w = size.width
            val h = size.height
            return Path().apply {
                moveTo(w * 0.5f, h * (0.5f - 0.48f * scale))
                lineTo(w * (0.5f + 0.5f * scale), h * (0.5f + 0.4f * scale))
                lineTo(w * (0.5f - 0.5f * scale), h * (0.5f + 0.4f * scale))
                close()
            }
        }
        drawPath(triangle(0.88f), Color.White, style = Stroke(width = size.width * 0.12f, join = StrokeJoin.Round))
        drawPath(triangle(0.42f), Color.White, style = Fill)
    }
}
