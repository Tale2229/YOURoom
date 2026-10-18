package com.example.youroom.data

import com.example.youroom.data.local.RememberedCredentials
import com.example.youroom.data.local.SessionStore
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Single-flight recovery lives beyond an individual campus request, but never beyond logout. */
internal class SchoolSession(
    private val api: SchoolApi,
    private val store: SessionStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    internal class Lease(val token: String, val generation: Long)
    private val lock = Mutex()
    private var generation = 0L
    private var recovery: Deferred<Lease>? = null
    private var recoveryToken: String? = null
    private var recoveryStarted = 0L

    suspend fun hasSession() = withContext(Dispatchers.IO) {
        lock.withLock { !store.read().isNullOrBlank() }
    }

    suspend fun rememberedAccount() = withContext(Dispatchers.IO) {
        lock.withLock { store.credentials()?.account }
    }

    suspend fun rememberedCredentials() = withContext(Dispatchers.IO) {
        lock.withLock { store.credentials() }
    }

    suspend fun login(account: String, password: String, remember: Boolean) {
        val attempt = withContext(Dispatchers.IO) {
            lock.withLock {
                generation++
                recovery?.cancel(); recovery = null; recoveryToken = null
                store.clear()
                generation
            }
        }
        val token = api.login(account, password)
        withContext(Dispatchers.IO) {
            lock.withLock {
                current(attempt)
                currentCoroutineContext().ensureActive()
                store.saveLogin(token, if (remember) RememberedCredentials(account, password) else null)
            }
        }
    }

    suspend fun logout() = withContext(NonCancellable + Dispatchers.IO) {
        lock.withLock {
            generation++
            recovery?.cancel(); recovery = null; recoveryToken = null
            // Credentials only exist after an explicitly remembered login. Logout must
            // revoke the session without revoking that consent or starting a new login.
            store.clearToken()
        }
    }

    suspend fun lease(): Lease = withContext(Dispatchers.IO) {
        lock.withLock { Lease(store.read()?.takeIf { it.isNotBlank() } ?: throw AuthenticationRequired(), generation) }
    }

    suspend fun recover(failed: Lease): Lease {
        val deferred = withContext(Dispatchers.IO) {
            lock.withLock {
                current(failed.generation)
                val token = store.read()?.takeIf { it.isNotBlank() }
                if (token != null && token != failed.token) return@withContext CompletableDeferred(Lease(token, generation))
                val credentials = store.credentials() ?: run {
                    store.clearToken()
                    throw AuthenticationRequired()
                }
                // Failed transient attempts are shared too; avoid a burst of repeated logins.
                val existing = recovery
                if (existing != null && recoveryToken == failed.token &&
                    (!existing.isCompleted || System.nanoTime() - recoveryStarted < 30_000_000_000L)) {
                    return@withContext existing
                }
                recoveryToken = failed.token
                recoveryStarted = System.nanoTime()
                scope.async(start = CoroutineStart.LAZY) {
                    val fresh = try { api.login(credentials.account, credentials.password) }
                    catch (error: SchoolLoginRejected) {
                        lock.withLock { current(failed.generation); store.clear() }
                        throw AuthenticationRequired("自动登录未成功，已清除保存的密码。${error.userMessage}")
                    }
                    lock.withLock {
                        current(failed.generation)
                        currentCoroutineContext().ensureActive()
                        store.save(fresh)
                        Lease(fresh, generation)
                    }
                }.also { recovery = it; it.start() }
            }
        }
        return deferred.await()
    }

    suspend fun expire(lease: Lease): Nothing = withContext(Dispatchers.IO) {
        lock.withLock {
            current(lease.generation)
            val token = store.read()
            if (token != null && token != lease.token) throw CancellationException("Session superseded")
            store.clear()
            throw AuthenticationRequired("自动登录后仍无法访问，请重新登录。")
        }
    }

    suspend fun <T> withCurrent(lease: Lease, action: suspend () -> T): T = withContext(Dispatchers.IO) {
        lock.withLock {
            current(lease.generation)
            // Token replacement by another request is fine, account replacement is not.
            if (store.read().isNullOrBlank()) throw AuthenticationRequired()
            action()
        }
    }

    private fun current(expected: Long) {
        if (generation != expected) throw CancellationException("Session superseded")
    }
}
