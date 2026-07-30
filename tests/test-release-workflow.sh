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
grep -q 'flutter build apk --debug' "$ci"
grep -q 'tags:' "$release"
grep -Eq "['\"]v\\\\?\\*['\"]" "$release"
grep -q 'flutter build apk --release' "$release"
grep -q 'softprops/action-gh-release@v3' "$release"
grep -q 'prerelease: true' "$release"
grep -Eq '^version: 0\.2\.0\+[0-9]+$' "$pubspec"

echo "PASS: release workflow contract"
