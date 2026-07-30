---
title: DroidDesk changelog
type: changelog
status: active
version: 0.2.0
updated: 2026-07-30
---

# Changelog

## 0.2.0 - 2026-07-30

- Replaced the active XFCE setup and launch path with the pinned
  `dwm-jangir` desktop profile.
- Added DWM source verification, build automation, mobile autostart, and
  LightDM session compatibility for rooted Debian installs.
- Centralized native and chroot DWM file installation in one versioned,
  idempotent profile that preserves user TOML settings while updating
  DroidDesk-managed session files.
- Added an explicit ARM64 native-runtime gate so incompatible x86 emulators
  fail with a clear ABI message instead of attempting the AArch64 bootstrap.
- Fixed relocated Termux Git helper, Make shell, and `pkg-config` discovery so
  native ARM64 setup can clone and build the pinned `dwm-jangir` source.
- Made interrupted DWM source builds recoverable by resetting the app-managed
  checkout to the pinned commit before each build.
- Added the XKB data, Xcursor runtime, and Quickshell packages required for the
  embedded DWM desktop, and synchronized the pinned profile's managed
  `config/` and `scripts/` trees using the upstream install layout.
- Fixed an X11 service startup race that could report failure after the native
  server had already started, and made the setup-complete view scroll safely on
  shorter landscape layouts.
- Added verified Tailscale ARM64 installation with userspace-networking
  fallback when `/dev/net/tun` is unavailable.
- Added verified RustDesk AArch64 installation for rooted Debian and explicit
  Android-app guidance for non-root devices.
- Added kernel visibility and an explicit safe policy that leaves
  device-specific kernel/boot-image updates to the device maintainer.
- Added Flutter, Kotlin, shell-contract, CI, APK artifact, checksum, and tagged
  release automation.

### Release qualification

This release requires the GitHub Actions test matrix to pass. Physical Android
device testing remains required for root/chroot, Termux:X11, LightDM, Tailscale
TUN, and RustDesk end-to-end qualification.
