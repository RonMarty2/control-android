package com.rnd.remoto.ir

import android.content.Context
import android.util.Log

private const val TAG = "IrDatabase"

/**
 * One power code from the bundled database plus the rest of the remote's buttons that share its
 * address, so finding the power code also yields a usable full remote.
 *
 * @param flags B = TV box / decoder, T = television, U = unknown device type
 */
class IrEntry(
    val code: String,
    val flags: String,
    val popularity: Int,
    val brands: List<String>,
    val extras: Map<String, String>
)

enum class IrScope(val label: String) {
    BOX("TV Box / decodificador"),
    TV("Televisor"),
    ALL("Todo")
}

/** Loads every `ir_power_codes*.txt` asset (see tools used to build them in the README). */
object IrDatabase {
    @Volatile private var cache: List<IrEntry>? = null

    fun load(context: Context): List<IrEntry> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val result = LinkedHashMap<String, IrEntry>()
            val assets = context.applicationContext.assets
            val files = assets.list("")?.filter { it.startsWith("ir_power_codes") && it.endsWith(".txt") }.orEmpty().sorted()
            for (file in files) {
                runCatching {
                    assets.open(file).bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.forEach { line -> parseLine(line)?.let { result.putIfAbsent(it.code, it) } }
                    }
                }.onFailure { Log.e(TAG, "No se pudo leer $file", it) }
            }
            // Códigos genéricos de cajas Android verificados a mano: aseguran que estén aunque
            // los archivos de datos no los traigan.
            for (legacy in CommonPowerCodes.GENERIC_TV_BOX) {
                val code = IrCodec.legacyToNec(legacy) ?: continue
                result.putIfAbsent(code, IrEntry(code, "B", 100, listOf("Genérico TV Box"), emptyMap()))
            }
            return result.values.toList().also { cache = it }
        }
    }

    private fun parseLine(line: String): IrEntry? {
        if (line.isBlank() || line.startsWith("#")) return null
        val p = line.split("|")
        if (p.size < 5) return null
        val extras = p[4].split(";").mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()
        return IrEntry(
            code = p[2],
            flags = p[0],
            popularity = p[1].toIntOrNull() ?: 0,
            brands = p[3].split(";").filter { it.isNotBlank() },
            extras = extras
        )
    }

    /** Codes to try, most likely first, for the given device scope and optional brand text. */
    fun candidates(context: Context, scope: IrScope, brandFilter: String): List<IrEntry> {
        val all = load(context)
        val brand = brandFilter.trim().lowercase()
        val filtered = if (brand.isEmpty()) all else all.filter { e -> e.brands.any { it.lowercase().contains(brand) } }

        fun rank(e: IrEntry): Int = when (scope) {
            IrScope.BOX -> if ('B' in e.flags) 0 else if ('U' in e.flags) 1 else 2
            IrScope.TV -> if ('T' in e.flags) 0 else if ('U' in e.flags) 1 else 2
            IrScope.ALL -> 0
        }
        return filtered.sortedWith(compareBy<IrEntry> { rank(it) }.thenByDescending { it.popularity })
    }
}
