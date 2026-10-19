package com.rnd.remoto.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "devices_store")

class DeviceRepository(private val context: Context) {

    private val key = stringPreferencesKey("devices_json")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(RemoteDevice.serializer())

    val devices: Flow<List<RemoteDevice>> = context.dataStore.data.map { prefs ->
        val raw = prefs[key] ?: return@map emptyList()
        runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    suspend fun saveDevice(device: RemoteDevice) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString(serializer, it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = current.filterNot { it.id == device.id } + device
            prefs[key] = json.encodeToString(serializer, updated)
        }
    }

    /** Read-modify-write sobre el estado guardado actual, para que callbacks de larga vida
     * (token nuevo, IP nueva) no pisen cambios hechos después con una copia vieja del dispositivo. */
    suspend fun updateDevice(id: String, transform: (RemoteDevice) -> RemoteDevice) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString(serializer, it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = current.map { if (it.id == id) transform(it) else it }
            prefs[key] = json.encodeToString(serializer, updated)
        }
    }

    suspend fun deleteDevice(id: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString(serializer, it) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[key] = json.encodeToString(serializer, current.filterNot { it.id == id })
        }
    }
}
