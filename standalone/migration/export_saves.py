#!/usr/bin/env python3
"""Validate and export a PokeWilds 0.8.11 save directory to a ZIP archive."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import sys
from typing import BinaryIO, Iterable
import zipfile


MANIFEST = "pokewilds-save-manifest.json"
GAME_VERSION = "0.8.11"


class ExportError(Exception):
    pass


def _reject_symlink(path: Path) -> None:
    if path.is_symlink():
        raise ExportError(f"Refusing symlink in save data: {path}")


def _regular_file(path: Path, label: str) -> None:
    _reject_symlink(path)
    if not path.is_file():
        raise ExportError(f"{label} is not a regular file: {path.name}")


def _iter_files(path: Path) -> Iterable[Path]:
    _reject_symlink(path)
    if path.is_file():
        yield path
        return
    if not path.is_dir():
        raise ExportError(f"Expected a file or directory: {path}")
    for directory, directories, files in os.walk(path, followlinks=False):
        directory_path = Path(directory)
        for name in sorted(directories):
            _reject_symlink(directory_path / name)
        for name in sorted(files):
            candidate = directory_path / name
            _regular_file(candidate, "Save entry")
            yield candidate


def _validate_json_save(path: Path) -> None:
    _regular_file(path, "JSON save")
    try:
        with zipfile.ZipFile(path) as save:
            info = save.getinfo("data.json")
            if info.is_dir() or info.file_size == 0 or not save.read(info).strip():
                raise ExportError(f"Invalid empty save data: {path}")
    except (KeyError, zipfile.BadZipFile) as error:
        raise ExportError(f"Invalid JSON save archive: {path}: {error}") from error


def _validate_world(path: Path) -> None:
    _reject_symlink(path)
    if not path.is_dir():
        raise ExportError(f"World is not a directory: {path.name}")
    entries = sorted(path.iterdir())
    files = []
    for entry in entries:
        _reject_symlink(entry)
        if entry.is_dir():
            raise ExportError(f"World contains a nested directory: {path.name}")
        _regular_file(entry, "World entry")
        files.append(entry)
    game = path / "game.json.zip"
    maps = [entry for entry in files if entry.name.startswith("map") and entry.name.endswith(".json.zip")]
    spawns = [entry for entry in files if entry.name.startswith("spawn") and entry.name.endswith(".json.zip")]
    if not game.is_file() or not maps or not spawns:
        raise ExportError(f"World is empty or missing game/map/spawn JSON saves: {path.name}")
    for save in [game, *maps, *spawns]:
        _validate_json_save(save)


def _normalize_world(selector: str) -> str:
    if not selector or selector in {".", ".."} or "/" in selector or "\\" in selector:
        raise ExportError("World selection must be a single basename")
    if selector.endswith(".sav.zip"):
        world = selector[:-4]
    elif selector.endswith(".sav"):
        world = selector
    else:
        world = selector + ".sav"
    if world in {".sav", "..sav"} or not world.endswith(".sav"):
        raise ExportError("World selection must name a .sav world")
    return world


def _find_root() -> Path:
    candidates = (Path("/root/pokewilds"), Path("/root/pokewilds-download/pokewilds-v0.8.11-otherplatforms"))
    for candidate in candidates:
        if candidate.is_dir() and (candidate / "pokewilds.jar").is_file():
            return candidate
    raise ExportError("Cannot find the installed game")


def _top_items(root: Path, selector: str | None) -> list[Path]:
    settings = root / "settings.txt"
    mods = root / "mods"
    items: list[Path] = []
    if settings.exists() or settings.is_symlink():
        _regular_file(settings, "Settings")
        items.append(settings)
    if mods.exists() or mods.is_symlink():
        _reject_symlink(mods)
        if not mods.is_dir():
            raise ExportError("Mods is not a directory")
        items.append(mods)

    if selector is not None:
        world_name = _normalize_world(selector)
        world = root / world_name
        _validate_world(world)
        items.append(world)
        backup = root / (world_name + ".zip")
        if backup.exists() or backup.is_symlink():
            _regular_file(backup, "World backup")
            items.append(backup)
        return items

    for item in sorted(root.iterdir()):
        name = item.name
        if name in {"settings.txt", "mods"}:
            continue
        if name.endswith(".sav"):
            _validate_world(item)
            items.append(item)
        elif name.endswith(".sav.zip"):
            _regular_file(item, "World backup")
            items.append(item)
    return items


def export_archive(root: Path, output: BinaryIO, selector: str | None = None) -> None:
    _reject_symlink(root)
    if not root.is_dir() or not (root / "pokewilds.jar").is_file():
        raise ExportError("Cannot find the installed game")
    items = _top_items(root, selector)
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(MANIFEST, json.dumps({"schema": 1, "game_version": GAME_VERSION}, separators=(",", ":")) + "\n")
        for item in items:
            for path in _iter_files(item):
                archive.write(path, path.relative_to(root).as_posix())


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("world", nargs="?", help="world basename, with optional .sav or .sav.zip suffix")
    parser.add_argument("--root", type=Path, default=None, help=argparse.SUPPRESS)
    args = parser.parse_args(argv)
    try:
        export_archive(args.root or _find_root(), sys.stdout.buffer, args.world)
    except (ExportError, OSError) as error:
        print(f"export failed: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
