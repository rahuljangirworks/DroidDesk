---
title: DroidDesk Android App
status: active
version: 0.2.0
updated: 2026-07-30
---

# DroidDesk Android App

Flutter controls setup and status while Kotlin owns the native Termux and
rooted Ubuntu runtimes. Both paths install and launch the pinned
`dwm-jangir` profile on the embedded X11 server.

## Checks

```bash
flutter pub get
dart format --output=none --set-exit-if-changed lib test
flutter analyze --fatal-infos
flutter test
flutter build apk --debug
```

Kotlin profile tests run through:

```bash
cd android
./gradlew testDebugUnitTest
```

The repository GitHub Actions workflows generate the ignored Gradle wrapper
before running Android tests and builds.
