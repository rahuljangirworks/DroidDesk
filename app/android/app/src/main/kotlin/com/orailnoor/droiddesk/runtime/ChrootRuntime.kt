package com.orailnoor.droiddesk.runtime

import android.content.Context
import android.util.Log
import java.io.File
import kotlin.concurrent.thread

/**
 * Real chroot-based Linux runtime for rooted Android devices.
 *
 * This runtime downloads a standard ARM64 Ubuntu rootfs, mounts the necessary
 * kernel filesystems via root access, and runs the desktop environment inside a
 * real chroot. X11 output is sent to the app's embedded LorieView X server
 * through a bind-mounted Unix socket.
 */
class ChrootRuntime(private val context: Context) {

    companion object {
        private const val TAG = "ChrootRuntime"
        private const val CHROOT_DE_MARKER = ".chroot_de_installed"

        // Ubuntu 24.04 ARM64 minimal rootfs
        const val ROOTFS_URL =
            "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.4-base-arm64.tar.gz"

        // DesktopActivity starts the chroot while MainActivity owns status and
        // stop controls, so the process handle must be shared app-wide.
        @Volatile private var sessionProcess: Process? = null
    }

    private val rootShell = RootShell(context)
    private val rootfsManager = RootfsManager(context)

    private val baseDir: File get() = context.filesDir
    private val rootfsDir: File get() = File(baseDir, "rootfs")
    private val tmpDir: File get() = File(baseDir, "tmp")
    private val x11HostDir: File get() = File(tmpDir, ".X11-unix")

    // ── Status ──

    fun hasRoot(): Boolean = rootShell.hasRoot()

    fun isRootfsReady(): Boolean = rootfsManager.isRootfsReady()

    fun isDesktopInstalled(): Boolean {
        return getInstalledDE() == DwmJangirProfile.DESKTOP_ID &&
                DwmJangirProfile.isInstalled(
                    File(rootfsDir, "root"),
                    File(rootfsDir, "usr/local/bin"),
                )
    }

    fun getInstalledDE(): String {
        val marker = File(rootfsDir, CHROOT_DE_MARKER)
        if (!marker.exists()) return ""
        return marker.readText().trim().takeIf {
            it == DwmJangirProfile.DESKTOP_ID &&
                DwmJangirProfile.isInstalled(
                    File(rootfsDir, "root"),
                    File(rootfsDir, "usr/local/bin"),
                )
        } ?: ""
    }

    fun isRunning(): Boolean = sessionProcess?.isAlive == true

    fun getRootfsPath(): String = rootfsDir.absolutePath

    fun getRootfsSizeMB(): Long = rootfsManager.getRootfsSizeMB()

    fun getOptionalAppsStatus(): Map<String, Boolean> = mapOf(
        "firefox" to File(rootfsDir, "usr/bin/firefox").exists(),
        "code_oss" to (File(rootfsDir, "usr/bin/code").exists() || File(rootfsDir, "usr/bin/code-oss").exists()),
        "nodejs" to (File(rootfsDir, "usr/bin/node").exists() && File(rootfsDir, "usr/bin/npm").exists()),
        "imagemagick" to (File(rootfsDir, "usr/bin/convert").exists() || File(rootfsDir, "usr/bin/magick").exists()),
        "tailscale" to File(rootfsDir, "usr/local/bin/tailscale").exists(),
        "rustdesk" to File(rootfsDir, "usr/bin/rustdesk").exists(),
    )

    // ── Rootfs setup ──

    /**
     * Download the Ubuntu rootfs with progress callbacks.
     */
    fun downloadRootfs(onProgress: (Double, String) -> Unit) {
        rootfsManager.downloadRootfs("ubuntu", onProgress)
    }

    /**
     * Extract the downloaded rootfs and configure it for chroot.
     */
    fun extractRootfs(onProgress: (Double, String) -> Unit) {
        rootfsManager.extractRootfs { progress, status ->
            onProgress(progress, status)
            if (progress == 1.0) {
                // Additional chroot-specific configuration
                configureChrootRootfs()
            }
        }
    }

    private fun configureChrootRootfs() {
        Log.i(TAG, "Applying chroot-specific rootfs configuration")

        // Ensure critical mount points exist
        listOf(
            "dev", "dev/pts", "dev/shm",
            "proc", "sys", "run",
            "tmp", "tmp/.X11-unix", "tmp/runtime-root",
            "root", "mnt/android", "mnt/sdcard"
        ).forEach {
            File(rootfsDir, it).mkdirs()
        }

        // Portable software-rendering profile. Android vendor GPU libraries do
        // not automatically become usable inside an Ubuntu chroot.
        File(rootfsDir, "etc/profile.d/droiddesk-ha.sh").apply {
            parentFile?.mkdirs()
            writeText(
                """
                #!/bin/bash
                # DroidDesk portable graphics environment
                export DISPLAY=:0
                export XDG_RUNTIME_DIR=/tmp/runtime-root
                export XDG_SESSION_TYPE=x11
                export XDG_DATA_DIRS=/usr/share:/usr/local/share
                export XDG_CONFIG_DIRS=/etc/xdg

                # Conservative Mesa fallback that works across GPU vendors
                export LIBGL_ALWAYS_SOFTWARE=true
                export GALLIUM_DRIVER=llvmpipe
                export MESA_LOADER_DRIVER_OVERRIDE=llvmpipe

                # Disable accessibility bus spam
                export NO_AT_BRIDGE=1
                export GTK_A11Y=none

                # Locale
                export LANG=C.UTF-8
                export LC_ALL=C.UTF-8
                export LANGUAGE=C.UTF-8

                # Prompt
                export PS1='\[\033[01;32m\]droiddesk\[\033[00m\]:\[\033[01;34m\]\w\[\033[00m\]\$ '
                """.trimIndent()
            )
        }

        // Sources list for Ubuntu 24.04
        File(rootfsDir, "etc/apt/sources.list").writeText(
            """
            deb http://ports.ubuntu.com/ubuntu-ports noble main restricted universe multiverse
            deb http://ports.ubuntu.com/ubuntu-ports noble-updates main restricted universe multiverse
            deb http://ports.ubuntu.com/ubuntu-ports noble-security main restricted universe multiverse
            """.trimIndent().trim() + "\n"
        )

        // Make sure apt works without _apt sandbox user
        File(rootfsDir, "etc/apt/apt.conf.d/99-disable-sandbox").writeText("APT::Sandbox::User \"root\";\n")
        File(rootfsDir, "etc/apt/apt.conf.d/99-droiddesk-reliability").writeText(
            "Acquire::Retries \"3\";\n" +
                    "Acquire::http::Timeout \"30\";\n" +
                    "Acquire::https::Timeout \"30\";\n" +
                    "DPkg::Lock::Timeout \"60\";\n"
        )

        // A chroot does not own Android's init system. Prevent package postinst
        // scripts from trying to start LightDM, Tailscale, or other services.
        File(rootfsDir, "usr/sbin/policy-rc.d").apply {
            parentFile?.mkdirs()
            writeText("#!/bin/sh\nexit 101\n")
            setExecutable(true, false)
        }

        Log.i(TAG, "Chroot rootfs configuration complete")
    }

    /**
     * Install the desktop environment and GPU drivers inside the chroot.
     */
    private fun installDwmJangir(onLog: (String) -> Unit): Boolean {
        val sourcePath = "/opt/droiddesk/dwm-jangir"
        val buildCommand = """
            set -e
            mkdir -p /opt/droiddesk
            if [ ! -d "$sourcePath/.git" ]; then
                git clone --no-checkout "${DwmJangirProfile.SOURCE_REPOSITORY}" "$sourcePath"
            fi
            git -C "$sourcePath" remote set-url origin "${DwmJangirProfile.SOURCE_REPOSITORY}"
            git -C "$sourcePath" fetch --depth=1 origin "${DwmJangirProfile.SOURCE_COMMIT}"
            git -C "$sourcePath" checkout --detach --force "${DwmJangirProfile.SOURCE_COMMIT}"
            test "${'$'}(git -C "$sourcePath" rev-parse HEAD)" = "${DwmJangirProfile.SOURCE_COMMIT}"
            make -C "$sourcePath" clean
            make -C "$sourcePath" PREFIX=/usr/local
        """.trimIndent()
        if (execChroot(buildCommand, onLog) != 0) return false

        val sourceDir = File(rootfsDir, "opt/droiddesk/dwm-jangir")
        return DwmJangirProfile.install(
            sourceDir = sourceDir,
            homeDir = File(rootfsDir, "root"),
            binDir = File(rootfsDir, "usr/local/bin"),
            prefix = "/usr/local",
            xSessionsDir = File(rootfsDir, "usr/share/xsessions"),
            lightDmConfigDir = File(rootfsDir, "etc/lightdm/lightdm.conf.d"),
        )
    }

    private fun installTailscale(onLog: (String) -> Unit): Boolean {
        val command = """
            set -e
            work=/tmp/droiddesk-tailscale
            rm -rf "${'$'}work"
            mkdir -p "${'$'}work"
            curl -fL --retry 3 "${DwmJangirProfile.TAILSCALE_URL}" -o "${'$'}work/${DwmJangirProfile.TAILSCALE_ARCHIVE}"
            printf '%s  %s\n' "${DwmJangirProfile.TAILSCALE_SHA256}" "${'$'}work/${DwmJangirProfile.TAILSCALE_ARCHIVE}" | sha256sum -c -
            tar -xzf "${'$'}work/${DwmJangirProfile.TAILSCALE_ARCHIVE}" -C "${'$'}work"
            install -m 0755 "${'$'}work/tailscale_${DwmJangirProfile.TAILSCALE_VERSION}_arm64/tailscale" /usr/local/bin/tailscale
            install -m 0755 "${'$'}work/tailscale_${DwmJangirProfile.TAILSCALE_VERSION}_arm64/tailscaled" /usr/local/bin/tailscaled
        """.trimIndent()
        if (execChroot(command, onLog) != 0) return false

        File(rootfsDir, "usr/local/bin/droiddesk-tailscaled").apply {
            parentFile?.mkdirs()
            writeText(
                """
                #!/bin/bash
                set -eu
                socket=/run/tailscale/tailscaled.sock
                state=/var/lib/tailscale
                mkdir -p /run/tailscale "${'$'}state"
                case "${'$'}{1:-status}" in
                    start)
                        if ! pgrep -f "tailscaled.*${'$'}socket" >/dev/null 2>&1; then
                            tun=userspace-networking
                            [ -c /dev/net/tun ] && tun=tailscale0
                            nohup /usr/local/bin/tailscaled \
                                --socket="${'$'}socket" \
                                --state="${'$'}state/tailscaled.state" \
                                --tun="${'$'}tun" \
                                >"${'$'}state/tailscaled.log" 2>&1 &
                            sleep 1
                            if ! pgrep -f "tailscaled.*${'$'}socket" >/dev/null 2>&1 &&
                                [ "${'$'}tun" != userspace-networking ]; then
                                nohup /usr/local/bin/tailscaled \
                                    --socket="${'$'}socket" \
                                    --state="${'$'}state/tailscaled.state" \
                                    --tun=userspace-networking \
                                    --socks5-server=127.0.0.1:1055 \
                                    --outbound-http-proxy-listen=127.0.0.1:1055 \
                                    >"${'$'}state/tailscaled.log" 2>&1 &
                            fi
                        fi
                        ;;
                    up) /usr/local/bin/tailscale --socket="${'$'}socket" up ;;
                    status) /usr/local/bin/tailscale --socket="${'$'}socket" status ;;
                    stop) pkill -f "tailscaled.*${'$'}socket" >/dev/null 2>&1 || true ;;
                    *) echo "Usage: droiddesk-tailscaled {start|up|status|stop}" >&2; exit 2 ;;
                esac
                """.trimIndent() + "\n",
            )
            setExecutable(true, false)
        }
        return true
    }

    private fun installRustDesk(onLog: (String) -> Unit): Boolean {
        val command = """
            set -e
            deb="/tmp/${DwmJangirProfile.RUSTDESK_DEB}"
            curl -fL --retry 3 "${DwmJangirProfile.RUSTDESK_URL}" -o "${'$'}deb"
            printf '%s  %s\n' "${DwmJangirProfile.RUSTDESK_SHA256}" "${'$'}deb" | sha256sum -c -
            dpkg -i "${'$'}deb" || true
            DEBIAN_FRONTEND=noninteractive apt-get install -f -y
            test -x /usr/bin/rustdesk
        """.trimIndent()
        return execChroot(command, onLog) == 0
    }

    fun installDesktopEnvironment(
        desktopEnv: String = DwmJangirProfile.DESKTOP_ID,
        onProgress: (Double, String) -> Unit = { _, _ -> },
        onLog: (String) -> Unit = {}
    ) {
        if (!hasRoot()) {
            onProgress(-1.0, "Root access required for chroot mode")
            return
        }
        if (!isRootfsReady()) {
            onProgress(-1.0, "Rootfs not ready. Download and extract first.")
            return
        }

        thread(name = "chroot-de-install") {
            try {
                onProgress(0.0, "Mounting rootfs...")
                ensureMounts()

                onProgress(0.05, "Updating package lists...")
                if (execChroot("apt-get update -y", onLog) != 0) {
                    throw IllegalStateException("Package index update failed")
                }

                onProgress(0.1, "Installing core tools...")
                if (execChroot(
                    "DEBIAN_FRONTEND=noninteractive TZ=Etc/UTC apt-get install -y --no-install-recommends " +
                            "locales ca-certificates wget curl dbus-x11",
                    onLog
                ) != 0) throw IllegalStateException("Core package installation failed")

                onProgress(0.2, "Installing Mesa GPU drivers...")
                if (execChroot(
                    "DEBIAN_FRONTEND=noninteractive TZ=Etc/UTC apt-get install -y --no-install-recommends " +
                            "mesa-vulkan-drivers mesa-opencl-icd libgl1-mesa-dri libglx-mesa0 vulkan-tools",
                    onLog
                ) != 0) Log.w(TAG, "Mesa packages unavailable; desktop will use available software rendering")

                val selectedDesktop = DwmJangirProfile.normalizeDesktop(desktopEnv)
                onProgress(0.35, "Installing DWM, LightDM compatibility, and build dependencies...")
                val dePackages = DwmJangirProfile.chrootPackages.joinToString(" ")
                if (execChroot(
                    "DEBIAN_FRONTEND=noninteractive TZ=Etc/UTC apt-get install -y --no-install-recommends $dePackages",
                    onLog
                ) != 0) throw IllegalStateException("Desktop package installation failed")

                onProgress(0.62, "Building pinned dwm-jangir source...")
                if (!installDwmJangir(onLog)) {
                    throw IllegalStateException("Pinned dwm-jangir build failed")
                }

                onProgress(0.74, "Installing verified Tailscale...")
                if (!installTailscale(onLog)) {
                    throw IllegalStateException("Tailscale installation failed")
                }

                onProgress(0.82, "Installing verified RustDesk ARM64 package...")
                if (!installRustDesk(onLog)) {
                    throw IllegalStateException("RustDesk installation failed")
                }

                onProgress(0.90, "Installing Desktop Essentials tools...")
                val essentialsExit = execChroot(
                    "DEBIAN_FRONTEND=noninteractive TZ=Etc/UTC apt-get install -y --no-install-recommends " +
                            "git nano htop wget curl python3 python3-pip openssh-client",
                    onLog
                )
                if (essentialsExit != 0) throw IllegalStateException("Desktop Essentials package installation failed")

                onProgress(0.9, "Cleaning up...")
                execChroot("apt-get clean", onLog)

                File(rootfsDir, CHROOT_DE_MARKER).writeText("$selectedDesktop\n")
                onProgress(1.0, "DWM Rahul, Tailscale, and RustDesk installed in chroot")
                Log.i(TAG, "Desktop environment installation complete")
            } catch (e: Exception) {
                Log.e(TAG, "DE install failed", e)
                onProgress(-1.0, "Installation failed: ${e.message}")
            }
        }
    }

    fun installOptionalApp(
        appId: String,
        onProgress: (Double, String) -> Unit = { _, _ -> },
        onLog: (String) -> Unit = {},
    ): Boolean {
        if (!hasRoot() || !isDesktopInstalled()) return false
        if (getOptionalAppsStatus()[appId] == true) {
            onProgress(1.0, "Already installed")
            return true
        }

        return try {
            ensureMounts()
            onProgress(0.05, "Repairing interrupted packages...")
            execChroot("DEBIAN_FRONTEND=noninteractive dpkg --configure -a", onLog)

            val command = when (appId) {
                "firefox" -> """
                    set -e
                    export DEBIAN_FRONTEND=noninteractive
                    apt-get install -y --no-install-recommends ca-certificates wget gpg
                    install -d -m 0755 /etc/apt/keyrings
                    wget -q https://packages.mozilla.org/apt/repo-signing-key.gpg -O /etc/apt/keyrings/packages.mozilla.org.asc
                    echo 'deb [signed-by=/etc/apt/keyrings/packages.mozilla.org.asc] https://packages.mozilla.org/apt mozilla main' > /etc/apt/sources.list.d/mozilla.list
                    printf 'Package: *\nPin: origin packages.mozilla.org\nPin-Priority: 1000\n' > /etc/apt/preferences.d/mozilla
                    apt-get update -y
                    apt-get install -y --no-install-recommends firefox
                """.trimIndent()
                "code_oss" -> """
                    set -e
                    export DEBIAN_FRONTEND=noninteractive
                    apt-get install -y --no-install-recommends ca-certificates wget gpg apt-transport-https
                    install -d -m 0755 /etc/apt/keyrings
                    wget -qO- https://packages.microsoft.com/keys/microsoft.asc | gpg --dearmor -o /etc/apt/keyrings/packages.microsoft.gpg
                    echo 'deb [arch=arm64 signed-by=/etc/apt/keyrings/packages.microsoft.gpg] https://packages.microsoft.com/repos/code stable main' > /etc/apt/sources.list.d/vscode.list
                    apt-get update -y
                    apt-get install -y --no-install-recommends code
                """.trimIndent()
                "nodejs" -> "DEBIAN_FRONTEND=noninteractive apt-get update -y && apt-get install -y --no-install-recommends nodejs npm"
                "imagemagick" -> "DEBIAN_FRONTEND=noninteractive apt-get update -y && apt-get install -y --no-install-recommends imagemagick"
                "tailscale" -> {
                    val installed = installTailscale(onLog)
                    onProgress(if (installed) 1.0 else -1.0, if (installed) "Tailscale installed" else "Tailscale installation failed")
                    return installed
                }
                "rustdesk" -> {
                    val installed = installRustDesk(onLog)
                    onProgress(if (installed) 1.0 else -1.0, if (installed) "RustDesk installed" else "RustDesk installation failed")
                    return installed
                }
                else -> return false
            }

            onProgress(0.25, "Installing optional application...")
            val exitCode = execChroot(command, onLog)
            if (exitCode != 0) throw IllegalStateException("Package manager exited with code $exitCode")
            onProgress(1.0, "Installation complete")
            true
        } catch (error: Exception) {
            Log.e(TAG, "Optional app installation failed: $appId", error)
            onProgress(-1.0, "Installation failed: ${error.message}")
            false
        }
    }

    // ── Session management ──

    /**
     * Start the chrooted desktop session.
     * The caller should ensure the X11 socket directory is mounted before this.
     */
    fun startSession(
        desktopEnv: String = DwmJangirProfile.DESKTOP_ID,
        width: Int = 1920,
        height: Int = 1080,
    ) {
        if (!hasRoot()) {
            Log.e(TAG, "Cannot start chroot session without root")
            return
        }
        if (!isRootfsReady()) {
            Log.e(TAG, "Rootfs not ready")
            return
        }
        if (isRunning()) {
            Log.w(TAG, "Chroot session already running")
            return
        }

        ensureMounts()
        bindX11Socket()

        val selectedDesktop = DwmJangirProfile.normalizeDesktop(desktopEnv)
        val deBin = "/usr/local/bin/dwm"

        val runScript = """
            # Standard FHS PATH (inherited Android PATH lacks /usr/bin)
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

            # Reset environment variables leaked from Android app
            export TMPDIR=/tmp
            export HOME=/root
            export PREFIX=/usr
            export XDG_SESSION_TYPE=x11
            export XDG_CURRENT_DESKTOP=dwm
            export DESKTOP_SESSION=dwm

            # Source DroidDesk environment
            . /etc/profile.d/droiddesk-ha.sh 2>/dev/null || true

            # Session D-Bus
            export DBUS_SESSION_BUS_ADDRESS=unix:path=/tmp/dbus-session
            rm -f /tmp/dbus-session
            dbus-daemon --session --address="${'$'}DBUS_SESSION_BUS_ADDRESS" --fork --nopidfile

            # Make sure X11 socket dir exists in case bind mount was late
            mkdir -p /tmp/.X11-unix

            /usr/local/bin/droiddesk-tailscaled start >/dev/null 2>&1 || true

            echo "DIAG: Starting DWM Rahul in chroot on DISPLAY=:0 ..."
            exec $deBin
        """.trimIndent()

        Log.i(TAG, "Starting chroot session for $selectedDesktop")

        // Launch via ProcessBuilder through su so we get a Process handle we can monitor.
        val su = rootShell.findSuPath() ?: return
        val fullCommand = "chroot ${rootfsDir.absolutePath} /bin/bash -c ${shellQuote(runScript)}"
        val startedSession = ProcessBuilder(su, "-c", fullCommand)
            .redirectErrorStream(true)
            .start()
        sessionProcess = startedSession

        Thread {
            try {
                val reader = startedSession.inputStream.bufferedReader()
                val buffer = CharArray(1024)
                var charsRead: Int
                while (reader.read(buffer).also { charsRead = it } != -1) {
                    Log.d(TAG, "CHROOT DESKTOP: " + String(buffer, 0, charsRead))
                }
            } catch (error: java.io.IOException) {
                Log.d(TAG, "Chroot desktop output stream closed")
            }
        }.start()
    }

    /**
     * Stop the chroot session and unmount bind mounts.
     */
    fun stopSession() {
        Log.i(TAG, "Stopping chroot session...")
        sessionProcess?.let {
            try {
                it.destroyForcibly()
                it.waitFor()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping session: ${e.message}")
            }
        }
        sessionProcess = null
        unmountAll()
        Log.i(TAG, "Chroot session stopped")
    }

    // ── Mount handling ──

    /**
     * Ensure /dev, /proc, /sys, /dev/pts and tmpfs mounts are active.
     */
    fun ensureMounts() {
        if (!hasRoot()) return

        val mounts = rootShell.exec("mount").lines()
        fun isMounted(path: String): Boolean {
            val absolute = File(rootfsDir, path).absolutePath
            return mounts.any { it.contains(" on $absolute ") }
        }

        mountIfNeeded("/dev", "--bind /dev") { isMounted("dev") }
        mountIfNeeded("/dev/pts", "--bind /dev/pts") { isMounted("dev/pts") }
        mountIfNeeded("/dev/shm", "-t tmpfs tmpfs") { isMounted("dev/shm") }
        mountIfNeeded("/proc", "--bind /proc") { isMounted("proc") }
        mountIfNeeded("/sys", "--bind /sys") { isMounted("sys") }
        mountIfNeeded("/run", "-t tmpfs tmpfs") { isMounted("run") }
        mountIfNeeded("/tmp", "-t tmpfs tmpfs") { isMounted("tmp") }

        // Create runtime dirs after tmpfs is mounted
        execChroot("mkdir -p /tmp/.X11-unix /tmp/runtime-root /root")
    }

    private fun mountIfNeeded(relative: String, mountArgs: String, alreadyMounted: () -> Boolean) {
        if (alreadyMounted()) return
        val target = File(rootfsDir, relative).absolutePath
        try {
            rootShell.exec("mkdir -p $target && mount $mountArgs $target")
            Log.i(TAG, "Mounted $target")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to mount $target: ${e.message}")
        }
    }

    /**
     * Bind-mount the host X11 socket directory into the chroot.
     */
    fun bindX11Socket() {
        if (!hasRoot()) return
        x11HostDir.mkdirs()
        val chrootX11 = File(rootfsDir, "tmp/.X11-unix").absolutePath
        val hostX11 = x11HostDir.absolutePath

        // If already mounted, leave it
        val mounts = rootShell.exec("mount").lines()
        if (mounts.any { it.contains(" on $chrootX11 ") }) return

        rootShell.exec("mkdir -p $chrootX11 && mount --bind $hostX11 $chrootX11")
        Log.i(TAG, "Bound X11 socket: $hostX11 -> $chrootX11")
    }

    /**
     * Unmount all DroidDesk-related mounts.
     */
    fun unmountAll() {
        if (!hasRoot()) return
        val mounts = rootShell.exec("mount").lines()
        val targets = listOf(
            File(rootfsDir, "tmp/.X11-unix").absolutePath,
            File(rootfsDir, "dev/pts").absolutePath,
            File(rootfsDir, "dev/shm").absolutePath,
            File(rootfsDir, "dev").absolutePath,
            File(rootfsDir, "proc").absolutePath,
            File(rootfsDir, "sys").absolutePath,
            File(rootfsDir, "run").absolutePath,
            File(rootfsDir, "tmp").absolutePath
        )
        // Unmount in reverse order, be tolerant of busy mounts
        targets.reversed().forEach { target ->
            if (mounts.any { it.contains(" on $target ") }) {
                try {
                    rootShell.exec("umount -l $target 2>/dev/null || umount $target 2>/dev/null || true")
                    Log.i(TAG, "Unmounted $target")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to unmount $target: ${e.message}")
                }
            }
        }
    }

    // ── Command execution inside chroot ──

    /**
     * Execute a command inside the chroot as root.
     */
    fun executeCommand(command: String, onOutput: ((String) -> Unit)? = null): String {
        if (!hasRoot()) return "Error: root access required"
        val wrapped = "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; $command"
        return if (onOutput != null) {
            val code = rootShell.exec("chroot ${rootfsDir.absolutePath} /bin/bash -c ${shellQuote(wrapped)}") { chunk ->
                onOutput(chunk)
            }
            "Exit code: $code"
        } else {
            rootShell.exec("chroot ${rootfsDir.absolutePath} /bin/bash -c ${shellQuote(wrapped)}")
        }
    }

    private fun execChroot(command: String, onLog: (String) -> Unit = {}): Int {
        val wrapped = "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; $command"
        val output = rootShell.exec("chroot ${rootfsDir.absolutePath} /bin/bash -c ${shellQuote(wrapped)}") { chunk ->
            onLog(chunk)
        }
        Log.d(TAG, "chroot command exit code: $output")
        return output
    }

    private fun shellQuote(input: String): String {
        // Use a single-quoted string that handles embedded single quotes safely
        return "'" + input.replace("'", "'\"'\"'") + "'"
    }
}
