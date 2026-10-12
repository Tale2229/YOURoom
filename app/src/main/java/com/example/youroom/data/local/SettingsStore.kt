package com.example.youroom.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.youroom.domain.Campus
import com.example.youroom.domain.SearchFilters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

interface FilterSettings {
    val filters: Flow<Map<Campus, SearchFilters>>
    suspend fun save(campus: Campus, filters: SearchFilters)
}

private val Context.youRoomSettings by preferencesDataStore(
    name = "youroom_settings", produceMigrations = { listOf(CampusFilterMigration()) })

class SettingsStore(context: Context) : FilterSettings {
    private val store = context.youRoomSettings

    override val filters = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { values -> Campus.entries.associateWith { CampusFilterPreferences.read(values, it) } }

    override suspend fun save(campus: Campus, filters: SearchFilters) {
        store.edit { CampusFilterPreferences.write(it, campus, filters) }
    }
}
