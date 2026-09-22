#!/data/data/com.termux/files/usr/bin/bash
set -eu
export HOME=/data/data/com.termux/files/home PREFIX=/data/data/com.termux/files/usr
export PATH=$PREFIX/bin:/system/bin TMPDIR=$PREFIX/tmp DISPLAY=:0
# A notification can be tapped before Java finishes opening its window.
for _attempt in $(seq 1 60); do
    window=$(xdotool search --name '^PokeWilds$' 2>/dev/null | head -n 1 || true)
    if [ -n "$window" ]; then
        exec "$HOME/.local/bin/pokewilds-close" "$window"
    fi
    if flock -n "$HOME/.pokewilds-launch.lock" true; then
        exit 0
    fi
    sleep 1
done
printf 'PokeWilds has not opened a window yet; try Quit again.\n' >&2
exit 1
