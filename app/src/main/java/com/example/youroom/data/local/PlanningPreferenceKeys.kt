package com.example.youroom.data.local

import androidx.datastore.preferences.core.*
import com.example.youroom.domain.*
import kotlinx.serialization.json.*

internal object PlanningPreferenceKeys {
    fun direction(campus: Campus) = stringPreferencesKey("planning.${campus.dataKey}.direction")
    fun flexible(campus: Campus) = booleanPreferencesKey("planning.${campus.dataKey}.flexible")
    fun templates(campus: Campus) = stringPreferencesKey("planning.${campus.dataKey}.templates")
    fun read(values: Preferences) = PlanningPreferences(Campus.entries.associateWith { campus ->
        val stored = runCatching {
            Json.parseToJsonElement(values[templates(campus)] ?: "[]").jsonArray.mapNotNull { element -> runCatching {
                val item = element.jsonObject
                val id = item.getValue("id").jsonPrimitive.content
                val name = item.getValue("name").jsonPrimitive.content.trim()
                val windows = item.getValue("windows").jsonArray.map { range -> range.jsonArray.let {
                    require(it.size == 2); StudyWindow(it[0].jsonPrimitive.int, it[1].jsonPrimitive.int)
                } }
                require(id.isNotBlank() && name.isNotEmpty() && name.length <= 20 && TimeTemplates.valid(windows))
                TimeTemplate(id, name, windows.sortedBy { it.start })
            }.getOrNull() }.distinctBy { it.id }
        }.getOrDefault(emptyList())
        CampusPlanningPreferences(PlanningDirection.entries.firstOrNull { it.name == values[direction(campus)] }
            ?: PlanningDirection.BALANCED, values[flexible(campus)] ?: true, stored)
    })
    fun write(values: MutablePreferences, preferences: PlanningPreferences) {
        Campus.entries.forEach { campus ->
            val pref = preferences.forCampus(campus)
            values[direction(campus)] = pref.direction.name
            values[flexible(campus)] = pref.flexible
            values[templates(campus)] = JsonArray(pref.templates.map { template ->
                require(template.id.isNotBlank() && template.name.isNotBlank() && template.name.length <= 20 && TimeTemplates.valid(template.windows))
                buildJsonObject {
                    put("id", template.id); put("name", template.name.trim())
                    put("windows", JsonArray(template.windows.sortedBy { it.start }.map {
                        JsonArray(listOf(JsonPrimitive(it.start), JsonPrimitive(it.end)))
                    }))
                }
            }).toString()
        }
    }
}
