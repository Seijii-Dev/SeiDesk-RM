package io.seidesk.rm.ui

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReachabilityProbeTest {

    @Before
    fun setup() {
        ReachabilityProbe.clearCache()
    }

    @Test
    fun reachabilityStatusEnumValues() {
        assertEquals(5, ReachabilityStatus.entries.size)
        assertTrue(ReachabilityResult(status = ReachabilityStatus.REACHABLE).online)
        assertFalse(ReachabilityResult(status = ReachabilityStatus.UNREACHABLE).online)
        assertFalse(ReachabilityResult(status = ReachabilityStatus.CHECKING).online)
        assertFalse(ReachabilityResult(status = ReachabilityStatus.FAILED).online)
        assertFalse(ReachabilityResult(status = ReachabilityStatus.UNKNOWN).online)
    }

    @Test
    fun backwardCompatibilityConstructor() {
        val onlineResult = ReachabilityResult(online = true, latencyMs = 24L)
        assertEquals(ReachabilityStatus.REACHABLE, onlineResult.status)
        assertTrue(onlineResult.online)
        assertEquals(24L, onlineResult.latencyMs)

        val offlineResult = ReachabilityResult(online = false)
        assertEquals(ReachabilityStatus.UNREACHABLE, offlineResult.status)
        assertFalse(offlineResult.online)
    }

    @Test
    fun invalidInputReturnsFailed() = runBlocking {
        val blankHost = ReachabilityProbe.check("", 3389)
        assertEquals(ReachabilityStatus.FAILED, blankHost.status)

        val invalidPort = ReachabilityProbe.check("127.0.0.1", 999999)
        assertEquals(ReachabilityStatus.FAILED, invalidPort.status)
    }

    @Test
    fun cacheEvictionWorks() {
        ReachabilityProbe.evict("10.0.0.1", 3389)
        assertEquals(null, ReachabilityProbe.getCached("10.0.0.1", 3389))
    }
}
