package com.example.youroom.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.youroom.domain.SearchFilters
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.youRoomSettings by preferencesDataStore(name = "youroom_settings")

class SettingsStore(context: Context) {
    private val store = context.youRoomSettings
    private val building = stringPreferencesKey("building")
    private val favorites = booleanPreferencesKey("favorites_only")
    private val quiet = booleanPreferencesKey("quiet_only")
    private val outlets = booleanPreferencesKey("outlets_only")
    private val capacity = intPreferencesKey("minimum_capacity")

    val filters = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { values ->
        SearchFilters(building = values[building], favoritesOnly = values[favorites] ?: false,
            quietOnly = values[quiet] ?: false, outletsOnly = values[outlets] ?: false,
            minimumCapacity = values[capacity] ?: 0)
    }

    suspend fun save(filters: SearchFilters) {
        store.edit { values ->
            filters.building?.let { values[building] = it } ?: values.remove(building)
            values[favorites] = filters.favoritesOnly
            values[quiet] = filters.quietOnly
            values[outlets] = filters.outletsOnly
            values[capacity] = filters.minimumCapacity
        }
    }
}
