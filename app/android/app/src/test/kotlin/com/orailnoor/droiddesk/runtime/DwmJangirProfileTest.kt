package com.orailnoor.droiddesk.runtime

import org.junit.Test
import java.io.File
import java.nio.file.Files
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
        assertTrue(DwmJangirProfile.nativeCorePackages.contains("xkeyboard-config"))
        assertTrue(DwmJangirProfile.nativeCorePackages.contains("libxcursor"))
        assertTrue(DwmJangirProfile.nativeCorePackages.contains("quickshell"))
        assertFalse(DwmJangirProfile.nativeRecommendedPackages.contains("quickshell"))
    }

    @Test
    fun remoteArtifactsArePinned() {
        assertTrue(DwmJangirProfile.SOURCE_COMMIT.matches(Regex("[0-9a-f]{40}")))
        assertTrue(DwmJangirProfile.TAILSCALE_SHA256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(DwmJangirProfile.RUSTDESK_SHA256.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun nativeRuntimeRejectsNonArm64PrimaryAbi() {
        assertTrue(DwmJangirProfile.supportsNativeAbi(listOf("arm64-v8a", "armeabi-v7a")))
        assertFalse(DwmJangirProfile.supportsNativeAbi(listOf("x86_64", "x86")))
        assertFalse(DwmJangirProfile.supportsNativeAbi(emptyList()))
    }

    @Test
    fun profileInstallIsVersionedIdempotentAndPreservesUserToml() {
        val root = Files.createTempDirectory("droiddesk-dwm-profile").toFile()
        try {
            val source = File(root, "source")
            val config = File(source, "config")
            File(config, "quickshell").mkdirs()
            File(config, "hotkeys.toml").writeText("source-hotkeys\n")
            File(config, "themes.toml").writeText("source-themes\n")
            File(config, "window-rules.toml").writeText("source-rules\n")
            File(config, "quickshell/shell.qml").writeText("managed-v1\n")
            File(source, "scripts").mkdirs()
            File(source, "scripts/dwm-status").apply {
                writeText("#!/bin/sh\n")
                setExecutable(true)
            }
            File(source, "dwm").apply {
                writeText("binary\n")
                setExecutable(true)
            }

            val home = File(root, "home")
            val bin = File(root, "prefix/bin")
            assertTrue(DwmJangirProfile.install(source, home, bin, "/prefix"))
            assertTrue(DwmJangirProfile.isInstalled(home, bin))

            val hotkeys = File(home, ".config/dwm-titus/hotkeys.toml")
            hotkeys.writeText("user-hotkeys\n")
            File(config, "quickshell/shell.qml").writeText("managed-v2\n")
            assertTrue(DwmJangirProfile.install(source, home, bin, "/prefix"))

            assertEquals("user-hotkeys\n", hotkeys.readText())
            assertEquals(
                "managed-v2\n",
                File(home, ".config/quickshell/shell.qml").readText(),
            )
            assertEquals(
                "managed-v2\n",
                File(home, ".local/share/dwm-titus/config/quickshell/shell.qml").readText(),
            )
            assertTrue(File(home, ".local/share/dwm-titus/scripts/dwm-status").canExecute())
            assertTrue(File(bin, "dwm-status").canExecute())
        } finally {
            root.deleteRecursively()
        }
    }
}
