package com.example.youroom.domain

/** Build once per immutable snapshot, in O(total classroom entries), not once per rendered row. */
class ClassroomIndex(slots: List<LessonSlot>) {
    val classrooms: List<Classroom>
    val roomsById: Map<String, Classroom>
    val roomsBySlot: Map<Int, Set<String>>
    val freeSlotsByRoom: Map<String, Set<Int>>

    init {
        val rooms = linkedMapOf<String, Classroom>()
        val bySlot = linkedMapOf<Int, MutableSet<String>>()
        val byRoom = linkedMapOf<String, MutableSet<Int>>()
        for (slot in slots) {
            val ids = bySlot.getOrPut(slot.number) { linkedSetOf() }
            for (room in slot.classrooms) {
                rooms.putIfAbsent(room.id, room)
                ids.add(room.id)
                byRoom.getOrPut(room.id) { linkedSetOf() }.add(slot.number)
            }
        }
        roomsById = rooms.toMap()
        classrooms = rooms.values.toList()
        roomsBySlot = bySlot.mapValues { (_, ids) -> ids.toSet() }
        freeSlotsByRoom = byRoom.mapValues { (_, numbers) -> numbers.toSet() }
    }
}
