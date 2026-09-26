package local.pokewilds.bugfix;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Checks that each source-level flag gates only its own fix across all 16 settings. */
public final class TestToggleMatrix {
    private static int failures;

    private static byte[] classBytes(ZipFile jar, String name) throws IOException {
        ZipEntry entry = jar.getEntry(name + ".class");
        if (entry == null) throw new IOException("missing class " + name);
        try (InputStream in = jar.getInputStream(entry)) { return in.readAllBytes(); }
    }

    private static boolean has(List<String> labels, String prefix) {
        for (String label : labels) if (label.startsWith(prefix)) return true;
        return false;
    }

    private static void check(boolean actual, boolean expected, String message) {
        if (actual != expected) {
            failures++;
            System.out.println("FAIL " + message + ": expected " + expected + ", got " + actual);
        }
    }

    private static List<String> transform(String name, byte[] bytes, boolean sprites,
            boolean hooh, boolean floors, boolean eggs) {
        List<String> labels = new ArrayList<String>();
        BugFixAgent.transformClass(name, bytes, sprites, hooh, floors, eggs, labels);
        return labels;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("usage: TestToggleMatrix official-pokewilds.jar");
        try (ZipFile jar = new ZipFile(args[0])) {
            byte[] upper = classBytes(jar, BugFixAgent.UPPER);
            byte[] pokemon = classBytes(jar, BugFixAgent.POKEMON);
            byte[] map = classBytes(jar, BugFixAgent.PKMN_MAP);
            byte[] eggData = classBytes(jar, BugFixAgent.POKEMON_DATA_V07);
            for (int mask = 0; mask < 16; mask++) {
                boolean sprites = (mask & 1) != 0;
                boolean hooh = (mask & 2) != 0;
                boolean floors = (mask & 4) != 0;
                boolean eggs = (mask & 8) != 0;
                String setting = "mask " + mask;
                check(has(transform(BugFixAgent.UPPER, upper, sprites, hooh, floors, eggs), "sprites:"), sprites, setting + " sprites");
                check(has(transform(BugFixAgent.POKEMON, pokemon, sprites, hooh, floors, eggs), "hooh:"), hooh, setting + " Ho-Oh");
                check(has(transform(BugFixAgent.PKMN_MAP, map, sprites, hooh, floors, eggs), "floors-"), floors, setting + " floors");
                check(has(transform(BugFixAgent.POKEMON_DATA_V07, eggData, sprites, hooh, floors, eggs), "eggs:"), eggs, setting + " eggs");
            }
        }
        if (failures == 0) System.out.println("PASS all 16 toggle combinations independently gate the four fixes");
        else System.out.println("FAILURES: " + failures);
        System.exit(failures == 0 ? 0 : 1);
    }
}
