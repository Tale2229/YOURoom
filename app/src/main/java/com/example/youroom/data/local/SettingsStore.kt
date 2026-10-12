package com.example.youroom.data.local

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.youroom.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

interface FilterSettings {
    val filters: Flow<SearchFilters>
    val customTags: Flow<Set<String>>
    suspend fun save(filters: SearchFilters)
    suspend fun addTag(name: String)
}

object SharedFilterPreferences {
    val migrated = booleanPreferencesKey("shared_filters_migrated_v2")
    val favorites = booleanPreferencesKey("shared.favorites")
    val outlets = booleanPreferencesKey("shared.outlets")
    val tags = stringSetPreferencesKey("shared.tags")
    val definitions = stringSetPreferencesKey("personal_tag_definitions")
    val capacity = intPreferencesKey("shared.capacity")
    fun read(values: Preferences) = SearchFilters(
        favoritesOnly = values[favorites] ?: false, outletsOnly = values[outlets] ?: false,
        tags = values[tags].orEmpty(), minimumCapacity = values[capacity] ?: 0)
    fun write(values: MutablePreferences, filters: SearchFilters) {
        values[favorites] = filters.favoritesOnly
        values[outlets] = filters.outletsOnly
        values[tags] = filters.tags
        values[capacity] = filters.minimumCapacity
    }
}

/** West-campus preferences become the shared defaults; all old keys remain intact. */
class SharedFilterMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences) = currentData[SharedFilterPreferences.migrated] != true
    override suspend fun migrate(currentData: Preferences): Preferences {
        val values = currentData.toMutablePreferences()
        val old = CampusFilterPreferences.read(values, Campus.XITUCHENG)
        SharedFilterPreferences.write(values, old)
        values[SharedFilterPreferences.definitions] = values[SharedFilterPreferences.definitions].orEmpty() + old.tags
        values[SharedFilterPreferences.migrated] = true
        return values
    }
    override suspend fun cleanUp() = Unit
}

private val Context.youRoomSettings by preferencesDataStore(name = "youroom_settings",
    produceMigrations = { listOf(CampusFilterMigration(), SharedFilterMigration()) })

class SettingsStore(context: Context) : FilterSettings {
    private val store = context.youRoomSettings
    private val values = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }
    override val filters = values.map(SharedFilterPreferences::read)
    override val customTags = values.map { it[SharedFilterPreferences.definitions].orEmpty() }
    override suspend fun save(filters: SearchFilters) {
        store.edit { SharedFilterPreferences.write(it, filters) }
    }
    override suspend fun addTag(name: String) {
        store.edit { it[SharedFilterPreferences.definitions] = it[SharedFilterPreferences.definitions].orEmpty() + name }
    }
}
