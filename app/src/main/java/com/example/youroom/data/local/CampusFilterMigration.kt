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
        building = values[building(campus)], favoritesOnly = values[favorites(campus)] ?: false,
        quietOnly = values[quiet(campus)] ?: false, outletsOnly = values[outlets(campus)] ?: false,
        minimumCapacity = values[capacity(campus)] ?: 0)

    fun write(values: MutablePreferences, campus: Campus, filters: SearchFilters) {
        filters.building?.let { values[building(campus)] = it } ?: values.remove(building(campus))
        values[favorites(campus)] = filters.favoritesOnly
        values[quiet(campus)] = filters.quietOnly
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
            building = values[stringPreferencesKey("building")],
            favoritesOnly = values[booleanPreferencesKey("favorites_only")] ?: false,
            quietOnly = values[booleanPreferencesKey("quiet_only")] ?: false,
            outletsOnly = values[booleanPreferencesKey("outlets_only")] ?: false,
            minimumCapacity = values[intPreferencesKey("minimum_capacity")] ?: 0)
        CampusFilterPreferences.write(values, west, legacy)
        values[CampusFilterPreferences.migrated] = true
        return values
    }
    override suspend fun cleanUp() = Unit
}
