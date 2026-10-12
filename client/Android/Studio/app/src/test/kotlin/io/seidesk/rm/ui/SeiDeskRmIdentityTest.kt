package io.seidesk.rm.ui

import io.seidesk.rm.SeiSessionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeiDeskRmIdentityTest {

    @Test
    fun brandConstantsMatchOfficialIdentity() {
        assertEquals("SeiDesk RM", AppBrand.APP_NAME)
        assertEquals("io.seidesk.rm", AppBrand.APPLICATION_ID)
        assertEquals("Seijixinghe888", AppBrand.PUBLISHER)
    }

    @Test
    fun englishLanguageIsDefaultAndConsistent() {
        assertEquals(XyLang.EN, SeiText.current())
        assertEquals("Android RDP client for Windows and Windows Server.", SeiText.t("app.tagline"))
        assertEquals("Devices", SeiText.t("home.devices"))
        assertEquals("Thanks for using SeiDesk RM.", SeiText.t("about.thanks"))
        assertTrue(SeiText.t("about.license.body").contains("SeiDesk RM"))
        assertTrue(SeiText.t("about.license.body").contains("JetBrains Mono"))
    }

    @Test
    fun hudLayoutExportUsesSeiDeskRmBrand() {
        val exported = HudKey.exportLayoutJson(emptyList())
        assertTrue(exported.contains("\"app\":\"SeiDesk RM\""))
        assertTrue(exported.contains("\"version\":1"))
    }

    @Test
    fun actionStringsUseNewNamespace() {
        assertEquals("io.seidesk.rm.action.STOP_SESSION", SeiSessionService.ACTION_STOP)
        assertEquals("io.seidesk.rm.action.PING_SESSION", SeiSessionService.ACTION_PING)
        assertEquals("io.seidesk.rm.action.GO_HOME", SeiSessionService.ACTION_HOME)
        assertEquals("io.seidesk.rm.action.MUSIC_TOGGLE", SeiSessionService.ACTION_MUSIC)
    }
}
