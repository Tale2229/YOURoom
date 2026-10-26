package com.example.youroom.data.local

import com.example.youroom.domain.*
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Local, versioned snapshots. No credentials, account names, notes or entire school response. */
internal object PlanningHistoryCodec {
    fun read(text: String?): List<PlanningHistoryRecord> = runCatching {
        Json.parseToJsonElement(text ?: "[]").jsonArray.mapNotNull { item ->
            runCatching { decode(item.jsonObject) }.getOrNull()
        }
    }.getOrDefault(emptyList())

    fun write(records: List<PlanningHistoryRecord>) = JsonArray(records.map(::encode)).toString()
    private fun JsonObject.s(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.i(key: String) = getValue(key).jsonPrimitive.int
    private fun JsonObject.b(key: String) = getValue(key).jsonPrimitive.boolean
    private fun windows(values: List<StudyWindow>) = JsonArray(values.map { JsonArray(listOf(JsonPrimitive(it.start), JsonPrimitive(it.end))) })
    private fun JsonElement.windows() = jsonArray.map { StudyWindow(it.jsonArray[0].jsonPrimitive.int, it.jsonArray[1].jsonPrimitive.int) }
    private fun room(value: Classroom) = buildJsonObject {
        put("id", value.id); put("name", value.name); put("building", value.building)
        value.capacity?.let { put("capacity", it) }
    }
    private fun JsonElement.room(): Classroom = jsonObject.let {
        Classroom(it.s("id"), it.s("name"), it.s("building"), it["capacity"]?.jsonPrimitive?.intOrNull)
    }
    private fun plan(value: StudyPlan) = buildJsonObject {
        put("windows", windows(value.windows)); put("window", windows(listOf(value.window)))
        put("metrics", JsonArray(listOf(value.studyMinutes, value.favoriteMinutes, value.changes, value.buildingChanges,
            value.walkingMinutes, value.approachMinutes, value.lostMinutes, value.deviation, value.score,
            value.freshnessPenalty, value.expectedMinutes).map(::JsonPrimitive)))
        put("steps", JsonArray(value.steps.map { step -> buildJsonObject {
            put("start", step.start); put("end", step.end)
            when (step) {
                is PlanningStep.Study -> { put("kind", "study"); put("room", room(step.classroom)); put("favorite", step.favorite) }
                is PlanningStep.Transfer -> { put("kind", "transfer"); put("from", room(step.from)); put("to", room(step.to)); put("walking", step.walkingMinutes) }
                is PlanningStep.Break -> put("kind", "break")
            }
        } }))
    }
    private fun JsonElement.plan(): StudyPlan {
        val p = jsonObject
        val m = p.getValue("metrics").jsonArray.map { it.jsonPrimitive.int }
        require(m.size == 11)
        val steps = p.getValue("steps").jsonArray.map { element ->
            val s = element.jsonObject
            when (s.s("kind")) {
                "study" -> PlanningStep.Study(s.i("start"), s.i("end"), s.getValue("room").room(), s.b("favorite"))
                "transfer" -> PlanningStep.Transfer(s.i("start"), s.i("end"), s.getValue("from").room(), s.getValue("to").room(), s.i("walking"))
                "break" -> PlanningStep.Break(s.i("start"), s.i("end"))
                else -> error("Unknown history step")
            }
        }
        return StudyPlan(p.getValue("window").windows().single(), steps, m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8],
            p.getValue("windows").windows(), m[9], m[10])
    }
    private fun encode(value: PlanningHistoryRecord) = buildJsonObject {
        put("v", 1); put("id", value.id); put("owner", value.owner); put("completed", value.completedAt.toString())
        put("date", value.date.toString()); put("campus", value.campus.name)
        put("selected", value.selectedOption); put("direction", value.defaultDirection.name)
        value.inputKey?.let { put("inputKey",it) }
        put("goal", buildJsonObject {
            put("start", value.goal.start); put("end", value.goal.end); put("flexible", value.goal.flexible)
            value.goal.origin?.let { put("origin", it) }
            put("saved", windows(value.goal.savedWindows)); put("expected", JsonArray(value.goal.expectedRooms.sorted().map(::JsonPrimitive)))
        })
        put("fetched", value.snapshot.fetchedAt.toString())
        put("slots", JsonArray(value.snapshot.slots.map { slot -> buildJsonObject {
            put("number", slot.number); put("start", slot.start.toString()); put("end", slot.end.toString())
            put("rooms", JsonArray(slot.classrooms.map(::room)))
        } }))
        put("options", JsonArray(value.result.options.map { option -> buildJsonObject {
            put("label", option.label); put("plan", plan(option.plan))
        } }))
        value.result.message?.let { put("message", it) }
        put("groups", JsonArray(value.result.groups.map { group -> buildJsonObject {
            put("direction", group.direction.name); put("indices", JsonArray(group.optionIndices.map(::JsonPrimitive)))
            group.message?.let { put("message", it) }
        } }))
    }
    private fun decode(p: JsonObject): PlanningHistoryRecord {
        require(p.i("v") == 1 && p.s("owner").startsWith("account:"))
        val campus = Campus.valueOf(p.s("campus")); val date = LocalDate.parse(p.s("date"))
        val g = p.getValue("goal").jsonObject
        val goal = PlanningGoal(g.i("start"), g.i("end"), g.b("flexible"), g["origin"]?.jsonPrimitive?.content,
            g.getValue("saved").windows(), g.getValue("expected").jsonArray.map { it.jsonPrimitive.content }.toSet())
        val slots = p.getValue("slots").jsonArray.map { it.jsonObject.let { s ->
            LessonSlot(s.i("number"), LocalTime.parse(s.s("start")), LocalTime.parse(s.s("end")), s.getValue("rooms").jsonArray.map { it.room() })
        } }
        val options = p.getValue("options").jsonArray.map { it.jsonObject.let { o -> PlanningOption(o.s("label"), o.getValue("plan").plan()) } }
        val groups = p.getValue("groups").jsonArray.map { it.jsonObject.let { g ->
            PlanningGroup(PlanningDirection.valueOf(g.s("direction")), g.getValue("indices").jsonArray.map { it.jsonPrimitive.int }, g["message"]?.jsonPrimitive?.content)
        } }
        require(groups.all { group -> group.optionIndices.all { it in options.indices } })
        return PlanningHistoryRecord(p.s("id"), p.s("owner"), Instant.parse(p.s("completed")), date, campus, goal,
            ClassroomSnapshot(campus.dataKey, date, Instant.parse(p.s("fetched")), slots),
            PlanningResult(options, p["message"]?.jsonPrimitive?.content, groups = groups), p.i("selected"), PlanningDirection.valueOf(p.s("direction")),
            p["inputKey"]?.jsonPrimitive?.content?.takeIf { it.matches(Regex("[0-9a-f]{64}")) })
    }
}
