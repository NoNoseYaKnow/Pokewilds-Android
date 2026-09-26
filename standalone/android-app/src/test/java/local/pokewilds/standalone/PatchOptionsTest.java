package local.pokewilds.standalone;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class PatchOptionsTest {
    @Test public void disabledOptionsStillPassExplicitFalseValues() {
        PatchOptions options = new PatchOptions(false, false, false, false);

        assertFalse(options.anyEnabled());
        assertEquals(Arrays.asList(
            "-Dbugfix.sprites=false", "-Dbugfix.hooh=false",
            "-Dbugfix.floors=false", "-Dbugfix.eggs=false"), options.jvmArguments());
        assertEquals(Collections.emptyList(), options.enabledNames());
    }

    @Test public void enabledPatchesHaveIndependentFlagsAndNames() {
        PatchOptions options = new PatchOptions(true, false, true, false);

        assertTrue(options.anyEnabled());
        assertEquals(Arrays.asList(
            "-Dbugfix.sprites=true", "-Dbugfix.hooh=false",
            "-Dbugfix.floors=true", "-Dbugfix.eggs=false"), options.jvmArguments());
        assertEquals(Arrays.asList("Directional sprites", "Separate floor occupancy"), options.enabledNames());
    }

    @Test public void controlsDoNotEnableTheBugFixAgent() {
        PatchOptions options = new PatchOptions(false, false, false, false, true, false);
        assertFalse(options.anyEnabled());
        assertTrue(options.controlsEnabled());
        assertTrue(options.radial);
        assertFalse(options.zoom);
        assertEquals(Arrays.asList("Field move wheel"), options.enabledNames());
    }
}
