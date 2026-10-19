package com.rnd.remoto.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Paquete de la app de Jellyfin en Android TV. */
internal const val JELLYFIN_PACKAGE = "org.jellyfin.androidtv"

/** Botón con los colores de Jellyfin (degradé violeta-celeste) y su logo dibujado. */
@Composable
internal fun JellyfinButton(onClick: () -> Unit) {
    val gradient = Brush.horizontalGradient(listOf(Color(0xFFAA5CC3), Color(0xFF00A4DC)))
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(32.dp))
            .background(gradient)
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        JellyfinLogo(Modifier.size(34.dp))
        Text(
            "Abrir Jellyfin",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Logo de Jellyfin simplificado: un triángulo redondeado con otro más chico adentro. */
@Composable
private fun JellyfinLogo(modifier: Modifier) {
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
