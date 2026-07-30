#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

dwm_repo=https://github.com/rahuljangirworks/dwm-jangir.git
dwm_commit=164d43470736e85a3d878e138f81352166c3297f
tailscale_version=1.98.10
tailscale_archive=tailscale_1.98.10_arm64.tgz
tailscale_sha256=d74a84e07cb1948d9f09a23ae161417c6127e562949773705c95d0762be2809d
tailscale_url=https://pkgs.tailscale.com/stable/tailscale_1.98.10_arm64.tgz

termux_prefix=${PREFIX:-/data/data/com.termux/files/usr}
dwm_source=$HOME/.local/src/dwm-jangir
xdg_config=${XDG_CONFIG_HOME:-$HOME/.config}
xdg_data=${XDG_DATA_HOME:-$HOME/.local/share}

log() {
    printf '\n==> %s\n' "$*"
}

if [ "$(uname -m)" != aarch64 ]; then
    echo "DroidDesk DWM currently supports ARM64 Termux only." >&2
    exit 1
fi

log "Updating Termux repositories"
pkg update -y
pkg install -y x11-repo tur-repo
pkg update -y

log "Installing DWM Rahul dependencies"
pkg install -y \
    termux-x11-nightly xorg-xrandr pulseaudio dbus \
    dwm dmenu st \
    git make clang pkg-config coreutils curl tar \
    libx11 libxft libxinerama libxrender imlib2 libxcb fontconfig freetype \
    wget openssh htop python

for optional_package in picom feh noto-fonts noto-fonts-emoji; do
    pkg install -y "$optional_package" ||
        printf 'Optional package unavailable: %s\n' "$optional_package" >&2
done

log "Fetching pinned dwm-jangir source"
mkdir -p "$(dirname "$dwm_source")"
if [ ! -d "$dwm_source/.git" ]; then
    git clone --no-checkout "$dwm_repo" "$dwm_source"
fi
git -C "$dwm_source" remote set-url origin "$dwm_repo"
git -C "$dwm_source" fetch --depth=1 origin "$dwm_commit"
git -C "$dwm_source" checkout --detach "$dwm_commit"
test "$(git -C "$dwm_source" rev-parse HEAD)" = "$dwm_commit"

log "Building DWM Rahul"
make -C "$dwm_source" clean
make -C "$dwm_source" CC=clang PREFIX="$termux_prefix"
install -m 0755 "$dwm_source/dwm" "$termux_prefix/bin/dwm"
for helper in "$dwm_source"/scripts/*; do
    [ -f "$helper" ] || continue
    [ -x "$helper" ] || continue
    install -m 0755 "$helper" "$termux_prefix/bin/$(basename "$helper")"
done

log "Installing DWM configuration"
mkdir -p "$xdg_config/dwm-titus"
for config_name in hotkeys.toml themes.toml window-rules.toml; do
    if [ ! -e "$xdg_config/dwm-titus/$config_name" ]; then
        install -m 0644 \
            "$dwm_source/config/$config_name" \
            "$xdg_config/dwm-titus/$config_name"
    fi
done

rm -rf "$xdg_config/quickshell"
mkdir -p "$xdg_config/quickshell"
cp -a "$dwm_source/config/quickshell/." "$xdg_config/quickshell/"

mkdir -p "$xdg_data/dwm-titus/scripts"
install -m 0755 "$dwm_source/scripts/autostop.sh" "$xdg_data/dwm-titus/scripts/autostop.sh"
cat >"$xdg_data/dwm-titus/scripts/autostart.sh" <<'AUTOSTART'
#!/data/data/com.termux/files/usr/bin/sh
export DISPLAY="${DISPLAY:-:0}"
export XDG_SESSION_TYPE=x11
export XDG_CURRENT_DESKTOP=dwm
export DESKTOP_SESSION=dwm
if command -v quickshell >/dev/null 2>&1; then
    quickshell_version=$(quickshell --version 2>/dev/null |
        grep -Eo '[0-9]+\.[0-9]+(\.[0-9]+)?' | head -n 1)
    if [ -n "$quickshell_version" ] &&
        [ "$(printf '%s\n' 0.3.0 "$quickshell_version" | sort -V | head -n 1)" = 0.3.0 ]; then
        quickshell --no-duplicate >/dev/null 2>&1 &
    fi
fi
command -v picom >/dev/null 2>&1 &&
    picom --backend xrender >/dev/null 2>&1 &
droiddesk-tailscaled start >/dev/null 2>&1 || true
AUTOSTART
chmod 0755 "$xdg_data/dwm-titus/scripts/autostart.sh"

log "Installing verified Tailscale"
tailscale_work=$(mktemp -d "${TMPDIR:-$termux_prefix/tmp}/droiddesk-tailscale.XXXXXX")
trap 'rm -rf "$tailscale_work"' EXIT
curl -fL --retry 3 "$tailscale_url" -o "$tailscale_work/$tailscale_archive"
printf '%s  %s\n' "$tailscale_sha256" "$tailscale_work/$tailscale_archive" |
    sha256sum -c -
tar -xzf "$tailscale_work/$tailscale_archive" -C "$tailscale_work"
install -m 0755 \
    "$tailscale_work/tailscale_${tailscale_version}_arm64/tailscale" \
    "$termux_prefix/bin/tailscale"
install -m 0755 \
    "$tailscale_work/tailscale_${tailscale_version}_arm64/tailscaled" \
    "$termux_prefix/bin/tailscaled"

cat >"$termux_prefix/bin/droiddesk-tailscaled" <<'TAILSCALE'
#!/data/data/com.termux/files/usr/bin/bash
set -eu
socket="${TMPDIR:-/data/data/com.termux/files/usr/tmp}/tailscaled.sock"
state="$HOME/.local/state/tailscale"
mkdir -p "$state"
case "${1:-status}" in
    start)
        if ! pgrep -f "tailscaled.*$socket" >/dev/null 2>&1; then
            nohup tailscaled \
                --socket="$socket" \
                --state="$state/tailscaled.state" \
                --tun=userspace-networking \
                --socks5-server=127.0.0.1:1055 \
                --outbound-http-proxy-listen=127.0.0.1:1055 \
                >"$state/tailscaled.log" 2>&1 &
        fi
        ;;
    up) tailscale --socket="$socket" up ;;
    status) tailscale --socket="$socket" status ;;
    stop) pkill -f "tailscaled.*$socket" >/dev/null 2>&1 || true ;;
    *) echo "Usage: droiddesk-tailscaled {start|up|status|stop}" >&2; exit 2 ;;
esac
TAILSCALE
chmod 0755 "$termux_prefix/bin/droiddesk-tailscaled"

cat >"$HOME/start-droiddesk.sh" <<'START'
#!/data/data/com.termux/files/usr/bin/bash
set -e
export DISPLAY=:0
export XDG_SESSION_TYPE=x11
export XDG_CURRENT_DESKTOP=dwm
export DESKTOP_SESSION=dwm
pulseaudio --start --exit-idle-time=-1 >/dev/null 2>&1 || true
pgrep -f 'termux-x11.*:0' >/dev/null 2>&1 ||
    termux-x11 :0 >/dev/null 2>&1 &
sleep 1
exec dbus-run-session -- dwm
START
chmod 0755 "$HOME/start-droiddesk.sh"

cat >"$HOME/stop-droiddesk.sh" <<'STOP'
#!/data/data/com.termux/files/usr/bin/bash
pkill -x dwm >/dev/null 2>&1 || true
pkill -x quickshell >/dev/null 2>&1 || true
pkill -x picom >/dev/null 2>&1 || true
droiddesk-tailscaled stop >/dev/null 2>&1 || true
pkill -f 'termux-x11.*:0' >/dev/null 2>&1 || true
STOP
chmod 0755 "$HOME/stop-droiddesk.sh"

log "DWM Rahul setup complete"
echo "Start desktop: bash ~/start-droiddesk.sh"
echo "Connect Tailscale: droiddesk-tailscaled start && droiddesk-tailscaled up"
echo "RustDesk: use the official Android ARM64 app in non-root Termux mode."
echo "LightDM: not started because Android owns the login/display lifecycle."
