package com.example.youroom.ui

import androidx.compose.runtime.*
import com.example.youroom.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ClassroomSearchPresentation(val result: SearchResult, val pending: Boolean)
private data class CompletedSearch(val request: ClassroomSearchRequest, val result: SearchResult)
private val EmptySearch = SearchResult(emptyList(), emptyList(), false)

@Composable
fun rememberClassroomSearch(state: YouRoomState, enabled: Boolean): ClassroomSearchPresentation {
    val snapshot = state.campusSnapshot
    val cache = remember { ClassroomSearchCache() }
    val request = remember(snapshot, state.filters, state.profiles, state.stale) {
        snapshot?.takeUnless { state.stale }
            ?.let { ClassroomSearchRequest(it, state.filters, state.profiles) }
    }
    val completed by produceState<CompletedSearch?>(null, request, enabled) {
        if (request == null || !enabled) return@produceState
        val result = cache.peek(request) ?: withContext(Dispatchers.Default) {
            ClassroomFilter.search(request.snapshot, request.filters, request.profiles)
        }.also { cache.put(request, it) }
        // Cancelled searches cannot publish a response into a newer selection/campus.
        value = CompletedSearch(request, result)
    }
    if (request == null) return ClassroomSearchPresentation(EmptySearch, false)
    val result = completed?.takeIf { it.request == request }?.result ?: cache.peek(request)
    // Retain the visible list during local filtering instead of replacing it with
    // a loading placeholder. Never retain results across snapshots or stale dates.
    val previous = completed?.takeIf { it.request.snapshot === request.snapshot }?.result
    return ClassroomSearchPresentation(result ?: previous ?: EmptySearch, result == null)
}
