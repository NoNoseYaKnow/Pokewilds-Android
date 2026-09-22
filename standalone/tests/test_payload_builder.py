#!/usr/bin/env python3
import hashlib
import json
import subprocess
import tarfile
import tempfile
import unittest
import zipfile
from pathlib import Path


RUNTIME = Path(__file__).resolve().parents[1] / "runtime"
TOOL = RUNTIME / "payload_tool.py"
LOCK = RUNTIME / "manifest.json"


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class PayloadBuilderTest(unittest.TestCase):
    def test_lock_fails_closed_when_a_dependency_is_unpinned(self):
        with tempfile.TemporaryDirectory() as temp:
            manifest = json.loads(LOCK.read_text(encoding="utf-8"))
            manifest["inputs"][0]["sha256"] = None
            manifest["inputs"][-1]["artifacts"] = []
            manifest["inputs"][-1]["sha256"] = None
            manifest_path = Path(temp) / "manifest.json"
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            result = subprocess.run(
                ["python3", str(TOOL), "build", "--manifest", str(manifest_path), "--cache", temp, "--output", f"{temp}/payload.tar.gz", "--offline"],
                text=True,
                capture_output=True,
            )
        self.assertEqual(result.returncode, 2)
        self.assertIn("ubuntu-base-24.04.3-arm64: missing pinned SHA-256", result.stderr)
        self.assertIn("package closure has no pinned artifact list", result.stderr)

    def test_game_release_cannot_be_added_to_runtime_inputs(self):
        manifest = json.loads(LOCK.read_text(encoding="utf-8"))
        manifest["inputs"].append({"id": "game", "role": "game-release", "url": "https://example.invalid/game.zip", "sha256": "0" * 64, "format": "zip", "extract_to": "game"})
        from importlib.util import spec_from_file_location, module_from_spec
        spec = spec_from_file_location("payload_tool", TOOL)
        module = module_from_spec(spec)
        assert spec and spec.loader
        spec.loader.exec_module(module)
        self.assertTrue(any("game files must be acquired separately" in error for error in module.validate_lock(manifest)))

    def test_fixture_build_is_offline_deterministic_and_verifiable(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            cache = root / "cache"
            cache.mkdir()

            base = cache / "base"
            with tarfile.open(base, "w:gz") as archive:
                for name, contents in (
                    ("lib/aarch64-linux-gnu/libc.so.6", b"glibc"),
                    ("usr/lib/aarch64-linux-gnu/dri/virgl_dri.so", b"virgl"),
                ):
                    info = tarfile.TarInfo(name)
                    info.size = len(contents)
                    info.mode = 0o755
                    archive.addfile(info, __import__("io").BytesIO(contents))

            jre = cache / "jre"
            with tarfile.open(jre, "w:gz") as archive:
                for name, contents in (
                    ("jdk/bin/java", b"java"),
                    ("jdk/lib/server/libjvm.so", b"jvm"),
                ):
                    info = tarfile.TarInfo(name)
                    info.size = len(contents)
                    info.mode = 0o755
                    archive.addfile(info, __import__("io").BytesIO(contents))

            manifest = {
                "schema": 1,
                "payload_id": "fixture",
                "target": {"arch": "aarch64", "abi": "arm64-v8a", "os": "linux", "libc": "glibc", "rootfs": "fixture"},
                "inputs": [
                    {"id": "base", "url": "https://example.invalid/base", "sha256": digest(base), "format": "tar.gz", "extract_to": "rootfs"},
                    {"id": "jre", "url": "https://example.invalid/jre", "sha256": digest(jre), "format": "tar.gz", "extract_to": "rootfs/opt/pokewilds/jre", "strip_components": 1},
                ],
                "required_paths": [
                    "rootfs/lib/aarch64-linux-gnu/libc.so.6",
                    "rootfs/usr/lib/aarch64-linux-gnu/dri/virgl_dri.so",
                    "rootfs/opt/pokewilds/jre/bin/java",
                    "rootfs/opt/pokewilds/jre/lib/server/libjvm.so",
                    "rootfs/usr/bin/java",
                ],
            }
            manifest_path = root / "manifest.json"
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            output = root / "payload.tar.gz"
            command = [
                "python3", str(TOOL), "build", "--manifest", str(manifest_path),
                "--cache", str(cache), "--output", str(output), "--offline", "--log", str(root / "build.log"),
            ]
            first = subprocess.run(command, text=True, capture_output=True)
            second_output = root / "second.tar.gz"
            second_command = list(command)
            second_command[second_command.index("--output") + 1] = str(second_output)
            second_command[second_command.index("--log") + 1] = str(root / "second.log")
            second = subprocess.run(second_command, text=True, capture_output=True)
            self.assertEqual(first.returncode, 0, first.stderr)
            self.assertEqual(second.returncode, 0, second.stderr)
            self.assertEqual(output.read_bytes(), second_output.read_bytes())
            verified = subprocess.run(["python3", str(TOOL), "verify", str(output)], text=True, capture_output=True)
            self.assertEqual(verified.returncode, 0, verified.stderr)
            properties = (root / "payload.properties").read_text(encoding="utf-8")
            self.assertIn(f"sha256={digest(output)}", properties)
            self.assertIn("expandedBytes=", properties)
            self.assertIn("gameBytes=0", properties)
            expanded = int(next(line.split("=", 1)[1] for line in properties.splitlines() if line.startswith("expandedBytes=")))
            with tarfile.open(output, "r:gz") as archive:
                members = archive.getmembers()
                archive_bytes = sum(member.size for member in members if member.isfile())
                self.assertFalse(any(member.name == "game" or member.name.startswith("game/") for member in members))
            self.assertGreaterEqual(expanded, archive_bytes)

            tampered = root / "tampered.tar.gz"
            archive_bytes = bytearray(output.read_bytes())
            archive_bytes[len(archive_bytes) // 2] ^= 1
            tampered.write_bytes(archive_bytes)
            rejected = subprocess.run(["python3", str(TOOL), "verify", str(tampered)], text=True, capture_output=True)
            self.assertNotEqual(rejected.returncode, 0)


if __name__ == "__main__":
    unittest.main()
