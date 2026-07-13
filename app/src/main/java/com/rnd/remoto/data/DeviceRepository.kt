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

    suspend fun deleteDevice(id: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString(serializer, it) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[key] = json.encodeToString(serializer, current.filterNot { it.id == id })
        }
    }
}
