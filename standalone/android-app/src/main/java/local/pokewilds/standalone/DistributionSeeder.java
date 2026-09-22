package local.pokewilds.standalone;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/** Installs bundled game files while retaining app-owned saves and settings. */
final class DistributionSeeder {
    private static final String MARKER = ".distribution-ready";

    private DistributionSeeder() {}

    static void ensure(Path source, Path target, String revision) throws IOException {
        Path marker = target.resolve(MARKER);
        if (Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
            && revision.equals(new String(Files.readAllBytes(marker), StandardCharsets.US_ASCII))) {
            return;
        }

        Path stage = target.resolveSibling("game.preparing");
        SafeTar.deleteTree(stage);
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(stage.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, stage.resolve(source.relativize(file)));
                return FileVisitResult.CONTINUE;
            }
        });

        try {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                mergeDistribution(stage, target);
                SafeTar.deleteTree(stage);
            } else {
                Files.move(stage, target);
            }
            Path markerStage = target.resolve(MARKER + ".preparing");
            Files.write(markerStage, revision.getBytes(StandardCharsets.US_ASCII),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            Files.move(markerStage, marker, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) {
            SafeTar.deleteTree(stage);
            throw error;
        }
    }

    private static void mergeDistribution(Path stage, Path target) throws IOException {
        Files.walkFileTree(stage, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(stage.relativize(path)));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                Path relative = stage.relativize(path);
                Path destination = target.resolve(relative);
                String top = relative.getName(0).toString();
                boolean userData = top.equals("settings.txt") || top.equals("mods")
                    || top.endsWith(".sav") || top.endsWith(".sav.zip");
                if (!userData || !Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                    Path temporary = destination.resolveSibling(destination.getFileName() + ".preparing");
                    Files.copy(path, temporary, StandardCopyOption.REPLACE_EXISTING);
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
