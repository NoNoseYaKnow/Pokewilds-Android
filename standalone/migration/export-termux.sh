#!/data/data/com.termux/files/usr/bin/bash
# Run in the original Termux installation after saving and quitting.
set -euo pipefail
if pgrep -f '[j]ava .*pokewilds.jar' >/dev/null; then
 echo 'Save and quit PokeWilds before exporting.' >&2
 exit 1
fi
output="$HOME/pokewilds-saves.zip"
stage="$output.preparing"
trap 'rm -f "$stage"' EXIT
proot-distro login ubuntu --shared-tmp -- python3 - <<'PY' > "$stage"
import json, pathlib, sys, zipfile

MANIFEST='pokewilds-save-manifest.json'
GAME_VERSION='0.8.11'

def validate_json_save(path):
    try:
        with zipfile.ZipFile(path) as save:
            info=save.getinfo('data.json')
            if info.is_dir() or info.file_size == 0 or not save.read(info).strip():
                raise SystemExit(f'Invalid empty save data: {path}')
    except (KeyError, zipfile.BadZipFile) as error:
        raise SystemExit(f'Invalid JSON save archive: {path}: {error}')

def validate_world(path):
    if not path.is_dir(): raise SystemExit(f'World is not a directory: {path.name}')
    entries=list(path.iterdir())
    files=[p for p in entries if p.is_file()]
    if any(p.is_dir() for p in entries):
        raise SystemExit(f'World contains a nested directory: {path.name}')
    game=path/'game.json.zip'
    maps=[p for p in files if p.name.startswith('map') and p.name.endswith('.json.zip')]
    spawns=[p for p in files if p.name.startswith('spawn') and p.name.endswith('.json.zip')]
    if not game.is_file() or not maps or not spawns:
        raise SystemExit(f'World is empty or missing game/map/spawn JSON saves: {path.name}')
    for save in [game, *maps, *spawns]: validate_json_save(save)

roots=[pathlib.Path('/root/pokewilds'),pathlib.Path('/root/pokewilds-download/pokewilds-v0.8.11-otherplatforms')]
root=next((p for p in roots if (p/'pokewilds.jar').is_file()),None)
if root is None: raise SystemExit('Cannot find the installed game')
items=[]
for item in sorted(root.iterdir()):
 if item.name in ('settings.txt','mods'):
  items.append(item)
 elif item.name.endswith('.sav'):
  validate_world(item)
  items.append(item)
 elif item.name.endswith('.sav.zip'):
  if not item.is_file(): raise SystemExit(f'World backup is not a file: {item.name}')
  items.append(item)
with zipfile.ZipFile(sys.stdout.buffer,'w',zipfile.ZIP_DEFLATED) as archive:
 archive.writestr(MANIFEST, json.dumps({'schema':1,'game_version':GAME_VERSION}, separators=(',',':'))+'\n')
 for item in items:
  paths=item.rglob('*') if item.is_dir() else [item]
  for p in paths:
   if p.is_file() and not p.is_symlink(): archive.write(p,p.relative_to(root))
PY
mv "$stage" "$output"
printf 'Saved a copy to %s\nUse Android Share to send it to Downloads, then Import saves in the standalone app.\n' "$output"
