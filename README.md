---
title: DroidDesk
status: active
version: 0.2.0
updated: 2026-07-30
---

# DroidDesk

DroidDesk runs Rahul's `dwm-jangir` X11 desktop on an ARM64 Android phone. The
standalone APK embeds its own X server and supports two Linux runtime modes:

- Rooted devices use an Ubuntu 24.04 chroot.
- Non-rooted devices use an app-private native Termux userspace.

Both modes launch DWM directly on `DISPLAY=:0`. XFCE is not installed by the
active setup flow.

> [!IMPORTANT]
> DroidDesk is an independent GPL-3.0 project that incorporates modified
> Termux:X11 components. It is not affiliated with or endorsed by Termux,
> Termux:X11, TUR, Canonical, Ubuntu, Tailscale, RustDesk, or the respective
> upstream projects.

## DWM Rahul Profile

The release pins these inputs so setup is reproducible:

| Component | Pinned input |
| --- | --- |
| `dwm-jangir` | Commit `164d43470736e85a3d878e138f81352166c3297f` |
| Tailscale | ARM64 `1.98.10` archive with SHA-256 verification |
| RustDesk | ARM64 `1.4.9` Debian package with SHA-256 verification |
| Rooted base | Ubuntu Base 24.04 ARM64 |
| Display | Embedded Termux:X11-compatible server on `:0` |

The DWM installer preserves existing `hotkeys.toml`, `themes.toml`, and
`window-rules.toml`. The Quickshell configuration is managed from the pinned
`dwm-jangir` source. Quickshell is started only when version 0.3.0 or newer is
already available; otherwise DWM's built-in bar remains usable.

## Automated Setup

The standalone app performs these steps:

1. Detect root and select native Termux or rooted Ubuntu chroot mode.
2. Prepare the package repositories and repair interrupted package state.
3. Install the X11, audio, D-Bus, compiler, and DWM dependencies.
4. Fetch the pinned `dwm-jangir` revision and verify its exact Git commit.
5. Build and install DWM plus Rahul's scripts and runtime configuration.
6. Install a mobile-safe autostart profile without fixed monitor assumptions.
7. Install verified Tailscale ARM64 binaries.
8. In rooted Ubuntu mode, install verified RustDesk ARM64 and LightDM
   compatibility files.
9. Start the embedded X server and launch DWM directly.

Setup is designed to be repeatable. Existing user-owned DWM TOML configuration
is not overwritten.

## Tailscale

Run this inside the DroidDesk terminal after setup:

```bash
droiddesk-tailscaled start
droiddesk-tailscaled up
droiddesk-tailscaled status
```

Rooted mode tries `/dev/net/tun` first and falls back to Tailscale userspace
networking. Non-root mode uses userspace networking with SOCKS5 and HTTP proxy
listeners on `127.0.0.1:1055`.

Authentication is deliberately interactive. DroidDesk never stores an auth key
in source, app assets, or release artifacts.

## RustDesk

Rooted Ubuntu mode installs the verified official Linux ARM64 package and
starts its tray process from the DWM session when available.

The official Android RustDesk app is the supported fallback for non-rooted
devices because a glibc Linux package cannot run inside the native Android
Termux userspace.

## LightDM

The rooted chroot receives:

- `/usr/share/xsessions/dwm.desktop`
- `/etc/lightdm/lightdm.conf.d/50-droiddesk.conf`
- LightDM and its GTK greeter packages

DroidDesk does not start LightDM at Android boot. Android owns the device init,
login, and display lifecycle, so the app launches DWM directly against its
embedded X server. The LightDM files provide Linux compatibility and recovery
metadata only.

## Kernel Policy

DroidDesk reports:

- the running Android kernel release;
- `/dev/net/tun` availability;
- the selected Tailscale networking mode; and
- that kernel management is device-specific.

It never flashes a boot image or installs a generic "latest kernel." Android
kernels, vendor modules, boot images, AVB state, and bootloader requirements
depend on the exact phone and ROM. Kernel replacement belongs in a separately
reviewed, device-specific recovery plan.

## Installation

Download the ARM64 APK and its checksum from the latest GitHub release:

```text
DroidDesk-v0.2.0-arm64.apk
DroidDesk-v0.2.0-arm64.apk.sha256
```

Verify the checksum, sideload the APK, open DroidDesk, and follow the setup
screen. Root access is optional.

Requirements:

- ARM64 Android device;
- Android API 28 or newer;
- at least 2 GB free storage;
- network access during first-time provisioning; and
- an unlocked/rooted device only for Ubuntu chroot, Linux RustDesk host mode,
  and LightDM compatibility.

The standalone app does not require a separate Termux or Termux:X11 APK.

## Development

Run repository contract checks:

```bash
scripts/check.sh
```

With Flutter and Java installed, the check also runs formatting, analysis, and
Flutter tests. GitHub Actions additionally runs Kotlin tests and builds the
Android APK.

CI workflow:

```text
.github/workflows/ci.yml
```

Release workflow:

```text
.github/workflows/release.yml
```

Pushing a `v*` tag validates the version, runs tests, builds the release APK,
generates its SHA-256 file, and publishes both to a GitHub release.

## Runtime Verification

Static and CI checks cannot prove a graphical Android session. Before calling a
release device-verified, test on an ARM64 phone:

1. fresh install in the intended root mode;
2. repeated setup;
3. DWM launch and stop;
4. terminal launch and keyboard/mouse input;
5. Tailscale authentication and connectivity;
6. RustDesk view/control in rooted mode;
7. app restart and Android reboot recovery; and
8. software rendering plus Adreno acceleration where applicable.

## Credits and License

Created originally by [orailnoor](https://youtube.com/@orailnoor) and adapted
for Rahul's `dwm-jangir` desktop.

DroidDesk is licensed under [GNU GPL version 3 only](LICENSE). See:

- [Notices and attribution](NOTICE.md)
- [Third-party software inventory](THIRD_PARTY_NOTICES.md)
- [Release compliance status](COMPLIANCE.md)

Do not describe a binary as fully compliant until the blocking items in
`COMPLIANCE.md` are resolved.
