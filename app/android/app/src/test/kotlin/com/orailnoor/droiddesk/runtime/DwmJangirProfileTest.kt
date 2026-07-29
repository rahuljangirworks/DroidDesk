package com.orailnoor.droiddesk.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DwmJangirProfileTest {
    @Test
    fun desktopIsAlwaysDwmJangir() {
        assertEquals(DwmJangirProfile.DESKTOP_ID, DwmJangirProfile.normalizeDesktop(null))
        assertEquals(DwmJangirProfile.DESKTOP_ID, DwmJangirProfile.normalizeDesktop("xfce4"))
    }

    @Test
    fun activePackagePlansNeverInstallXfce() {
        listOf("native", "chroot").forEach { mode ->
            val packages = DwmJangirProfile.packagePlan(mode)
            assertTrue(packages.isNotEmpty())
            assertFalse(packages.any { it.contains("xfce", ignoreCase = true) })
        }
        assertFalse(DwmJangirProfile.nativeCorePackages.contains("dwm"))
        assertFalse(DwmJangirProfile.nativeRecommendedPackages.contains("quickshell"))
    }

    @Test
    fun remoteArtifactsArePinned() {
        assertTrue(DwmJangirProfile.SOURCE_COMMIT.matches(Regex("[0-9a-f]{40}")))
        assertTrue(DwmJangirProfile.TAILSCALE_SHA256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(DwmJangirProfile.RUSTDESK_SHA256.matches(Regex("[0-9a-f]{64}")))
    }
}
