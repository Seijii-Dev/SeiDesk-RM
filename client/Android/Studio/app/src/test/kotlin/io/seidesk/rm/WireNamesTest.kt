package io.seidesk.rm

import io.seidesk.rm.core.WireNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WireNamesTest {

    @Test
    fun wireContractConstantsRemainIntact() {
        assertEquals("XyDesk", WireNames.ACCOUNT_MULTI_USER)
        assertEquals("XYDESK_QUIC_AUDIO_SUB_V1", WireNames.AUDIO_BRIDGE_TAG)
        assertEquals("XYGP", WireNames.GAMEPAD_MAGIC)
        assertEquals("X-XyDesk-Client-ID", WireNames.HTTP_HEADER_CLIENT_ID_LEGACY)
        assertEquals("X-SeiDesk-Client-ID", WireNames.HTTP_HEADER_CLIENT_ID)
        assertEquals("xydeskHostBuild", WireNames.JSON_FIELD_HOST_BUILD)
        assertEquals("XY-", WireNames.PC_ID_PREFIX_LEGACY)
        assertEquals("SEI-", WireNames.PC_ID_PREFIX)
        assertEquals("xydesk-quic", WireNames.NATIVE_LIB_QUIC)
        assertEquals("CABLE Input / XyDesk Virtual Microphone", WireNames.MIC_ENDPOINT_NAME)
        assertEquals("xykal/XyRDP", WireNames.TEMPLATE_REPO)
        assertEquals("SeiDesk", WireNames.REMOTE_DRIVE_NAME)
        assertEquals("XyDesk", WireNames.REMOTE_DRIVE_NAME_LEGACY)
    }

    @Test
    fun pcIdPrefixDetectionAcceptsBothOldAndNew() {
        assertTrue(WireNames.isPcId("XY-12345678"))
        assertTrue(WireNames.isPcId("xy-12345678"))
        assertTrue(WireNames.isPcId("SEI-12345678"))
        assertTrue(WireNames.isPcId("sei-12345678"))
    }
}
