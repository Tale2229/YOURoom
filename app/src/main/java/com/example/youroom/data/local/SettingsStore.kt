package com.example.youroom.data.local

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.youroom.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.*
import java.io.IOException

interface FilterSettings {
    val filters: Flow<SearchFilters>
    val catalog: Flow<TagCatalog>
    val buildings: Flow<Map<Campus, Set<String>>>
    suspend fun save(filters: SearchFilters)
    suspend fun saveBuildings(campus: Campus, names: Set<String>)
    suspend fun addTag(name: String)
    suspend fun removeTag(name: String)
    suspend fun reorderTags(names: List<String>)
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
    produceMigrations = { listOf(CampusFilterMigration(), SharedFilterMigration(), TagCatalogMigration()) })

class SettingsStore(context: Context) : FilterSettings {
    private val store = context.youRoomSettings
    private val values = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }
    override val filters = values.map(SharedFilterPreferences::read).distinctUntilChanged()
    override val catalog = values.map(TagCatalogPreferences::read).distinctUntilChanged()
    override val buildings = values.map { value -> Campus.entries.associateWith { BuildingFilterPreferences.read(value, it) } }
        .distinctUntilChanged()
    override suspend fun save(filters: SearchFilters) {
        store.edit { SharedFilterPreferences.write(it, filters) }
    }
    override suspend fun addTag(name: String) {
        store.edit { TagCatalogPreferences.write(it, TagCatalogPreferences.read(it).added(name)) }
    }
    override suspend fun saveBuildings(campus: Campus, names: Set<String>) {
        store.edit { it[BuildingFilterPreferences.key(campus)] = names }
    }
    override suspend fun removeTag(name: String) {
        if (name == PersonalTags.favorite) return
        store.edit {
            TagCatalogPreferences.write(it, TagCatalogPreferences.read(it).deleted(name))
            SharedFilterPreferences.write(it, SharedFilterPreferences.read(it).withoutTerm(name))
        }
    }
    override suspend fun reorderTags(names: List<String>) {
        store.edit { TagCatalogPreferences.write(it, TagCatalogPreferences.read(it).reordered(names)) }
    }
}

object BuildingFilterPreferences {
    fun key(campus: Campus) = stringSetPreferencesKey("filter.${campus.dataKey}.buildings")
    fun read(values: Preferences, campus: Campus) = values[key(campus)]
        ?: values[CampusFilterPreferences.building(campus)]?.let { setOf(it) }.orEmpty()
}

object TagCatalogPreferences {
    val migrated = booleanPreferencesKey("tag_catalog_migrated_v3")
    val order = stringPreferencesKey("tag_catalog_order")
    val removed = stringSetPreferencesKey("tag_catalog_removed")
    fun read(values: Preferences): TagCatalog {
        val names = values[order]?.let { encoded -> runCatching {
            Json.parseToJsonElement(encoded).jsonArray.map { it.jsonPrimitive.content }
        }.getOrNull() } ?: (PersonalTags.defaultOrder + values[SharedFilterPreferences.definitions].orEmpty().sorted())
        return TagCatalog(names, values[removed].orEmpty() - PersonalTags.favorite)
    }
    fun write(values: MutablePreferences, catalog: TagCatalog) {
        values[order] = JsonArray(catalog.available().map(::JsonPrimitive)).toString()
        values[removed] = catalog.removed - PersonalTags.favorite
    }
}

class TagCatalogMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences) = currentData[TagCatalogPreferences.migrated] != true
    override suspend fun migrate(currentData: Preferences): Preferences {
        val values = currentData.toMutablePreferences()
        TagCatalogPreferences.write(values, TagCatalogPreferences.read(values))
        Campus.entries.forEach { values[BuildingFilterPreferences.key(it)] = BuildingFilterPreferences.read(values, it) }
        values[TagCatalogPreferences.migrated] = true
        return values
    }
    override suspend fun cleanUp() = Unit
}
