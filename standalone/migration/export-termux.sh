#!/data/data/com.termux/files/usr/bin/bash
# Run in the original Termux installation after saving and quitting.
# Usage: ./export-termux.sh [world.sav|world-name]
set -euo pipefail

if (($# > 1)); then
  echo "Usage: $0 [world.sav|world-name]" >&2
  exit 2
fi
if pgrep -f '[j]ava .*pokewilds.jar' >/dev/null; then
  echo 'Save and quit PokeWilds before exporting.' >&2
  exit 1
fi

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
output="$HOME/pokewilds-saves.zip"
stage="$output.preparing"
trap 'rm -f "$stage"' EXIT

if (($# == 1)); then
  proot-distro login ubuntu --shared-tmp -- python3 - -- "$1" < "$script_dir/export_saves.py" > "$stage"
  selection=" for world $1"
else
  proot-distro login ubuntu --shared-tmp -- python3 - < "$script_dir/export_saves.py" > "$stage"
  selection=""
fi
mv "$stage" "$output"
printf 'Saved a copy%s to %s\n' "$selection" "$output"
cat <<'EOF'

Copy the archive to Android before opening PokeWilds Standalone:
  termux-share "$HOME/pokewilds-saves.zip"
Then choose Save/Downloads in the Android share sheet. If termux-share is
unavailable, run termux-setup-storage once, then:
  cp "$HOME/pokewilds-saves.zip" "$HOME/storage/downloads/"
In the standalone launcher choose Manage saves -> Import saves and select the
archive from Downloads. The original Termux world is left in place.
EOF
