package com.orailnoor.droiddesk.runtime

/**
 * Reproducible DroidDesk desktop profile.
 *
 * Keep network artifacts pinned here so runtime installers and tests share one
 * source of truth. Authentication material never belongs in this profile.
 */
object DwmJangirProfile {
    const val DESKTOP_ID = "dwm-jangir"
    const val DISPLAY_NAME = "DWM Rahul"

    const val SOURCE_REPOSITORY = "https://github.com/rahuljangirworks/dwm-jangir.git"
    const val SOURCE_COMMIT = "164d43470736e85a3d878e138f81352166c3297f"

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

    fun packagePlan(mode: String): List<String> = when (mode) {
        "native" -> nativeCorePackages + nativeBuildPackages + nativeRecommendedPackages
        "chroot" -> chrootPackages
        else -> emptyList()
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
