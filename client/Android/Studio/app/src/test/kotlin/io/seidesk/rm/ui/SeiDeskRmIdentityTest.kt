package io.seidesk.rm.ui

import io.seidesk.rm.XySessionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeiDeskRmIdentityTest {

    @Test
    fun brandConstantsMatchOfficialIdentity() {
        assertEquals("SeiDesk RM", AppBrand.APP_NAME)
        assertEquals("io.seidesk.rm", AppBrand.APPLICATION_ID)
        assertEquals("XyVerse Technology Global", AppBrand.PUBLISHER)
    }

    @Test
    fun englishLanguageIsDefaultAndConsistent() {
        assertEquals(XyLang.EN, XyText.current())
        assertEquals("Android RDP client for Windows and Windows Server.", XyText.t("app.tagline"))
        assertEquals("Devices", XyText.t("home.devices"))
        assertEquals("Thanks for using SeiDesk RM.", XyText.t("about.thanks"))
        assertTrue(XyText.t("about.license.body").contains("SeiDesk RM"))
    }

    @Test
    fun hudLayoutExportUsesSeiDeskRmBrand() {
        val exported = HudKey.exportLayoutJson(emptyList())
        assertTrue(exported.contains("\"app\":\"SeiDesk RM\""))
        assertTrue(exported.contains("\"version\":1"))
    }

    @Test
    fun actionStringsUseNewNamespace() {
        assertEquals("io.seidesk.rm.action.STOP_SESSION", XySessionService.ACTION_STOP)
        assertEquals("io.seidesk.rm.action.PING_SESSION", XySessionService.ACTION_PING)
        assertEquals("io.seidesk.rm.action.GO_HOME", XySessionService.ACTION_HOME)
        assertEquals("io.seidesk.rm.action.MUSIC_TOGGLE", XySessionService.ACTION_MUSIC)
    }
}
