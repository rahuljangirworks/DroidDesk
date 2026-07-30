#!/usr/bin/env bash
set -euo pipefail

repo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_dir"

bash -n scripts/check.sh termux-dwm-setup.sh termux-linux-setup.sh tests/*.sh

for test_script in tests/*.sh; do
    "$test_script"
done

if command -v flutter >/dev/null 2>&1; then
    (
        cd app
        flutter pub get
        dart format --output=none --set-exit-if-changed lib test test_ffi.dart
        flutter analyze --fatal-infos
        flutter test
    )
else
    echo "SKIP: Flutter toolchain is not installed; CI runs Flutter checks."
fi

if command -v shellcheck >/dev/null 2>&1; then
    shellcheck scripts/check.sh termux-dwm-setup.sh tests/*.sh
else
    echo "SKIP: shellcheck is not installed."
fi

git diff --check
echo "DroidDesk checks passed."
