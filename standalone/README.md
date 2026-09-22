# Standalone APK prototype

This directory is developed on the `standalone-apk-plan` branch so the
existing Termux installer and shortcut APK on `main` remain independent. The
branch packages the unchanged PokeWilds 0.8.11 release with a private Linux
guest payload and Android host artifacts. It is a build prototype, not a
completed standalone runtime.

The current build can assemble a debug APK and run the JVM unit tests. A
separate modernprobe APK has reached the unchanged game JAR with llvmpipe on
an Android 13 emulator, without Termux apps, but that probe is not a release
or device-complete result for this project. The Android execution path,
writable-code restrictions, PRoot/JRE placement, embedded X11/Lorie surface,
VirGL and audio services, controller input, and save/exit lifecycle remain
runtime validation gates. See
[`docs/standalone-android-plan.md`](../docs/standalone-android-plan.md) and
[`runtime/PAYLOAD_CONTRACT.md`](runtime/PAYLOAD_CONTRACT.md) for the design
constraints.

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
VirGL paths. The ANGLE profiles remain experiments while their relocation is
being probed, and no profile in this prototype is a claim of release-ready
standalone rendering.

The separate modernprobe APK reached the unchanged JAR with llvmpipe on an
Android 13 emulator. The original private APK has also shown generated-world
creation and movement, but its AWT quit path currently fails on missing
`libbrotlidec`, and guest PulseAudio still has a memfd negotiation failure.
Those fixes are in progress; these observations do not establish complete
save/quit or audio behavior for this standalone APK.

## Prototype boundary

The archive is a Linux ARM64/glibc guest payload. Android host libraries are
built separately and placed under `host/`; they are not evidence that Android
can execute the extracted Linux programs from writable app storage. The
Android Gradle target SDK is 33, selected after the modernprobe Android 13
check, and the output is a debug artifact. Treat an APK build as a packaging
and static/unit-test result until the remaining runtime gates above are
demonstrated for this APK on the target device.
