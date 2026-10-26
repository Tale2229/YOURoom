package com.example.youroom.data

import com.example.youroom.data.local.FilterSettings
import com.example.youroom.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.*

/** Synthetic-only adapter. It has no reference to the app container, database,
 * credential store or HTTP client, so a guided action cannot reach real data. */
internal class GuideData(val clock: Clock) : ClassroomDataSource {
    override val profiles = MutableStateFlow<Map<String,RoomProfile>>(emptyMap())
    override suspend fun hasSession() = true
    override suspend fun login(account: String, password: String) = error("Guide has no login")
    override suspend fun logout() = Unit
    override suspend fun cached(campus: Campus) = snapshot(campus)
    override suspend fun refresh(campus: Campus) = snapshot(campus)
    override suspend fun saveProfile(profile: RoomProfile) { profiles.value += profile.roomId to profile }
    override suspend fun deleteProfile(roomId: String) { profiles.value -= roomId }

    fun snapshot(campus: Campus): ClassroomSnapshot {
        val source = DemoData.snapshot(campus,clock)
        val rooms = source.classrooms
        return source.copy(slots = source.slots.mapIndexed { index, slot -> slot.copy(classrooms =
            rooms.filterIndexed { room, _ -> when(room) {
                // Three full-day alternatives keep the real portfolio search on
                // its bounded direct-route path instead of an expensive demo graph.
                0,1,2 -> true
                else -> (room + index) % 3 != 0
            } }) })
    }

    companion object {
        fun clock(date: LocalDate = LocalDate.now(SchoolZone)): Clock = Clock.fixed(
            date.atTime(10,0).atZone(SchoolZone).toInstant(),SchoolZone)
        val windows get() = listOf(StudyWindow(660,720),StudyWindow(840,960),StudyWindow(1140,1230))
        fun preferences() = PlanningPreferences(Campus.entries.associateWith {
            CampusPlanningPreferences(templates = listOf(
                TimeTemplate("guide","指引练习",windows),
                TimeTemplate("guide-weekday","工作日",listOf(StudyWindow(540,720),StudyWindow(840,1020))),
                TimeTemplate("guide-weekend","周末",listOf(StudyWindow(705,840)))))
        })
    }
}

internal class GuideSettings(appearance: AppearancePreferences, strip: PeriodStripPreferences) : FilterSettings {
    override val appearance = MutableStateFlow(appearance)
    override val periodStrip = MutableStateFlow(strip)
    override val planningPreferences = MutableStateFlow(GuideData.preferences())
    override val filters = MutableStateFlow(SearchFilters())
    override val catalog = MutableStateFlow(TagCatalog())
    override val buildings = MutableStateFlow(Campus.entries.associateWith { emptySet<String>() })
    override suspend fun saveAppearance(value: AppearancePreferences) { appearance.value=value }
    override suspend fun savePeriodStrip(value: PeriodStripPreferences) { periodStrip.value=value }
    override suspend fun savePlanningPreferences(value: PlanningPreferences) { planningPreferences.value=value }
    override suspend fun save(filters: SearchFilters) { this.filters.value=filters }
    override suspend fun saveBuildings(campus: Campus, names: Set<String>) { buildings.value+=campus to names }
    override suspend fun addTag(name: String) { catalog.value=catalog.value.added(name) }
    override suspend fun removeTag(name: String) { catalog.value=catalog.value.deleted(name) }
    override suspend fun reorderTags(names: List<String>) { catalog.value=catalog.value.reordered(names) }
}
