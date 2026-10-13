package com.example.youroom.domain

/** Selected periods must all be free; no selection lists even excluded rooms for browsing. */
object ClassroomFilter {
    fun search(
        snapshot: ClassroomSnapshot,
        filters: SearchFilters,
        profiles: Map<String, RoomProfile>,
    ): SearchResult {
        val numbers = filters.selectedSlots
        if (numbers.any { it <= 0 }) return SearchResult(emptyList(), emptyList(), false, true)
        val selected = snapshot.slots.filter { it.number in numbers }.sortedBy { it.number }
        if (selected.map { it.number }.toSet() != numbers) {
            return SearchResult(emptyList(), selected, false, true)
        }
        val index = snapshot.index
        val common = if (selected.isEmpty()) index.roomsById.keys else selected
            .map { slot -> index.roomsBySlot.getValue(slot.number) }
            .reduce { first, next -> first intersect next }
        val query = filters.query.trim()
        val matches = common.asSequence().mapNotNull(index.roomsById::get)
            .map { room ->
                RoomMatch(room, profiles[room.id] ?: room.emptyProfile(), index.freeSlotsByRoom.getValue(room.id))
            }
            .filter { selected.isEmpty() || !it.profile.excluded }
            .filter { filters.buildings.isEmpty() || it.classroom.building in filters.buildings }
            .filter { query.isEmpty() || it.classroom.name.contains(query, true) }
            .filter { !filters.favoritesOnly || it.profile.favorite }
            .filter { !filters.outletsOnly || it.profile.outlets }
            .filter { it.profile.allTags.containsAll(filters.tags) }
            .filter { filters.minimumCapacity == 0 || (it.classroom.capacity ?: 0) >= filters.minimumCapacity }
            .sortedWith(compareByDescending<RoomMatch> { it.profile.favorite }
                .thenBy { it.classroom.building.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.classroom.name })
            .toList()
        return SearchResult(matches, selected, selected.zipWithNext().any { (a, b) -> a.end < b.start })
    }
}
