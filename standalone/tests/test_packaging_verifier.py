import gzip
import io
import json
import tarfile
import tempfile
import unittest
import zipfile
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from standalone.packaging.verify_apk import inspect_game_source, inspect_payload, verify

ROOT = Path(__file__).resolve().parents[1]


class PackagingVerifierTest(unittest.TestCase):
    def test_requires_exact_pinned_game_source_metadata(self):
        source_path = ROOT / "packaging" / "game-source.json"
        source = json.loads(source_path.read_text(encoding="utf-8"))
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w") as apk:
            apk.writestr("assets/game-source.json", json.dumps(source))
        with zipfile.ZipFile(io.BytesIO(buffer.getvalue())) as apk:
            errors = []
            found = inspect_game_source(apk, errors)
        self.assertEqual(errors, [])
        self.assertEqual(found["sha256"], source["sha256"])
        self.assertEqual(found["jarSha256"], source["jarSha256"])

    def test_rejects_runtime_archive_with_game_files(self):
        payload_buffer = io.BytesIO()
        with gzip.GzipFile(fileobj=payload_buffer, mode="wb", mtime=0) as compressed:
            with tarfile.open(fileobj=compressed, mode="w") as archive:
                content = b"jar"
                member = tarfile.TarInfo("game/pokewilds.jar")
                member.size = len(content)
                archive.addfile(member, io.BytesIO(content))
        apk_buffer = io.BytesIO()
        with zipfile.ZipFile(apk_buffer, "w") as apk:
            apk.writestr("assets/runtime.bin", payload_buffer.getvalue())
        with zipfile.ZipFile(io.BytesIO(apk_buffer.getvalue())) as apk:
            errors = []
            result = inspect_payload(apk, "assets/runtime.bin", errors)
        self.assertTrue(result["containsGame"])
        self.assertIn("runtime payload contains bundled game files", errors)

    def test_rejects_game_jar_as_direct_apk_asset(self):
        with tempfile.TemporaryDirectory() as folder:
            apk_path = Path(folder) / "sample.apk"
            with zipfile.ZipFile(apk_path, "w") as apk:
                apk.writestr("assets/game/pokewilds.jar", b"jar")
            result, status = verify(apk_path, None, None)
        self.assertEqual(status, 1)
        self.assertTrue(any("direct game files" in error for error in result["errors"]))


if __name__ == "__main__":
    unittest.main()
