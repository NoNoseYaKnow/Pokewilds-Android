#!/data/data/com.termux/files/usr/bin/bash
# Run inside Termux from a complete checkout of this repository.
set -euo pipefail
cd "$(dirname "$0")"
REPO_DIR="$PWD"
export DEBIAN_FRONTEND=noninteractive
export TMPDIR="$PREFIX/tmp"

pkg update -y
pkg install -y x11-repo
pkg install -y termux-x11-nightly pulseaudio proot-distro wget xdotool virglrenderer-android clang libx11 util-linux

# Reuse existing Ubuntu installations and game saves.
if ! proot-distro login ubuntu -- true >/dev/null 2>&1; then
    proot-distro install ubuntu
fi
proot-distro login ubuntu --shared-tmp -- bash -s <<'UBUNTU'
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends openjdk-17-jre libgl1 libxrandr2 libxcursor1 libxinerama1 libopenal1 libalut0 libgles2 libglfw3 libgl1-mesa-dri mesa-utils unzip wget ca-certificates
if [ ! -f /root/pokewilds/pokewilds.jar ] && [ ! -f /root/pokewilds-download/pokewilds-v0.8.11-otherplatforms/pokewilds.jar ]; then
    if [ -e /root/pokewilds ]; then
        echo '/root/pokewilds already exists but has no pokewilds.jar; leaving it untouched.' >&2
        exit 1
    fi
    download_dir=$(mktemp -d)
    trap 'rm -rf "$download_dir"' EXIT
    wget -nv -O "$download_dir/game.zip" https://github.com/SheerSt/pokewilds/releases/download/v0.8.11/pokewilds-otherplatforms.zip
    unzip -q "$download_dir/game.zip" -d "$download_dir"
    test -f "$download_dir/pokewilds-v0.8.11-otherplatforms/pokewilds.jar"
    mv "$download_dir/pokewilds-v0.8.11-otherplatforms" /root/pokewilds
fi
UBUNTU

mkdir -p "$HOME/.local/bin"
clang "$REPO_DIR/pokewilds-close.c" -lX11 -o "$HOME/.local/bin/pokewilds-close"
for script in pokewilds.sh pokewilds-quit.sh; do
    if [ -f "$HOME/$script" ]; then
        cp -n "$HOME/$script" "$HOME/$script.before-gpu-launcher"
    fi
    install -m 700 "$REPO_DIR/$script" "$HOME/$script"
done
printf '\nInstalled. Run ~/pokewilds.sh to play.\n'
printf 'For the optional Android launcher APK and ES-DE setup, see README.md.\n'
