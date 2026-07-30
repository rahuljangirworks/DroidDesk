package com.orailnoor.droiddesk.runtime

import java.io.File

/**
 * Reproducible DroidDesk desktop profile.
 *
 * Keep network artifacts pinned here so runtime installers and tests share one
 * source of truth. This is also the single owner of the files installed from
 * dwm-jangir, matching the idempotent profile pattern used by upstream XFCE.
 * Authentication material never belongs in this profile.
 */
object DwmJangirProfile {
    const val DESKTOP_ID = "dwm-jangir"
    const val DISPLAY_NAME = "DWM Rahul"
    const val SUPPORTED_NATIVE_ABI = "arm64-v8a"

    const val SOURCE_REPOSITORY = "https://github.com/rahuljangirworks/dwm-jangir.git"
    const val SOURCE_COMMIT = "164d43470736e85a3d878e138f81352166c3297f"
    private const val PROFILE_VERSION = "1"
    private const val PROFILE_MARKER = ".droiddesk-dwm-rahul-profile"

    const val TAILSCALE_VERSION = "1.98.10"
    const val TAILSCALE_ARCHIVE = "tailscale_1.98.10_arm64.tgz"
    const val TAILSCALE_SHA256 = "d74a84e07cb1948d9f09a23ae161417c6127e562949773705c95d0762be2809d"
    const val TAILSCALE_URL = "https://pkgs.tailscale.com/stable/$TAILSCALE_ARCHIVE"

    const val RUSTDESK_VERSION = "1.4.9"
    const val RUSTDESK_DEB = "rustdesk-1.4.9-aarch64.deb"
    const val RUSTDESK_SHA256 = "ce62c996f14d33f3bbe3a330e953644a44bace7f05885a7953f7395d69fb49c0"
    const val RUSTDESK_URL =
        "https://github.com/rustdesk/rustdesk/releases/download/$RUSTDESK_VERSION/$RUSTDESK_DEB"

    val nativeCorePackages = listOf(
        "xorg-xrandr",
        "pulseaudio",
        "dbus",
        "dmenu",
        "st",
    )

    val nativeBuildPackages = listOf(
        "git",
        "make",
        "clang",
        "pkg-config",
        "coreutils",
        "curl",
        "tar",
        "libx11",
        "libxft",
        "libxinerama",
        "libxrender",
        "imlib2",
        "libxcb",
        "fontconfig",
        "freetype",
    )

    val nativeRecommendedPackages = listOf(
        "picom",
        "feh",
        "noto-fonts",
        "noto-fonts-emoji",
    )

    val chrootPackages = listOf(
        "build-essential",
        "pkg-config",
        "git",
        "curl",
        "ca-certificates",
        "dbus-x11",
        "x11-xserver-utils",
        "xterm",
        "dmenu",
        "picom",
        "feh",
        "lightdm",
        "lightdm-gtk-greeter",
        "libx11-dev",
        "libxft-dev",
        "libxinerama-dev",
        "libxrender-dev",
        "libimlib2-dev",
        "libx11-xcb-dev",
        "libxcb1-dev",
        "libxcb-res0-dev",
        "libfontconfig1-dev",
        "libfreetype-dev",
        "fonts-dejavu-core",
        "fonts-noto-color-emoji",
    )

    fun normalizeDesktop(requested: String?): String = DESKTOP_ID

    fun supportsNativeAbi(supportedAbis: List<String>): Boolean =
        supportedAbis.firstOrNull() == SUPPORTED_NATIVE_ABI

    fun packagePlan(mode: String): List<String> = when (mode) {
        "native" -> nativeCorePackages + nativeBuildPackages + nativeRecommendedPackages
        "chroot" -> chrootPackages
        else -> emptyList()
    }

    private fun markerFile(homeDir: File): File =
        File(homeDir, ".local/share/dwm-titus/$PROFILE_MARKER")

    private fun markerContents(): String = "$PROFILE_VERSION:$SOURCE_COMMIT\n"

    fun isInstalled(homeDir: File, binDir: File): Boolean =
        File(binDir, "dwm").canExecute() &&
            markerFile(homeDir).run { isFile && readText() == markerContents() }

    /**
     * Install the Android-safe DWM profile from an already verified and built
     * dwm-jangir checkout. User-owned TOML files are seeded only once, while
     * Quickshell and DroidDesk session scripts are explicitly managed.
     */
    fun install(
        sourceDir: File,
        homeDir: File,
        binDir: File,
        prefix: String,
        xSessionsDir: File? = null,
        lightDmConfigDir: File? = null,
    ): Boolean {
        val builtDwm = File(sourceDir, "dwm")
        val sourceConfig = File(sourceDir, "config")
        if (!builtDwm.isFile || !sourceConfig.isDirectory) return false

        return runCatching {
            binDir.mkdirs()
            builtDwm.copyTo(File(binDir, "dwm"), overwrite = true)
            check(File(binDir, "dwm").setExecutable(true, false) || File(binDir, "dwm").canExecute())

            File(sourceDir, "scripts").listFiles()
                ?.filter { it.isFile && it.canExecute() }
                ?.forEach { helper ->
                    val destination = File(binDir, helper.name)
                    helper.copyTo(destination, overwrite = true)
                    check(destination.setExecutable(true, false) || destination.canExecute())
                }

            val xdgConfig = File(homeDir, ".config").apply { mkdirs() }
            val dwmConfig = File(xdgConfig, "dwm-titus").apply { mkdirs() }
            listOf("hotkeys.toml", "themes.toml", "window-rules.toml").forEach { name ->
                val destination = File(dwmConfig, name)
                if (!destination.exists()) {
                    File(sourceConfig, name).copyTo(destination)
                }
            }

            replaceManagedDirectory(
                source = File(sourceConfig, "quickshell"),
                destination = File(xdgConfig, "quickshell"),
            )

            val autostartDir =
                File(homeDir, ".local/share/dwm-titus/scripts").apply { mkdirs() }
            writeExecutable(
                File(autostartDir, "autostart.sh"),
                mobileAutostart(prefix),
            )
            writeExecutable(
                File(autostartDir, "autostop.sh"),
                """
                #!/bin/sh
                pkill -x quickshell >/dev/null 2>&1 || true
                pkill -x picom >/dev/null 2>&1 || true
                """.trimIndent() + "\n",
            )

            xSessionsDir?.let { directory ->
                directory.mkdirs()
                File(directory, "dwm.desktop").writeText(
                    """
                    [Desktop Entry]
                    Name=$DISPLAY_NAME
                    Comment=Rahul's dwm-jangir X11 desktop
                    Exec=$prefix/bin/dwm
                    Type=Application
                    DesktopNames=dwm
                    """.trimIndent() + "\n",
                )
            }
            lightDmConfigDir?.let { directory ->
                directory.mkdirs()
                File(directory, "50-droiddesk.conf").writeText(
                    """
                    [Seat:*]
                    user-session=dwm
                    greeter-session=lightdm-gtk-greeter
                    """.trimIndent() + "\n",
                )
            }

            markerFile(homeDir).apply {
                parentFile?.mkdirs()
                writeText(markerContents())
            }
            check(isInstalled(homeDir, binDir))
            true
        }.getOrDefault(false)
    }

    private fun replaceManagedDirectory(source: File, destination: File) {
        check(source.isDirectory)
        val staged = File(destination.parentFile, "${destination.name}.droiddesk-new")
        staged.deleteRecursively()
        check(source.copyRecursively(staged, overwrite = true))
        destination.deleteRecursively()
        check(staged.renameTo(destination))
    }

    private fun writeExecutable(file: File, contents: String) {
        file.writeText(contents)
        check(file.setExecutable(true, false) || file.canExecute())
    }

    fun mobileAutostart(prefix: String): String = """
        #!/bin/sh
        # DroidDesk owns DISPLAY and D-Bus. Keep this session Android-safe.
        export DISPLAY="${'$'}{DISPLAY:-:0}"
        export XDG_SESSION_TYPE=x11
        export XDG_CURRENT_DESKTOP=dwm
        export DESKTOP_SESSION=dwm

        start_once() {
            process_name=${'$'}1
            shift
            command -v "${'$'}1" >/dev/null 2>&1 || return 0
            pgrep -x "${'$'}process_name" >/dev/null 2>&1 || "${'$'}@" >/dev/null 2>&1 &
        }

        quickshell_compatible() {
            command -v quickshell >/dev/null 2>&1 || return 1
            version=${'$'}(quickshell --version 2>/dev/null |
                grep -Eo '[0-9]+\.[0-9]+(\.[0-9]+)?' | head -n 1)
            [ -n "${'$'}version" ] || return 1
            [ "${'$'}(printf '%s\n' 0.3.0 "${'$'}version" | sort -V | head -n 1)" = 0.3.0 ]
        }

        if quickshell_compatible &&
            [ -f "${'$'}{XDG_CONFIG_HOME:-${'$'}HOME/.config}/quickshell/shell.qml" ]; then
            start_once quickshell quickshell --no-duplicate
        fi
        start_once picom picom --backend xrender
        if command -v rustdesk >/dev/null 2>&1; then
            start_once rustdesk rustdesk --tray
        fi
        if [ -x "$prefix/bin/droiddesk-tailscaled" ]; then
            "$prefix/bin/droiddesk-tailscaled" start >/dev/null 2>&1 || true
        fi
    """.trimIndent() + "\n"
}
