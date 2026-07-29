#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

# Compatibility entrypoint for older DroidDesk install commands. The DWM Rahul
# installer is the only supported desktop setup.
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" 2>/dev/null && pwd)
if [ -x "$script_dir/termux-dwm-setup.sh" ]; then
    exec "$script_dir/termux-dwm-setup.sh" "$@"
fi

bootstrap_script="${TMPDIR:-/data/data/com.termux/files/usr/tmp}/termux-dwm-setup.sh"
curl -fL --retry 3 \
    https://raw.githubusercontent.com/rahuljangirworks/DroidDesk/main/termux-dwm-setup.sh \
    -o "$bootstrap_script"
chmod 0755 "$bootstrap_script"
exec "$bootstrap_script" "$@"
