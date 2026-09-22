# Standalone APK prototype

This directory is developed on the `standalone-apk-plan` branch so the
existing Termux installer and shortcut APK on `main` remain independent. The
branch packages the unchanged PokeWilds 0.8.11 release with a private Linux
guest payload and Android host artifacts. It is a working standalone prototype, pending validation on the Pocket DMG.

The Android 13 ARM64 emulator runs the unchanged game with an embedded X11
surface, accelerated VirGL through ANGLE Vulkan, and bundled PulseAudio.
World generation, save/reload, the Swing save prompt, Android document-picker
export, and process cleanup have been exercised without Termux apps installed.
The Pocket DMG's native GPU path, hardware controls, ES-DE integration, and
existing personal world still require device validation. See
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

Check out the prototype branch and run the entrypoint from `standalone/`:

```sh
git switch standalone-apk-plan
cd standalone
export ANDROID_HOME=/path/to/android-sdk
# A fresh build uses the pinned Docker close-helper automatically.
unset POKEWILDS_CLOSE_HELPER
./build.sh
```

The entrypoint prepares the pinned Termux:X11 source, assembles the locked
Android host package closure, builds Android PRoot, creates the deterministic
runtime archive, runs the debug unit tests, and assembles the debug APK. The
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
an emulator compatibility choice; the DMG's default native profile needs a
physical-device check.

From the launcher's **Manage saves** shortcut, stop the game before changing
runtime settings. The graphics choices are Native GPU (recommended), Vulkan
compatibility, OpenGL compatibility, and Software (slow diagnostics). The
viewport choices are Auto — match screen (default for new installations),
480x432, 640x576, and 960x864. Auto uses the available game display area,
keeps a minimum 480x432 canvas, and adjusts to window-size changes.
Screens narrower than 10:9 retain that canvas with letterboxing so the
unchanged desktop menu remains fully visible. For
example, 16:9 uses 768x432, 4:3 uses 576x432, and the Pocket DMG uses about
496x432. Width is aligned to eight pixels for the X11 mode, so a tiny aspect
ratio difference is possible. Existing fixed selections survive upgrades;
choose Auto explicitly to switch. Changing graphics profiles preserves Auto. The selections
are stored in the app's private runtime preferences and applied to the next
session; the controls are disabled while a session is active. The normal
launcher path keeps its one-tap auto-start behavior and uses the build's
`native` default until a management selection is saved.

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

## Playing and backups

Install the APK and choose **Start / resume** on first launch. Preparation is
local and shows progress. Subsequent ordinary launches start the game directly.
Long-press the Android launcher icon and choose **Manage saves** to open settings,
import/export, recovery, and startup logs. The running notification also opens
this screen. ES-DE can import the app as a normal Android game; its actual
Pocket DMG flow remains a device check.

D-pad/stick directions are mapped to arrows, A to Z, B to X, Start to Enter,
and shoulders to C/V. Start activates the menu's Go option and the focused
Swing save-dialog button. Android Back opens **Quit / Keep playing /
Force-stop recovery**. Quit asks the game to close normally; accept its save
prompt before leaving. Force-stop is for a hung session and discards unsaved
progress. Home leaves the session running; do not assume the game autosaves.

Use **Export saves** after saving and quitting, then choose a location in the
Android document picker. Uninstalling removes private worlds: export first.
[Migration instructions](migration/README.md) explain copying current 0.8.11
JSON saves from Termux. Legacy Kryo saves are not supported by this importer.
Imports preserve existing settings, refuse world-name collisions, merge
identical/missing mod files, and refuse conflicting mod contents.

## Prototype boundary

The first supported validation target is Android 13 ARM64, with target SDK 33.
The Linux ARM64/glibc guest executes through the packaged Android PRoot and
loader; those host executables live in Android's extracted native-library
directory. Guest Java and libraries remain app-private. No external command
permission, Termux package, root, or on-device package installation is needed.

`INTERNET` remains available to the unchanged desktop game's networking code;
o runtime download is performed. X11 disables TCP listening, and VirGL and
audio use app-private Unix sockets. Multiplayer/network behavior is untested.
The app requests the foreground-service capability to supervise an active game.
Android versions beyond the tested API, including 16 KB page-size devices, need
separate execution and native-library checks.
