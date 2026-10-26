package com.example.youroom.data

import android.content.Context
import com.example.youroom.domain.LeisureItem
import com.example.youroom.domain.PoetryCorpus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Immutable app content is loaded once off the UI thread, independently of user data. */
internal object OfflinePoetry {
    private val lock = Mutex()
    private var content: List<LeisureItem>? = null

    suspend fun load(context: Context): List<LeisureItem> = withContext(Dispatchers.IO) {
        lock.withLock {
            content ?: context.assets.open(PoetryCorpus.asset).bufferedReader(Charsets.UTF_8).use {
                PoetryCorpus.read(it)
            }.also { content = it }
        }
    }
}
