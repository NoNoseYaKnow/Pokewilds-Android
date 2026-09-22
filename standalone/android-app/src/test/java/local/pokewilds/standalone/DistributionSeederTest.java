package local.pokewilds.standalone;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class DistributionSeederTest {
    @Test public void refreshesDistributionButPreservesUserData() throws Exception {
        Path root = Files.createTempDirectory("distribution-seeder-test");
        try {
            Path source = root.resolve("source");
            Path game = root.resolve("game");
            Files.createDirectories(source);
            Files.createDirectories(game.resolve("mods"));
            Files.write(source.resolve("pokewilds.jar"), bytes("jar-v1"));
            Files.write(source.resolve("README.txt"), bytes("readme-v1"));
            DistributionSeeder.ensure(source, game, "revision-one");

            Files.write(game.resolve("settings.txt"), bytes("user-settings"));
            Files.write(game.resolve("mods/user.mod"), bytes("user-mod"));
            Files.createDirectories(game.resolve("world.sav"));
            Files.write(game.resolve("world.sav/game.json.zip"), bytes("user-world"));
            Files.write(source.resolve("pokewilds.jar"), bytes("jar-v2"));
            Files.write(source.resolve("README.txt"), bytes("readme-v2"));

            DistributionSeeder.ensure(source, game, "revision-two");

            assertEquals("jar-v2", text(game.resolve("pokewilds.jar")));
            assertEquals("readme-v2", text(game.resolve("README.txt")));
            assertEquals("user-settings", text(game.resolve("settings.txt")));
            assertEquals("user-mod", text(game.resolve("mods/user.mod")));
            assertEquals("user-world", text(game.resolve("world.sav/game.json.zip")));
            assertEquals("revision-two", text(game.resolve(".distribution-ready")));

            Files.write(source.resolve("pokewilds.jar"), bytes("jar-v3"));
            DistributionSeeder.ensure(source, game, "revision-two");
            assertEquals("jar-v2", text(game.resolve("pokewilds.jar")));
        } finally {
            SafeTar.deleteTree(root);
        }
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static String text(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
