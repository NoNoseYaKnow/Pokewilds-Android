package local.pokewilds.standalone;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.*;

public class SaveArchiveTest {
    @Test public void roundTripPreservesWorldSettingsAndExcludesJar() throws Exception {
        Path root = Files.createTempDirectory("save-test");
        try {
            Path game = root.resolve("source");
            createWorld(game.resolve("world.sav"));
            Files.write(game.resolve("settings.txt"), "source-settings".getBytes(StandardCharsets.UTF_8));
            Files.write(game.resolve("pokewilds.jar"), new byte[]{7});
            byte[] mapSave = Files.readAllBytes(game.resolve("world.sav/map001.json.zip"));
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            SaveArchive.exportTo(game, data);

            Path restored = root.resolve("restored");
            Files.createDirectories(restored);
            Files.write(restored.resolve("settings.txt"), "existing-settings".getBytes(StandardCharsets.UTF_8));
            SaveArchive.importFrom(new ByteArrayInputStream(data.toByteArray()), restored);
            assertArrayEquals(mapSave, Files.readAllBytes(restored.resolve("world.sav/map001.json.zip")));
            assertEquals("existing-settings", new String(Files.readAllBytes(restored.resolve("settings.txt")), StandardCharsets.UTF_8));
            assertFalse(Files.exists(restored.resolve("pokewilds.jar")));
            try {
                SaveArchive.importFrom(new ByteArrayInputStream(data.toByteArray()), restored);
                fail("collision");
            } catch (IOException expected) { }
            assertArrayEquals(mapSave, Files.readAllBytes(restored.resolve("world.sav/map001.json.zip")));
        } finally { SafeTar.deleteTree(root); }
    }

    @Test public void rejectsUnsupportedManifestWithoutChangingTarget() throws Exception {
        Path root = Files.createTempDirectory("save-test");
        try {
            Path game = root.resolve("game");
            Files.createDirectories(game);
            Files.write(game.resolve("settings.txt"), "keep".getBytes(StandardCharsets.UTF_8));
            byte[] archive = archiveWithManifest("{\"schema\":2,\"game_version\":\"0.8.11\"}", true);
            try {
                SaveArchive.importFrom(new ByteArrayInputStream(archive), game);
                fail("unsupported manifest");
            } catch (IOException expected) { }
            assertEquals("keep", new String(Files.readAllBytes(game.resolve("settings.txt")), StandardCharsets.UTF_8));
            assertFalse(Files.exists(game.resolve("world.sav")));
        } finally { SafeTar.deleteTree(root); }
    }

    @Test public void rejectsEmptyWorldWithoutChangingTarget() throws Exception {
        Path root = Files.createTempDirectory("save-test");
        try {
            Path game = root.resolve("game");
            Files.createDirectories(game);
            Files.write(game.resolve("settings.txt"), "keep".getBytes(StandardCharsets.UTF_8));
            byte[] archive = archiveWithManifest("{\"schema\":1,\"game_version\":\"0.8.11\"}", false);
            try {
                SaveArchive.importFrom(new ByteArrayInputStream(archive), game);
                fail("empty world");
            } catch (IOException expected) { }
            assertEquals("keep", new String(Files.readAllBytes(game.resolve("settings.txt")), StandardCharsets.UTF_8));
            assertFalse(Files.exists(game.resolve("empty.sav")));
        } finally { SafeTar.deleteTree(root); }
    }

    @Test public void rejectsTrailingOrDuplicateManifestWithoutChangingTarget() throws Exception {
        Path root = Files.createTempDirectory("save-test");
        try {
            Path game = root.resolve("game");
            Files.createDirectories(game);
            Files.write(game.resolve("settings.txt"), "keep".getBytes(StandardCharsets.UTF_8));
            String[] manifests = {
                "{\"schema\":1,\"game_version\":\"0.8.11\"} trailing",
                "{\"schema\":1,\"schema\":1,\"game_version\":\"0.8.11\"}"
            };
            for (String manifest : manifests) {
                try {
                    SaveArchive.importFrom(new ByteArrayInputStream(archiveWithManifest(manifest, true)), game);
                    fail("malformed manifest");
                } catch (IOException expected) { }
                assertEquals("keep", new String(Files.readAllBytes(game.resolve("settings.txt")), StandardCharsets.UTF_8));
                assertFalse(Files.exists(game.resolve("world.sav")));
            }
        } finally { SafeTar.deleteTree(root); }
    }

    @Test public void traversalRejectedWithoutChangingData() throws Exception {
        Path root = Files.createTempDirectory("save-test");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream z = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                putEntry(z, "pokewilds-save-manifest.json", "{\"schema\":1,\"game_version\":\"0.8.11\"}");
                putEntry(z, "world.sav/../../escape", "1");
            }
            try {
                SaveArchive.importFrom(new ByteArrayInputStream(bytes.toByteArray()), root.resolve("game"));
                fail("traversal");
            } catch (IOException expected) { }
            assertFalse(Files.exists(root.resolve("escape")));
        } finally { SafeTar.deleteTree(root); }
    }

    private static void createWorld(Path world) throws IOException {
        Files.createDirectories(world);
        writeJsonSave(world.resolve("game.json.zip"), "game");
        writeJsonSave(world.resolve("map001.json.zip"), "map");
        writeJsonSave(world.resolve("spawnplayer001.json.zip"), "spawn");
        Files.write(world.resolve("map001.png"), new byte[]{4, 5});
    }

    private static void writeJsonSave(Path output, String data) throws IOException {
        try (OutputStream stream = Files.newOutputStream(output);
             ZipOutputStream zip = new ZipOutputStream(stream, StandardCharsets.UTF_8)) {
            putEntry(zip, "data.json", data);
        }
    }

    private static byte[] archiveWithManifest(String manifest, boolean validWorld) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            putEntry(zip, "pokewilds-save-manifest.json", manifest);
            if (validWorld) {
                putEntry(zip, "world.sav/", (byte[]) null);
                putEntry(zip, "world.sav/game.json.zip", jsonSaveBytes("game"));
                putEntry(zip, "world.sav/map001.json.zip", jsonSaveBytes("map"));
                putEntry(zip, "world.sav/spawnplayer001.json.zip", jsonSaveBytes("spawn"));
            } else {
                putEntry(zip, "empty.sav/", (byte[]) null);
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] jsonSaveBytes(String data) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            putEntry(zip, "data.json", data);
        }
        return bytes.toByteArray();
    }

    private static void putEntry(ZipOutputStream zip, String name, String data) throws IOException {
        putEntry(zip, name, data == null ? null : data.getBytes(StandardCharsets.UTF_8));
    }

    private static void putEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        if (data != null) zip.write(data);
        zip.closeEntry();
    }
}
