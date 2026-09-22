# Runtime component inventory for a one-APK, exact-JAR build

## Decision

The most faithful way to ship the audited `0.8.11` game is to keep
`pokewilds.jar` and run it in a private Termux-like Linux userland. The JAR is
a desktop LWJGL application, not an Android libGDX application: its manifest
entry point is `com.pkmngen.game.desktop.DesktopLauncher`, it contains LWJGL3
and desktop controller backends, and it contains no
`com/badlogic/gdx/backends/android` classes. A conventional Android wrapper
cannot load its Linux `.so` files as APK JNI libraries.

This preserves the existing GLX/VirGL path, OpenAL/PulseAudio path, and the
Swing save-confirmation dialog. It also avoids a source rewrite: the official
PokeWilds `v0.8.11` tag contains release assets but no Java/build source (see
[`standalone-source-audit.md`](standalone-source-audit.md)).

## Minimal private process topology

```text
Android Activity / runtime supervisor
  ├─ embedded Termux:X11/Lorie-derived NDK X server
  ├─ virgl_test_server_android + ANGLE/EGL native libraries
  └─ private process supervisor and Linux userland
       ├─ Ubuntu/glibc rootfs, proot, OpenJDK 17, Mesa GLX/virpipe
       ├─ X11 client libraries, OpenAL/PulseAudio, xdotool/Xlib helper
       └─ java -jar pokewilds.jar

private shared tmp/socket directory:
  DISPLAY=:0  XDG_RUNTIME_DIR=/tmp  GALLIUM_DRIVER=virpipe
```

The current working topology is the reference configuration: Termux:X11 is
started as `termux-x11 :0 -ac`, the VirGL server is
`$PREFIX/bin/virgl_test_server_android`, and Ubuntu is entered with
`proot-distro login ubuntu --shared-tmp`. The guest sets `DISPLAY=:0`,
`XDG_RUNTIME_DIR=/tmp`, `GALLIUM_DRIVER=virpipe`,
`__GLX_VENDOR_LIBRARY_NAME=mesa`, and `MESA_GL_VERSION_OVERRIDE=3.3`.

The [official Termux:X11 README](https://github.com/termux/termux-x11) says
that the project consists of an Android app **and** a companion Termux package,
both required. It also documents `--shared-tmp`, the separate `termux-x11`
process, and the `com.termux.x11` Activity. Therefore “one app” means merging
or rebuilding those pieces behind one Activity; copying the command and JAR
into an APK is insufficient.

## Components that can remain unchanged

| Component | Why it can stay | Boundary |
| --- | --- | --- |
| `pokewilds.jar` and its bundled assets | The exact release JAR already has the desktop launcher, game code, OGG audio, and Linux ARM64 LWJGL natives. | It must execute inside a compatible Linux/glibc runtime; the native files are not Android JNI libraries. |
| `settings.txt`, `mods/`, and the JAR’s asset trees | `Game.checkForMods` checks the local data directory before falling back to JAR resources. | Give the private runtime a stable local-data directory. |
| Desktop GLX/LWJGL and OpenAL path | This is the behavior currently producing video/audio through X11 and VirGL. | Keep Mesa GLX, VirGL, X11, and OpenAL in the Linux runtime. |
| `pokewilds-close` | The small Xlib helper can send `WM_DELETE_WINDOW`; the existing desktop close handler then prompts to save. | Rebuild only if the private Linux userland does not provide the same X11 ABI. |

The JAR’s desktop close handler uses `JOptionPane.showConfirmDialog` and saves
through `Game.staticGame.saveGame()` when requested. Its save implementation
writes the existing JSON-in-zip format, so preserving the JAR preserves save
compatibility. Existing saves from the active game directory must be copied into the
private local-data directory rather than left behind in the old rootfs.

## Components that must be rebuilt or integrated

| Component | Work required |
| --- | --- |
| Termux:X11 Android app/server | Integrate the NDK X server and Android Activity (the upstream tree includes `lorie`, `lorie-app`, and `shell-loader`), then replace the external `com.termux.x11` Activity/package contract. |
| VirGL/ANGLE | Bundle or rebuild `virgl_test_server_android` and its ANGLE/EGL dependency closure. The [Termux package recipe](https://github.com/termux/termux-packages/blob/master/packages/virglrenderer-android/build.sh) is a packaging recipe, not an APK-ready library bundle. |
| Linux userland | Bundle a glibc Ubuntu rootfs, `proot`/supervisor, OpenJDK 17, Mesa GLX/virpipe, X11 client libraries, OpenAL/PulseAudio, and the Xlib/xdotool close path. |
| Android lifecycle | Own the X server, VirGL server, and JVM process from an Activity-owned supervisor (service use requires separate lifecycle validation); handle private sockets, screen surface, shutdown, and Android scoped storage. |
| Launcher paths | Remove hard-coded `/data/data/com.termux/...`, `com.termux.x11`, and external `am start` assumptions. Map `HOME`, `PREFIX`, `TMPDIR`, X11 sockets, game files, and saves to the new package-private directories. |

Termux:X11 is [GPLv3 licensed](https://github.com/termux/termux-x11/blob/master/LICENSE).
The embedded distribution needs corresponding source/license compliance. The
PokeWilds JAR carries dependency notices, but those notices alone do not
establish a redistribution license for the game itself.

## Exact coupling and limits

The current scripts assume the Termux prefix
`/data/data/com.termux/files/usr`, Termux home
`/data/data/com.termux/files/home`, `com.termux.x11/.MainActivity`, and a
shared `/tmp` view supplied by `--shared-tmp`. A private APK must replace all
of these assumptions. Termux:X11’s shared-UID APK variant does not solve this:
it is still a separate APK and requires matching signatures.

The following remain unverified and should be treated as implementation gates:

* whether the complete glibc/proot/JVM/Mesa/OpenAL dependency closure can be
  redistributed inside one APK at an acceptable size;
* whether Android SELinux and the linker permit the chosen private-rootfs
  process model without Termux’s existing package/runtime machinery;
* whether the integrated X11 surface keeps AWT/Swing dialogs reliable across
  Android lifecycle changes; and
* the exact save migration path and storage quota for the private directory.

The Android-backend alternative would require a source/backend port and would
replace the desktop GLX/OpenAL/Swing behavior. It cannot be treated as an
exact-JAR implementation given the audited source/JAR mismatch.

## Primary evidence

* Local artifact inventory: `pokewilds-v0.8.11-otherplatforms/pokewilds.jar`
  (manifest, class/resource counts, Linux ARM64 natives, and `javap` findings
  recorded in `standalone-source-audit.md`).
* [Termux:X11 official repository and README](https://github.com/termux/termux-x11)
  (app/package split, `--shared-tmp`, process and Activity contracts).
* [Termux `virglrenderer-android` recipe](https://github.com/termux/termux-packages/blob/master/packages/virglrenderer-android/build.sh)
  (Android VirGL server packaging and ANGLE dependency).

