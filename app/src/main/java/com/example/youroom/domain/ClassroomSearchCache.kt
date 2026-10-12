package com.example.youroom.domain

data class ClassroomSearchRequest(
    val snapshot: ClassroomSnapshot,
    val filters: SearchFilters,
    val profiles: Map<String, RoomProfile>,
)

/** UI-owned, one-entry cache per campus page. Access only from the UI dispatcher. */
class ClassroomSearchCache {
    private var request: ClassroomSearchRequest? = null
    private var result: SearchResult? = null
    fun peek(input: ClassroomSearchRequest): SearchResult? {
        val previous = request ?: return null
        return result.takeIf { previous.snapshot === input.snapshot &&
            previous.filters == input.filters && previous.profiles === input.profiles }
    }
    fun put(input: ClassroomSearchRequest, value: SearchResult) {
        request = input
        result = value
    }
}
