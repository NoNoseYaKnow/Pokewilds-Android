package local.pokewilds.standalone;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Copies current 0.8.11 JSON-in-zip save data; it never imports game files or another app. */
public final class SaveArchive {
    private static final long LIMIT = 2L * 1024 * 1024 * 1024;
    private static final int MANIFEST_LIMIT = 16 * 1024;
    private static final String MANIFEST = "pokewilds-save-manifest.json";
    private static final int MANIFEST_SCHEMA = 1;
    private static final String GAME_VERSION = "0.8.11";

    private SaveArchive() {}

    private static boolean allowed(String name) {
        String top = name.split("/", 2)[0];
        return top.equals("settings.txt") || top.equals("mods") || top.endsWith(".sav") || top.endsWith(".sav.zip");
    }

    public static void exportTo(Path game, OutputStream destination) throws IOException {
        validateExportRoot(game);
        try (ZipOutputStream zip = new ZipOutputStream(destination, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(MANIFEST));
            zip.write(manifestBytes());
            zip.closeEntry();
            Files.walkFileTree(game, new SimpleFileVisitor<Path>() {
                public FileVisitResult preVisitDirectory(Path p, java.nio.file.attribute.BasicFileAttributes a) {
                    return p.equals(game) || allowed(game.relativize(p).toString()) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
                }
                public FileVisitResult visitFile(Path p, java.nio.file.attribute.BasicFileAttributes a) throws IOException {
                    String name = game.relativize(p).toString().replace(File.separatorChar, '/');
                    if (allowed(name) && a.isRegularFile()) {
                        zip.putNextEntry(new ZipEntry(name));
                        Files.copy(p, zip);
                        zip.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    public static void importFrom(InputStream source, Path game) throws IOException {
        Path stage = game.resolveSibling("save-import.preparing");
        SafeTar.deleteTree(stage);
        Files.createDirectories(stage);
        Set<String> tops = new HashSet<>();
        Set<Path> seen = new HashSet<>();
        long size = 0;
        int count = 0;
        boolean manifestSeen = false;
        try {
            try (ZipInputStream zip = new ZipInputStream(source, StandardCharsets.UTF_8)) {
                ZipEntry e;
                byte[] buffer = new byte[65536];
                while ((e = zip.getNextEntry()) != null) {
                    if (++count > 100000) throw new IOException("Too many save archive entries");
                    String name = e.getName();
                    if (MANIFEST.equals(name)) {
                        if (manifestSeen || e.isDirectory()) throw new IOException("Invalid save archive manifest");
                        validateManifest(readBounded(zip, MANIFEST_LIMIT));
                        manifestSeen = true;
                        zip.closeEntry();
                        continue;
                    }
                    Path relative;
                    try { relative = Paths.get(name); }
                    catch (InvalidPathException error) { throw new IOException("Unsupported save archive path: " + name, error); }
                    Path out = stage.resolve(relative).normalize();
                    if (relative.isAbsolute() || name.contains("\\") || !out.startsWith(stage) || !allowed(name)) {
                        throw new IOException("Unsupported save archive path: " + name);
                    }
                    if (relative.getNameCount() == 0) throw new IOException("Empty save archive path");
                    String top = relative.getName(0).toString();
                    if (top.equals("settings.txt") && (e.isDirectory() || relative.getNameCount() != 1)) {
                        throw new IOException("Invalid settings entry");
                    }
                    if (!seen.add(out)) throw new IOException("Duplicate save archive path");
                    tops.add(top);
                    if (e.isDirectory()) {
                        Files.createDirectories(out);
                        zip.closeEntry();
                        continue;
                    }
                    if (out.getParent() == null) throw new IOException("Invalid save archive path: " + name);
                    Files.createDirectories(out.getParent());
                    try (OutputStream output = Files.newOutputStream(out, StandardOpenOption.CREATE_NEW)) {
                        int n;
                        while ((n = zip.read(buffer)) != -1) {
                            size += n;
                            if (size > LIMIT) throw new IOException("Save archive is too large");
                            output.write(buffer, 0, n);
                        }
                    }
                    zip.closeEntry();
                }
            }
            if (!manifestSeen) throw new IOException("Save archive manifest is missing");
            if (tops.isEmpty()) throw new IOException("Archive contains no saves or settings");
            for (String top : tops) {
                if (top.endsWith(".sav") && !top.endsWith(".sav.zip")) validateWorldFolder(stage.resolve(top), top);
            }
            Files.createDirectories(game);
            // Preserve existing controls/settings; importing a world must not reset them.
            if (Files.exists(game.resolve("settings.txt"))) {
                tops.remove("settings.txt");
                Files.deleteIfExists(stage.resolve("settings.txt"));
            }
            if (tops.isEmpty()) throw new IOException("No new worlds or mods in archive; existing settings were preserved");
            for (String top : tops) {
                if (Files.exists(game.resolve(top), LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Already exists: " + top + ". Import into a fresh game data directory or rename the world in the archive.");
                }
            }
            // Each top-level save is activated with a rename. Roll back if a later move fails.
            List<String> moved = new ArrayList<>();
            try {
                for (String top : tops) {
                    Files.move(stage.resolve(top), game.resolve(top));
                    moved.add(top);
                }
            } catch (IOException error) {
                for (String top : moved) {
                    try { Files.move(game.resolve(top), stage.resolve(top)); }
                    catch (IOException rollback) { error.addSuppressed(rollback); }
                }
                throw error;
            }
        } finally {
            SafeTar.deleteTree(stage);
        }
    }

    private static void validateExportRoot(Path game) throws IOException {
        if (!Files.isDirectory(game, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Game save directory is missing");
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(game)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (name.endsWith(".sav") && !name.endsWith(".sav.zip")) {
                    if (!Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) throw new IOException("World is not a directory: " + name);
                    validateWorldFolder(entry, name);
                } else if (name.endsWith(".sav.zip") && !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("World backup is not a file: " + name);
                }
            }
        }
    }

    private static void validateWorldFolder(Path world, String name) throws IOException {
        if (!Files.isDirectory(world, LinkOption.NOFOLLOW_LINKS)) throw new IOException("World is not a directory: " + name);
        // PkmnMap.saveToFileNew in v0.8.11 writes these JSON-in-zip members.
        boolean gameData = false;
        boolean mapData = false;
        boolean spawnData = false;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(world)) {
            for (Path entry : entries) {
                if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("World contains a non-file entry: " + name + "/" + entry.getFileName());
                }
                String file = entry.getFileName().toString();
                if (file.equals("game.json.zip")) {
                    validateJsonSave(entry);
                    gameData = true;
                } else if (file.startsWith("map") && file.endsWith(".json.zip")) {
                    validateJsonSave(entry);
                    mapData = true;
                } else if (file.startsWith("spawn") && file.endsWith(".json.zip")) {
                    validateJsonSave(entry);
                    spawnData = true;
                }
            }
        }
        if (!gameData || !mapData || !spawnData) {
            throw new IOException("World is empty or missing game/map/spawn JSON saves: " + name);
        }
    }

    private static void validateJsonSave(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry data = zip.getEntry("data.json");
            if (data == null || data.isDirectory() || data.getSize() == 0) {
                throw new IOException("Save archive is missing data.json: " + file.getFileName());
            }
            try (InputStream input = zip.getInputStream(data)) {
                int first;
                do { first = input.read(); }
                while (first != -1 && Character.isWhitespace((char) first));
                if (first == -1) throw new IOException("Save archive has empty data.json: " + file.getFileName());
            }
        } catch (ZipException error) {
            throw new IOException("Malformed save archive: " + file.getFileName(), error);
        }
    }

    private static byte[] manifestBytes() {
        return ("{\"schema\":1,\"game_version\":\"" + GAME_VERSION + "\"}\n").getBytes(StandardCharsets.UTF_8);
    }

    private static void validateManifest(byte[] bytes) throws IOException {
        ManifestParser parser = new ManifestParser(new String(bytes, StandardCharsets.UTF_8));
        parser.skipWhitespace();
        parser.expect('{');
        parser.skipWhitespace();
        boolean schemaSeen = false;
        boolean gameVersionSeen = false;
        int schema = 0;
        String gameVersion = null;
        for (int field = 0; field < 2; field++) {
            String key = parser.string();
            parser.skipWhitespace();
            parser.expect(':');
            parser.skipWhitespace();
            if ("schema".equals(key)) {
                if (schemaSeen) throw parser.malformed();
                schemaSeen = true;
                schema = parser.number();
            } else if ("game_version".equals(key)) {
                if (gameVersionSeen) throw parser.malformed();
                gameVersionSeen = true;
                gameVersion = parser.string();
            } else {
                throw parser.malformed();
            }
            parser.skipWhitespace();
            if (field == 0) parser.expect(',');
        }
        parser.skipWhitespace();
        parser.expect('}');
        parser.skipWhitespace();
        if (!schemaSeen || !gameVersionSeen || !parser.atEnd()) throw parser.malformed();
        if (schema != MANIFEST_SCHEMA || !GAME_VERSION.equals(gameVersion)) {
            throw new IOException("Unsupported save archive version");
        }
    }

    private static byte[] readBounded(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = input.read(buffer)) != -1) {
            if (output.size() + n > limit) throw new IOException("Save archive manifest is too large");
            output.write(buffer, 0, n);
        }
        return output.toByteArray();
    }

    private static final class ManifestParser {
        private final String text;
        private int position;

        ManifestParser(String text) { this.text = text; }

        void skipWhitespace() {
            while (position < text.length()) {
                char c = text.charAt(position);
                if (c != ' ' && c != '\t' && c != '\r' && c != '\n') break;
                position++;
            }
        }

        void expect(char expected) throws IOException {
            if (position >= text.length() || text.charAt(position++) != expected) throw malformed();
        }

        String string() throws IOException {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (position < text.length()) {
                char c = text.charAt(position++);
                if (c == '"') return value.toString();
                if (c == '\\' || c < 0x20) throw malformed();
                value.append(c);
            }
            throw malformed();
        }

        int number() throws IOException {
            int start = position;
            while (position < text.length()) {
                char c = text.charAt(position);
                if (c < '0' || c > '9') break;
                position++;
            }
            if (start == position || (position - start > 1 && text.charAt(start) == '0')) throw malformed();
            try { return Integer.parseInt(text.substring(start, position)); }
            catch (NumberFormatException error) { throw new IOException("Malformed save archive manifest", error); }
        }

        boolean atEnd() { return position == text.length(); }

        IOException malformed() { return new IOException("Malformed save archive manifest"); }
    }
}
