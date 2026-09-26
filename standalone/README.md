# Standalone APK prototype

This app packages the Linux runtime and Android host artifacts for PokeWilds
0.8.11. It does not package the game itself. On first launch, users can fetch
the pinned official release or select a copy of those official game files they
already have; the app installs those files into its private storage. It is a
working standalone prototype tested on the Pocket DMG running Android 13.
Touch controls and mod transfer were also tested on a Samsung SM-S901U running
Android 16.

The signed APK is available from the
[v1.0.0 release](https://github.com/NoNoseYaKnow/Pokewilds-Android/releases/tag/v1.0.0).
The build instructions below can produce a local APK.

The Android 13 ARM64 emulator runs the unchanged game with an embedded X11
surface, accelerated VirGL through ANGLE Vulkan, and bundled PulseAudio.
World generation, save/reload, the Swing save prompt, Android document-picker
export, and process cleanup have been exercised without Termux apps installed.
The Pocket DMG's native GPU path, hardware controls, quit dialog, and soft
keyboard have been exercised. ES-DE integration and migration of an existing
personal world still require device validation. See
[`VALIDATION.md`](VALIDATION.md) for the exact build and test evidence and
[`runtime/PAYLOAD_CONTRACT.md`](runtime/PAYLOAD_CONTRACT.md) for payload constraints.

## Prerequisites

The checked-in recipe currently expects a macOS host. In particular,
`native/build-proot.sh` selects the NDK's `darwin-x86_64` toolchain directory;
Linux and Windows hosts are not validated by this branch. An ARM64-capable
Docker engine is still required for the guest close helper, even on an Intel
Mac; Docker Desktop's emulation support is sufficient.

Install or provide:

- JDK 17 or newer, with `java` available on `PATH`.
- Android SDK Platform 36, a compatible Android SDK Build Tools release
  (36.0.0 is suitable), and NDK `29.0.14206865`.
- `ANDROID_HOME` pointing to that SDK. Set `ANDROID_NDK_ROOT` only when the
  exact NDK is outside `$ANDROID_HOME/ndk/29.0.14206865`.
- Docker Desktop or another Docker engine that can run `linux/arm64` images.
- `bash`, `git`, `curl`, `tar`, `make`, `patch`, `shasum`, `rg`, and `python3`.
- Network access for the first Gradle, Termux:X11, native dependency, Docker
  image, and pinned payload downloads. Later payload rebuilds can use the
  verified files in `runtime/cache`; the build entrypoint itself still runs
  the normal download/checksum steps.

Allow several gigabytes of working space for the Gradle cache, checked-out
X11 sources, native build products, package cache, and generated payload.
Do not place personal saves, a Termux prefix, signing keys, or an untracked
runtime directory in this tree.

## Build

Check out the main branch and run the entrypoint from `standalone/`:

```sh
git switch main
cd standalone
export ANDROID_HOME=/path/to/android-sdk
# A fresh build uses the pinned Docker close-helper automatically.
unset POKEWILDS_CLOSE_HELPER
./build.sh
```

The entrypoint prepares the pinned Termux:X11 source, assembles the locked
Android host package closure, builds Android PRoot, creates the deterministic
runtime archive (without game files), runs the debug unit tests, and assembles
the debug APK. The
APK is written to:

```text
android-app/build/outputs/apk/debug/android-app-debug.apk
```

When `POKEWILDS_CLOSE_HELPER` is unset, `build.sh` invokes the pinned Docker
recipe `runtime/build-close-helper-docker.sh` and writes the verified ARM64
Linux/glibc helper to `runtime/cache/pokewilds-close`. That script uses its
pinned Ubuntu ARM64 image and checks both the checked-in C source and compiler
output. To use an independently built, verified helper instead, set the
variable to its path:

```sh
POKEWILDS_CLOSE_HELPER=/path/to/pokewilds-close ./build.sh
```

Set `X11_SOURCE_DIR` to a prepared checkout to skip `prepare.sh`. The helper
and payload recipes record their own input checksums; inspect
`standalone-build-logs/` and `runtime/build/assets/payload.properties` when
auditing a build.

### Host graphics and audio closure

`packaging/host_packages.py` assembles the pinned VirGL, ANGLE, and PulseAudio
packages into the Android host payload. It deliberately removes the extracted
`lib/libbinder_ndk.so` compatibility shim: Android's framework binder library
must be selected for the host process, because the Termux shim can make system
EGL fail to load on Android 13.

The pinned `libvirgl_test_server_android.so` contains absolute ANGLE directory
literals. The packager checks and rewrites these exact literals, preserving ELF
offsets:

```text
/data/data/com.termux/files/usr/opt/angle-android/gl
/data/data/com.termux/files/usr/opt/angle-android/vulkan
/data/data/com.termux/files/usr/opt/angle-android/vulkan-null
```

They become `./opt/angle-android/{gl,vulkan,vulkan-null}` relative to the host
payload working directory. This is a pinned relocation step that fails closed
if the upstream binary changes; it is not a claim that every host library is
generically relocatable.

### Graphics profile experiments

Pass `-PruntimeGraphics=...` through `build.sh` to select the runtime profile
used by the Android supervisor:

```sh
./build.sh -PruntimeGraphics=native       # default target profile
./build.sh -PruntimeGraphics=angle-gl
./build.sh -PruntimeGraphics=angle-vulkan
./build.sh -PruntimeGraphics=software
```

`native` remains the default accelerated profile for this target. The
`software` profile selects guest llvmpipe and is useful for isolating VirGL
problems; `angle-gl` and `angle-vulkan` select the corresponding diagnostic
VirGL paths. The Android emulator uses Vulkan compatibility because its native EGL driver
lacks the surfaceless-context extension required by this VirGL build. This is
an emulator compatibility choice; the DMG uses the default native profile.

From the launcher's **App settings** shortcut, stop the game before changing
runtime or game settings. The graphics choices are Native GPU (recommended), Vulkan
compatibility, OpenGL compatibility, and Software (slow diagnostics). The
viewport choices are Auto — match screen (default for new installations),
480x432, 640x576, and 960x864. Auto uses the available game display area,
keeps a minimum 480x432 canvas, and adjusts to window-size changes.
Screens narrower than 10:9 retain that canvas with letterboxing so the
unchanged desktop menu remains fully visible. For
example, 16:9 uses 768x432, 4:3 uses 576x432, and the Pocket DMG uses about
496x432. With Shoulder zoom enabled, Auto uses a canvas at half the available
display size on large enough screens and scales it up 2x. Portrait keeps a
centered 10:9 game picture so the menu remains visible. In regular Auto mode,
width is aligned to eight pixels for the X11 mode, so a tiny aspect ratio
difference is possible. Existing fixed selections survive upgrades;
choose Auto explicitly to switch. Changing graphics profiles preserves Auto. The selections
are stored in the app's private runtime preferences and applied to the next
session; the controls are disabled while a session is active. The normal
launcher path keeps its one-tap auto-start behavior and uses the build's
`native` default until a management selection is saved.

On-screen controls can be set to Auto, On, or Off. Auto displays them when no
game controller is connected. The overlay provides D-pad, A/B, Select/Start,
and L1/L2/R1/R2 with the same actions as physical controller buttons. L2 sits
above L1 at the left, R2 above R1 at the right, and Select left of Start. All
buttons remain visible regardless of patch settings. It supports held and
simultaneous inputs and leaves the center
of the game available for direct touch. Its small toggle hides or restores the
controls during a session.
The X11 cursor is hidden over the game. Taps use screen coordinates so the
game's Swing save/quit dialog can still be selected by touch. In that dialog,
Left/Right moves between Yes and No, and A activates the focused choice.
On a gamepad, right stick click (R3) shows or hides the Android keyboard by
default. The shortcut can be changed or turned off in App settings. Touch uses
R2 if the selected button is not on screen; Off disables both. Select and L2
are no longer keyboard shortcut choices. Existing selections of those buttons
reset to R3 on physical controllers and R2 on touch. Other selections retain
their meaning. Android Back opens a
Game menu with a Show keyboard choice, so touch users can still reach it when
the on-screen controls are Off or hidden.

**PokeWilds game settings** edits the non-binding options in the private
`game/settings.txt` used by the unchanged game. Keyboard and gamepad bindings
are preserved but are not exposed in the app. Saving retains bindings, unknown
lines, and comments, writes through a staged file, and keeps the
previous contents in `settings.txt.backup`. Changes apply on the next game
start. Boolean options and text speed use lists of values recognized by
PokeWilds 0.8.11. Gamepad dead zone accepts 0 through 1, while zoom accepts a
positive number. The editor is disabled while the game is running.

## Signed local build

The `release` variant reads the ignored files `.signing/standalone.p12` and
`.signing/password`, using key alias `standalone`. Keep both files private and
back them up securely: subsequent APKs must use the same key to update this
installation without uninstalling and losing app-private saves. They are not
committed or included in the APK. A release without these files is unsigned.

With the local signing files present:

```sh
./build.sh :android-app:assembleRelease
python3 packaging/verify_apk.py android-app/build/outputs/apk/release/android-app-release.apk
```

The checked-in source and recipes can be published independently of the game
APK. See [upstream provenance and notices](UPSTREAM.md) before redistributing
bundled third-party artifacts.

## First launch, playing, and backups

The APK contains no PokeWilds game archive or assets. On first launch, the app
starts downloading the pinned PokeWilds 0.8.11 release from the
[official GitHub release page](https://github.com/SheerSt/pokewilds/releases/tag/v0.8.11).
The download is verified before installation. Alternatively, choose **Choose
game ZIP** or **Choose game folder** to select an archive or extracted copy you already
have. A selected ZIP must match the pinned release archive; an extracted folder
is checked for the expected game files. The app copies verified files into its
app-private storage and does not require Termux or another runtime app. Setup reports progress and the
download can be cancelled. The game starts after setup; subsequent ordinary
launches start it directly.
The app deletes its temporary downloaded or copied ZIP after extraction and
cleans up incomplete transfers on the next launch. A selected ZIP remains in
its original location.
Long-press the Android launcher icon and choose **App settings** to open settings,
import/export, recovery, and startup logs. The running notification also opens
this screen. Android Back in the game also offers **App settings**. ES-DE can import the app as a normal Android game; its actual
Pocket DMG flow remains a device check.

D-pad/stick directions are mapped to arrows, A to Z, B to X, Start to Enter,
and shoulders to C/V. A activates the focused Swing save-dialog button.
Android Back opens the Game menu with **Keep playing / Show keyboard / App settings /
Quit PokeWilds**. Quit asks the game to close normally; accept its save prompt before
leaving. Force-stop recovery remains available in App settings for a hung
session and discards unsaved progress. Home leaves the session running; do not
assume the game autosaves.

Use **Export saves** after saving and quitting, then choose a location in the
Android document picker. Uninstalling removes the privately stored game files
and worlds: export saves first. App-private storage is not shared with other
apps. **Import saves ZIP** accepts an app export ZIP or a ZIP containing one
PokeWilds 0.8.11 `.sav` world folder. **Import .sav folder** accepts that folder
directly, with its JSON ZIP files and PNG. Select the whole world folder or its
outer ZIP, not an individual JSON ZIP. Android's document picker grants access
to the selected source.
[Migration instructions](migration/README.md) explain copying current 0.8.11
JSON saves from Termux. Legacy Kryo saves are not supported by this importer.
Imports preserve existing settings, refuse world-name collisions, merge
identical/missing mod files, and refuse conflicting mod contents.

For direct mod installation, quit the game and open **App settings**. **Import
mods ZIP** accepts a ZIP with `mods/` at its root, one enclosing folder with
`mods/` inside, or mod files directly at its root; **Import mods folder** selects
the folder containing the mod files. When a pack has one enclosing folder, the
main `mods/` folder is imported; optional variants in sibling folders must be
selected separately.
These actions merge into the app-private `mods/` directory and replace files
at matching paths. **Export mods ZIP** saves a copy through Android's document
picker before making changes. Imports stage and validate files, then swap the
merged directory into place; an interrupted swap is recovered on next launch.
PokeWilds loads the changes on its next start.
To remove files, choose **Browse / remove installed mods** in App settings,
open the `mods/` subfolders, select individual files or whole folders, and
confirm. Imports are merged, so one folder can contain files from several
imports. The removal stages a copy of the remaining tree and activates it with
the same interrupted-swap recovery used by imports. Export a backup first if
you may want to restore deleted files.

The **Patches** section in App settings groups directional sprites, Ho-Oh, and
egg floor saving under **Fixes**. The field move wheel, shoulder zoom, Select map shortcut, controller button
prompts, and separate floor occupancy appear under **Enhancements**. Choices are saved
in app preferences and take effect when the game next starts. All eight are on
by default. The verified official `pokewilds.jar` stays untouched: the app
stages only the agents needed by the selected patches in its private session
directory. The wheel uses L2; zoom uses L1/R1 on physical controllers or the
touch overlay in the world and the map. Select opens the map directly and
Select or B returns to gameplay; the Start-menu map retains its menu return.
Controller prompts replace built-in keyboard instructions with A/B, L1/R1,
Start, and D-pad labels. Start also confirms nickname/sign edits even when
keyboard-Start is remapped; text entry still needs the keyboard. Player-written
text is not rewritten. Both new patches are independent of the wheel and zoom. The original on-screen controls remain in place,
and L1/R1 still cycle BUILD/DIG materials. No user-supplied code patches are
accepted. World zoom advances in whole framebuffer-pixel steps. With Auto
display mode, enabling zoom renders the game at half the available display size
and scales it up 2x on large enough screens. Map zoom reaches up to
4× magnification in whole-pixel steps. When different floors contain
Pokémon at the same coordinates, the floor fix writes exact placements to an
extra entry in the map save ZIP. The original `data.json` remains readable by
older PokeWilds versions as a legacy projection, but some Pokémon may appear
moved or absent there. Saving with an older version removes the exact entry.
The launcher warns about this and blocks starting with the floor fix off while
an enhanced world is installed. Export a save backup before opening such a
world in an older version.

## Prototype boundary

The first supported validation target is Android 13 ARM64, with target SDK 33.
The Linux ARM64/glibc guest executes through the packaged Android PRoot and
loader; those host executables live in Android's extracted native-library
directory. Guest Java and libraries remain app-private. No external command
permission, Termux package, root, or on-device package installation is needed.

`INTERNET` is used for the user initiated game download and remains available
to the unchanged desktop game's networking code. Runtime components are
packaged in the APK; the app does not download them. X11 disables TCP
listening, and VirGL and audio use app-private Unix sockets.
Multiplayer/network behavior is untested.
The app requests the foreground-service capability to supervise an active game.
Android versions beyond the tested API, including 16 KB page-size devices, need
separate execution and native-library checks.
