package io.seidesk.rm.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Result of checking remote host reachability.
 */
data class ReachabilityResult(
    val online: Boolean,
    val latencyMs: Long = -1L,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Probes host port reachability off the main thread with 1.5s timeout,
 * caching results for 30 seconds to minimize network traffic.
 */
object ReachabilityProbe {
    private val cache = ConcurrentHashMap<String, ReachabilityResult>()
    private const val CACHE_TTL_MS = 30_000L

    suspend fun check(host: String, port: Int): ReachabilityResult = withContext(Dispatchers.IO) {
        val key = "$host:$port"
        val cached = cache[key]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.timestamp < CACHE_TTL_MS) {
            return@withContext cached
        }

        val start = System.currentTimeMillis()
        val online = runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 1500)
                true
            }
        }.getOrDefault(false)

        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
        val res = ReachabilityResult(
            online = online,
            latencyMs = if (online) elapsed else -1L,
            timestamp = now,
        )
        cache[key] = res
        res
    }

    fun getCached(host: String, port: Int): ReachabilityResult? {
        val key = "$host:$port"
        val cached = cache[key]
        val now = System.currentTimeMillis()
        return if (cached != null && now - cached.timestamp < CACHE_TTL_MS) cached else null
    }
}
