package com.mf.minecraftlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherLogicTest {
    @Test fun ramSettingIsBoundedAndSnappedToSteps() {
        assertEquals(512, LauncherLogic.normalizeRamMb(100))
        assertEquals(768, LauncherLogic.normalizeRamMb(777))
        assertEquals(1024, LauncherLogic.normalizeRamMb(5000))
    }

    @Test fun acceptsNormalMinecraftServerAddresses() {
        assertTrue(LauncherLogic.isValidServerAddress("play.example.org"))
        assertTrue(LauncherLogic.isValidServerAddress("play.example.org:25565"))
        assertTrue(LauncherLogic.isValidServerAddress("192.0.2.12:25565"))
        assertTrue(LauncherLogic.isValidServerAddress("[2001:db8::1]:25565"))
        assertTrue(LauncherLogic.isValidServerAddress("localhost"))
    }

    @Test fun rejectsInvalidServerAddresses() {
        assertFalse(LauncherLogic.isValidServerAddress(""))
        assertFalse(LauncherLogic.isValidServerAddress("play example.org"))
        assertFalse(LauncherLogic.isValidServerAddress("example.org:0"))
        assertFalse(LauncherLogic.isValidServerAddress("example.org:65536"))
        assertFalse(LauncherLogic.isValidServerAddress("https://example.org"))
        assertFalse(LauncherLogic.isValidServerAddress("a..b"))
    }

    @Test fun validatesUsernamesBeforeOpeningSkinViewer() {
        assertTrue(LauncherLogic.isValidMinecraftUsername("Steve_123"))
        assertFalse(LauncherLogic.isValidMinecraftUsername("bad username"))
        assertFalse(LauncherLogic.isValidMinecraftUsername("username-that-is-too-long"))
        assertFalse(LauncherLogic.isValidMinecraftUsername("<script>"))
    }

    @Test fun manifestParserKeepsOnlyValidMojangEntries() {
        val json = """
            {
              "versions": [
                {"id":"1.21.1","type":"release","url":"https://piston-meta.mojang.com/v1/packages/a/manifest.json"},
                {"id":"24w14a","type":"snapshot","url":"https://piston-meta.mojang.com/v1/packages/b/manifest.json"},
                {"id":"evil","type":"release","url":"https://example.com/fake.json"},
                {"id":"bad id","type":"release","url":"https://piston-meta.mojang.com/v1/packages/c/manifest.json"}
              ]
            }
        """.trimIndent()
        val parsed = LauncherLogic.parseVersionManifest(json)
        assertEquals(listOf("1.21.1", "24w14a"), parsed.map { it.id })
    }
}
