#!/usr/bin/env bash
set -euo pipefail

repo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
ci="$repo_dir/.github/workflows/ci.yml"
release="$repo_dir/.github/workflows/release.yml"
pubspec="$repo_dir/app/pubspec.yaml"

test -f "$ci"
test -f "$release"

grep -q 'flutter analyze --fatal-infos' "$ci"
grep -q 'flutter test' "$ci"
grep -q './gradlew :app:testDebugUnitTest' "$ci"
grep -q 'flutter build apk --debug' "$ci"
grep -q 'tags:' "$release"
grep -Eq "['\"]v\\\\?\\*['\"]" "$release"
grep -q './gradlew :app:testDebugUnitTest' "$release"
grep -q 'flutter build apk --release' "$release"
grep -q -- '--split-per-abi' "$release"
grep -q -- '--target-platform android-arm64' "$release"
grep -q 'DROIDDESK_KEYSTORE_BASE64' "$release"
grep -q 'DROIDDESK_KEYSTORE_PATH' "$release"
grep -q 'apksigner.*verify' "$release"
grep -q 'Expected only arm64-v8a' "$release"
grep -q 'softprops/action-gh-release@v3' "$release"
grep -q 'draft: true' "$release"
grep -q 'prerelease: true' "$release"
if grep -q 'signingConfigs.getByName("debug")' "$repo_dir/app/android/app/build.gradle.kts"; then
    echo "FAIL: release build must not use Android debug signing" >&2
    exit 1
fi
grep -Eq '^version: 0\.2\.0\+[0-9]+$' "$pubspec"

echo "PASS: release workflow contract"
