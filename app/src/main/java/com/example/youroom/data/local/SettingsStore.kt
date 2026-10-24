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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.*
import java.io.IOException

interface FilterSettings {
    val planningHistory: Flow<List<PlanningHistoryRecord>> get() = kotlinx.coroutines.flow.flowOf(emptyList())
    suspend fun savePlanningHistory(record: PlanningHistoryRecord, date: java.time.LocalDate) = Unit
    suspend fun prunePlanningHistory(date: java.time.LocalDate) = Unit
    suspend fun matchesActiveAccount(account:String):Boolean? = null
    // Unavailable by default for sources with no progress storage (including previews).
    val guideHistory: Flow<GuideHistory> get() = kotlinx.coroutines.flow.flowOf(GuideHistory())
    suspend fun guideAccountIdentity(account: String): String? = null
    suspend fun saveGuideRecord(identity: String, record: GuideRecord) = Unit
    val planningPreferences: Flow<PlanningPreferences> get() = kotlinx.coroutines.flow.flowOf(PlanningPreferences())
    suspend fun savePlanningPreferences(value: PlanningPreferences) { error("Planning preference storage is unavailable") }
    val appearance: Flow<AppearancePreferences> get() = kotlinx.coroutines.flow.flowOf(AppearancePreferences())
    suspend fun saveAppearance(value: AppearancePreferences) { error("Appearance storage is unavailable") }
    val periodStrip: Flow<PeriodStripPreferences>
    suspend fun savePeriodStrip(value: PeriodStripPreferences)
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
        tags = values[tags].orEmpty())
    fun write(values: MutablePreferences, filters: SearchFilters) {
        values[favorites] = filters.favoritesOnly
        values[outlets] = filters.outletsOnly
        values[tags] = filters.tags
        // The capacity control was retired; do not retain an invisible constraint.
        values.remove(capacity)
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

private val Context.yourRoomSettings by preferencesDataStore(name = "youroom_settings",
    produceMigrations = { listOf(CampusFilterMigration(), SharedFilterMigration(), TagCatalogMigration()) })

class SettingsStore internal constructor(private val store: androidx.datastore.core.DataStore<Preferences>) : FilterSettings {
    constructor(context: Context) : this(context.yourRoomSettings)
    private val values = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }
    private val historyKey = stringPreferencesKey("planning.history.v1")
    override val planningHistory = values.map { it[historyKey] }.distinctUntilChanged()
        .map { PlanningHistoryCodec.read(it) }.flowOn(Dispatchers.Default)
    override suspend fun savePlanningHistory(record: PlanningHistoryRecord, date: java.time.LocalDate) {
        require(record.owner.startsWith("account:"))
        store.edit { it[historyKey] = PlanningHistoryCodec.write(PlanningHistoryPolicy.current(
            listOf(record) + PlanningHistoryCodec.read(it[historyKey]), date)) }
    }
    override suspend fun prunePlanningHistory(date: java.time.LocalDate) {
        store.edit {
            val records = PlanningHistoryCodec.read(it[historyKey])
            val current = PlanningHistoryPolicy.current(records, date)
            if (records != current) it[historyKey] = PlanningHistoryCodec.write(current)
        }
    }
    override val guideHistory = values.map(GuidePreferenceKeys::read).distinctUntilChanged()
    override suspend fun matchesActiveAccount(account:String):Boolean? = GuidePreferenceKeys.matchesActiveAccount(values.first(),account)
    override suspend fun guideAccountIdentity(account: String): String {
        var identity = ""
        store.edit { identity = GuidePreferenceKeys.account(it, account) }
        return identity
    }
    override suspend fun saveGuideRecord(identity: String, record: GuideRecord) {
        store.edit { GuidePreferenceKeys.write(it, identity, record) }
    }
    override val filters = values.map(SharedFilterPreferences::read).distinctUntilChanged()
    override val planningPreferences = values.map(PlanningPreferenceKeys::read).distinctUntilChanged()
    override suspend fun savePlanningPreferences(value: PlanningPreferences) {
        store.edit { PlanningPreferenceKeys.write(it, value) }
    }
    override val appearance = values.map(AppearancePreferenceKeys::read).distinctUntilChanged()
    override suspend fun saveAppearance(value: AppearancePreferences) {
        store.edit { AppearancePreferenceKeys.write(it, value) }
    }
    override val periodStrip = values.map(PeriodStripPreferenceKeys::read).distinctUntilChanged()
    override suspend fun savePeriodStrip(value: PeriodStripPreferences) {
        store.edit { PeriodStripPreferenceKeys.write(it,value) }
    }
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

object AppearancePreferenceKeys {
    val west = stringPreferencesKey("appearance.01.tone")
    val shahe = stringPreferencesKey("appearance.04.tone")
    val defaultCampus = stringPreferencesKey("appearance.default_campus")
    val darkMode = booleanPreferencesKey("appearance.dark_mode")
    fun read(values: Preferences) = AppearancePreferences(
        CampusTone.entries.firstOrNull { it.id == values[west] } ?: CampusTone.SAND,
        CampusTone.entries.firstOrNull { it.id == values[shahe] } ?: CampusTone.PINE,
        Campus.entries.firstOrNull { it.dataKey == values[defaultCampus] } ?: Campus.XITUCHENG,
        values[darkMode])
    fun write(values: MutablePreferences, appearance: AppearancePreferences) {
        values[west] = appearance.westTone.id
        values[shahe] = appearance.shaheTone.id
        values[defaultCampus] = appearance.defaultCampus.dataKey
        appearance.darkMode?.let { values[darkMode] = it } ?: values.remove(darkMode)
    }
}

object PeriodStripPreferenceKeys {
    val layout=stringPreferencesKey("display.period_strip.layout")
    val numbers=booleanPreferencesKey("display.period_strip.numbers")
    fun read(values: Preferences)=PeriodStripPreferences(
        PeriodStripLayout.entries.firstOrNull { it.name==values[layout] } ?: PeriodStripLayout.EQUAL,
        values[numbers] ?: false)
    fun write(values: MutablePreferences,value: PeriodStripPreferences) {
        values[layout]=value.layout.name
        values[numbers]=value.showNumbers
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
