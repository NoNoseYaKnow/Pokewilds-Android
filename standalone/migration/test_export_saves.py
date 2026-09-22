#!/usr/bin/env python3

import io
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).parent))
from export_saves import ExportError, export_archive  # noqa: E402


class ExportSavesTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="pokewilds-export-test-")
        self.root = Path(self.temp.name) / "game"
        self.root.mkdir()
        (self.root / "pokewilds.jar").write_bytes(b"jar")
        (self.root / "settings.txt").write_text("settings")
        (self.root / "mods").mkdir()
        (self.root / "mods" / "user.mod").write_text("mod")
        self._world("alpha.sav", "alpha")
        self._world("beta.sav", "beta")
        (self.root / "alpha.sav.zip").write_bytes(b"alpha-backup")

    def tearDown(self):
        self.temp.cleanup()

    def _world(self, name: str, value: str):
        world = self.root / name
        world.mkdir()
        for filename in ("game.json.zip", "map001.json.zip", "spawnplayer001.json.zip"):
            with zipfile.ZipFile(world / filename, "w") as save:
                save.writestr("data.json", value)

    def _archive(self, selector=None):
        output = io.BytesIO()
        export_archive(self.root, output, selector)
        with zipfile.ZipFile(io.BytesIO(output.getvalue())) as archive:
            return set(archive.namelist()), archive.read("alpha.sav.zip") if "alpha.sav.zip" in archive.namelist() else None

    def test_selected_world_includes_related_backup_settings_and_mods(self):
        names, backup = self._archive("alpha")
        self.assertIn("pokewilds-save-manifest.json", names)
        self.assertIn("settings.txt", names)
        self.assertIn("mods/user.mod", names)
        self.assertIn("alpha.sav/game.json.zip", names)
        self.assertIn("alpha.sav.zip", names)
        self.assertNotIn("beta.sav/game.json.zip", names)
        self.assertEqual(backup, b"alpha-backup")

    def test_invalid_selection_and_traversal_are_rejected(self):
        for selector in ("missing", "../alpha.sav", "alpha.sav/../beta.sav"):
            with self.subTest(selector=selector), self.assertRaises(ExportError):
                self._archive(selector)

    def test_symlink_is_rejected(self):
        link = self.root / "alpha.sav" / "map-link.json.zip"
        try:
            link.symlink_to(self.root / "alpha.sav" / "map001.json.zip")
        except (OSError, NotImplementedError):
            self.skipTest("symlinks unavailable")
        with self.assertRaises(ExportError):
            self._archive("alpha.sav")

    def test_malformed_json_save_is_rejected(self):
        (self.root / "beta.sav" / "map001.json.zip").write_bytes(b"not-a-zip")
        with self.assertRaises(ExportError):
            self._archive("beta.sav")


if __name__ == "__main__":
    unittest.main()
