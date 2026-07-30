#!/usr/bin/env bash
set -euo pipefail

repo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
profile="$repo_dir/app/android/app/src/main/kotlin/com/orailnoor/droiddesk/runtime/DwmJangirProfile.kt"
chroot_runtime="$repo_dir/app/android/app/src/main/kotlin/com/orailnoor/droiddesk/runtime/ChrootRuntime.kt"
linux_runtime="$repo_dir/app/android/app/src/main/kotlin/com/orailnoor/droiddesk/runtime/LinuxRuntime.kt"
main_activity="$repo_dir/app/android/app/src/main/kotlin/com/orailnoor/droiddesk/MainActivity.kt"
desktop_activity="$repo_dir/app/android/app/src/main/kotlin/com/orailnoor/droiddesk/view/DesktopActivity.kt"
app_state="$repo_dir/app/lib/state/app_state.dart"
picker="$repo_dir/app/lib/screens/setup/de_picker.dart"

grep -q 'const val DESKTOP_ID = "dwm-jangir"' "$profile"
grep -q 'const val SUPPORTED_NATIVE_ABI = "arm64-v8a"' "$profile"
grep -q '164d43470736e85a3d878e138f81352166c3297f' "$profile"
grep -q 'd74a84e07cb1948d9f09a23ae161417c6127e562949773705c95d0762be2809d' "$profile"
grep -q 'ce62c996f14d33f3bbe3a330e953644a44bace7f05885a7953f7395d69fb49c0' "$profile"
grep -q 'DwmJangirProfile.install' "$chroot_runtime"
grep -q 'DwmJangirProfile.install' "$linux_runtime"
grep -q 'DwmJangirProfile.isInstalled' "$chroot_runtime"
grep -q 'DwmJangirProfile.isInstalled' "$linux_runtime"
grep -q 'xdg_data/dwm-titus/scripts' "$repo_dir/termux-dwm-setup.sh"

grep -q "String _selectedDE = 'dwm-jangir';" "$app_state"
grep -q "id: 'dwm-jangir'" "$picker"
if grep -Eq '"xfce4"|'\''xfce4'\''' "$main_activity" "$desktop_activity" "$app_state" "$picker"; then
    echo "Active app setup still contains an XFCE desktop default." >&2
    exit 1
fi

grep -q 'installDwmJangirNative' \
    "$repo_dir/app/android/app/src/main/kotlin/com/orailnoor/droiddesk/runtime/LinuxRuntime.kt"
grep -q 'installDwmJangir' \
    "$repo_dir/app/android/app/src/main/kotlin/com/orailnoor/droiddesk/runtime/ChrootRuntime.kt"
grep -q 'Android host-managed; device-specific updates only' "$main_activity"

echo "PASS: DWM Rahul profile contract"
