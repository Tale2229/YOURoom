package com.example.youroom.domain

/** All selected teaching periods must be free. Breaks are deliberately not inferred. */
object ClassroomFilter {
    fun search(
        snapshot: ClassroomSnapshot,
        filters: SearchFilters,
        profiles: Map<String, RoomProfile>,
    ): SearchResult {
        val numbers = filters.selectedSlots
        if (numbers.isEmpty()) return SearchResult(emptyList(), emptyList(), false)
        if (numbers.any { it <= 0 }) return SearchResult(emptyList(), emptyList(), false, true)
        val selected = snapshot.slots.filter { it.number in numbers }.sortedBy { it.number }
        if (selected.map { it.number }.toSet() != numbers) {
            return SearchResult(emptyList(), selected, false, true)
        }
        val common = selected.map { slot -> slot.classrooms.map { it.id }.toSet() }
            .reduce { first, next -> first intersect next }
        val query = filters.query.trim()
        val matches = snapshot.classrooms.asSequence()
            .filter { it.id in common }
            .map { room ->
                RoomMatch(room, profiles[room.id] ?: room.emptyProfile(),
                    snapshot.slots.filter { slot -> slot.classrooms.any { it.id == room.id } }
                        .map { it.number }.toSet())
            }
            .filter { !it.profile.excluded }
            .filter { filters.building == null || it.classroom.building == filters.building }
            .filter { query.isEmpty() || it.classroom.name.contains(query, true) || it.profile.note.contains(query, true) }
            .filter { !filters.favoritesOnly || it.profile.favorite }
            .filter { !filters.quietOnly || it.profile.quiet }
            .filter { !filters.outletsOnly || it.profile.outlets }
            .filter { filters.minimumCapacity == 0 || (it.classroom.capacity ?: 0) >= filters.minimumCapacity }
            .sortedWith(compareByDescending<RoomMatch> { it.profile.favorite }
                .thenBy { it.classroom.building.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.classroom.name })
            .toList()
        return SearchResult(matches, selected, selected.zipWithNext().any { (a, b) -> a.end < b.start })
    }
}
