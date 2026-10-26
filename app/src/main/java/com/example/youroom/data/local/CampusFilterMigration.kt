package com.example.youroom.data.local

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.*
import com.example.youroom.domain.Campus
import com.example.youroom.domain.SearchFilters

object CampusFilterPreferences {
    val migrated = booleanPreferencesKey("campus_filters_migrated_v1")
    private fun prefix(campus: Campus) = "filter.${campus.dataKey}."
    fun building(campus: Campus) = stringPreferencesKey(prefix(campus) + "building")
    fun favorites(campus: Campus) = booleanPreferencesKey(prefix(campus) + "favorites_only")
    fun quiet(campus: Campus) = booleanPreferencesKey(prefix(campus) + "quiet_only")
    fun outlets(campus: Campus) = booleanPreferencesKey(prefix(campus) + "outlets_only")
    fun capacity(campus: Campus) = intPreferencesKey(prefix(campus) + "minimum_capacity")

    fun read(values: Preferences, campus: Campus) = SearchFilters(
        buildings = values[building(campus)]?.let { setOf(it) }.orEmpty(), favoritesOnly = values[favorites(campus)] ?: false,
        tags = if (values[quiet(campus)] == true) setOf("安静") else emptySet(), outletsOnly = values[outlets(campus)] ?: false,
        minimumCapacity = values[capacity(campus)] ?: 0)

    fun write(values: MutablePreferences, campus: Campus, filters: SearchFilters) {
        filters.buildings.singleOrNull()?.let { values[building(campus)] = it } ?: values.remove(building(campus))
        values[favorites(campus)] = filters.favoritesOnly
        values[quiet(campus)] = "安静" in filters.tags
        values[outlets(campus)] = filters.outletsOnly
        values[capacity(campus)] = filters.minimumCapacity
    }
}

/** Preserve legacy keys for rollback; copy into west-campus keys exactly once. */
class CampusFilterMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences) = currentData[CampusFilterPreferences.migrated] != true
    override suspend fun migrate(currentData: Preferences): Preferences {
        val values = currentData.toMutablePreferences()
        val west = Campus.XITUCHENG
        val legacy = SearchFilters(
            buildings = values[stringPreferencesKey("building")]?.let { setOf(it) }.orEmpty(),
            favoritesOnly = values[booleanPreferencesKey("favorites_only")] ?: false,
            tags = if (values[booleanPreferencesKey("quiet_only")] == true) setOf("安静") else emptySet(),
            outletsOnly = values[booleanPreferencesKey("outlets_only")] ?: false,
            minimumCapacity = values[intPreferencesKey("minimum_capacity")] ?: 0)
        CampusFilterPreferences.write(values, west, legacy)
        values[CampusFilterPreferences.migrated] = true
        return values
    }
    override suspend fun cleanUp() = Unit
}
