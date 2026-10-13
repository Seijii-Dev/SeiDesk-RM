package io.seidesk.rm.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

/**
 * Status of remote host / port reachability probe.
 */
enum class ReachabilityStatus {
    UNKNOWN,
    CHECKING,
    REACHABLE,
    UNREACHABLE,
    FAILED,
}

/**
 * Result of checking remote host reachability.
 */
data class ReachabilityResult(
    val status: ReachabilityStatus = ReachabilityStatus.UNKNOWN,
    val latencyMs: Long = -1L,
    val timestamp: Long = System.currentTimeMillis(),
    val errorDetail: String? = null,
) {
    val online: Boolean get() = status == ReachabilityStatus.REACHABLE

    constructor(
        online: Boolean,
        latencyMs: Long = -1L,
        timestamp: Long = System.currentTimeMillis(),
    ) : this(
        status = if (online) ReachabilityStatus.REACHABLE else ReachabilityStatus.UNREACHABLE,
        latencyMs = latencyMs,
        timestamp = timestamp,
        errorDetail = null,
    )

    companion object {
        fun unknown(): ReachabilityResult = ReachabilityResult(status = ReachabilityStatus.UNKNOWN)
        fun checking(): ReachabilityResult = ReachabilityResult(status = ReachabilityStatus.CHECKING)
    }
}

/**
 * Probes host port reachability off the main thread with 1.5s timeout,
 * caching results for 30 seconds to minimize network traffic.
 */
object ReachabilityProbe {
    private val cache = ConcurrentHashMap<String, ReachabilityResult>()
    const val CACHE_TTL_MS = 30_000L

    suspend fun check(host: String, port: Int): ReachabilityResult = withContext(Dispatchers.IO) {
        val cleanHost = host.trim()
        if (cleanHost.isBlank() || port !in 1..65535) {
            return@withContext ReachabilityResult(
                status = ReachabilityStatus.FAILED,
                errorDetail = "Invalid host or port",
            )
        }

        val key = "$cleanHost:$port"
        val cached = cache[key]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.timestamp < CACHE_TTL_MS && cached.status != ReachabilityStatus.CHECKING) {
            return@withContext cached
        }

        val start = System.currentTimeMillis()
        val result = try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(cleanHost, port), 1500)
                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                ReachabilityResult(
                    status = ReachabilityStatus.REACHABLE,
                    latencyMs = elapsed,
                    timestamp = System.currentTimeMillis(),
                )
            }
        } catch (e: UnknownHostException) {
            ReachabilityResult(
                status = ReachabilityStatus.FAILED,
                timestamp = System.currentTimeMillis(),
                errorDetail = "DNS resolution failed",
            )
        } catch (e: SocketTimeoutException) {
            ReachabilityResult(
                status = ReachabilityStatus.UNREACHABLE,
                timestamp = System.currentTimeMillis(),
                errorDetail = "Connection timed out",
            )
        } catch (e: IOException) {
            ReachabilityResult(
                status = ReachabilityStatus.UNREACHABLE,
                timestamp = System.currentTimeMillis(),
                errorDetail = e.message ?: "Unreachable",
            )
        } catch (t: Throwable) {
            ReachabilityResult(
                status = ReachabilityStatus.FAILED,
                timestamp = System.currentTimeMillis(),
                errorDetail = t.message ?: "Check failed",
            )
        }

        cache[key] = result
        result
    }

    fun getCached(host: String, port: Int): ReachabilityResult? {
        val key = "${host.trim()}:$port"
        val cached = cache[key]
        val now = System.currentTimeMillis()
        return if (cached != null && now - cached.timestamp < CACHE_TTL_MS) cached else null
    }

    fun evict(host: String, port: Int) {
        cache.remove("${host.trim()}:$port")
    }

    fun clearCache() {
        cache.clear()
    }
}
