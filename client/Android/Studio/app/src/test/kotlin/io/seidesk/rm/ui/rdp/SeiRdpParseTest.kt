package io.seidesk.rm.ui.rdp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeiRdpParseTest {
    @Test
    fun `tunnel ip tanpa tailscale terbaca`() {
        val json = """
        {"akses":{"mode":"keduanya",
          "tunnel":{"status":"ok","provider":"bore","host":"bore.pub","port":12345,
                    "address":"bore.pub:12345","selftest":"ok","outside":"ok (asia)"},
          "tailscale":{"ip":""},
          "rustdesk":{"id":"147088785"}}}
        """.trimIndent()
        val a = SeiRdpClient.parseAccess(json)!!
        assertEquals("bore.pub:12345", a.tunnelAddress)
        assertEquals(12345, a.tunnelPort)
        assertEquals("ok", a.selftest)
        assertEquals("147088785", a.rustdeskId)
        assertEquals("", a.tailscaleIp)
    }

    @Test
    fun `tanpa blok akses menghasilkan null`() {
        assertNull(SeiRdpClient.parseAccess("{}"))
        assertNull(SeiRdpClient.parseAccess("bukan json"))
    }

    @Test
    fun `field hilang tidak melempar`() {
        val a = SeiRdpClient.parseAccess("""{"akses":{"mode":"tunnel"}}""")!!
        assertEquals("tunnel", a.mode)
        assertEquals("", a.tunnelAddress)
        assertEquals(0, a.tunnelPort)
    }
}
