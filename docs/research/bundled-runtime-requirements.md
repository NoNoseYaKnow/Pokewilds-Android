# Bundling the exact PokeWilds 0.8.11 JAR in one APK

This note evaluates the revised architecture: preserve the exact `pokewilds-v0.8.11` JAR and ship its Java runtime and graphics/audio support inside one Android APK. It does not implement the bundle. The current repository launches the JAR through Termux, Ubuntu/proot, Termux:X11, virgl, PulseAudio, and a desktop LWJGL3/GLFW stack.

## Blockers to prove first

1. **Android 10+ executable-path rule.** For an app targeting API 29+, Android removes permission to execute files from the writable app home directory. Android says apps should load binary code embedded in the APK; `execve()` of a copied JRE, PRoot binary, shell, or Linux executable under ordinary app data is therefore not a valid assumed launch path. See [Android 10 behavior changes](https://developer.android.com/about/versions/10/behavior-changes-10). A proof-of-concept must launch the runtime without relying on `execve()` from a writable directory.

2. **The exact JAR's native contract.** A Linux desktop JAR may load LWJGL3/GLFW/OpenAL shared libraries, expect X11/GLX, and use Java desktop/AWT behavior. Those Linux/glibc libraries cannot simply be copied into APK assets or `jniLibs`; Android's native libraries must be Android ABI libraries packaged under `lib/<abi>`, and Gradle's normal path is `System.loadLibrary()`/JNI. See [Android ABI packaging](https://developer.android.com/ndk/guides/abis) and [Gradle native-library packaging](https://developer.android.com/studio/projects/add-native-code). The first spike must enumerate the JAR's classes, resource paths, native library names, and OpenGL/audio calls.

3. **X11 and virgl are not one-JAR dependencies.** Termux:X11 is an Android X-server app plus a companion Termux package, and its documented launch model is a separate `termux-x11` process with `DISPLAY`, shared `/tmp`, and a foreground Android Activity. A standalone APK must either merge/fork that Android X server and its companion package, or replace the X11/GLFW path with an Android surface bridge. See the [Termux:X11 README](https://github.com/termux/termux-x11/blob/master/README.md). The Termux `virglrenderer-android` package is separately built with the Android NDK and `angle-android`, and installs a `virgl_test_server_android` binary plus libraries into the Termux prefix; it cannot be assumed to run from an APK unchanged. See its [first-party package build](https://github.com/termux/termux-packages/blob/master/packages/virglrenderer-android/build.sh).

4. **Termux's compiled prefix is package-ID-specific.** Termux documents that package paths, prefixes, RPATHs, and build variables are compiled for `/data/data/com.termux/files` and package `com.termux`; packages do not work under another app package name or data/rootfs directory without recompiling them. A copied Termux prefix therefore is not a relocatable private runtime. See [Termux execution environment](https://github.com/termux/termux-packages/wiki/Termux-execution-environment) and [filesystem layout](https://github.com/termux/termux-packages/wiki/Termux-file-system-layout).

If any of these four proofs fails, “one APK containing the unchanged Linux runtime” is blocked. The practical fallback is an Android-compatible mobile JRE plus Android-adapted LWJGL/GLFW/OpenAL, as demonstrated by Pojav-style launchers; that still preserves the game JAR but does not preserve the current Linux/Termux runtime byte-for-byte.

## What can be embedded safely

APK assets are suitable for immutable JARs, JRE data, rootfs archives, configuration templates, and game assets. The app can copy/read them from private storage, but copied executable code has the Android 10 restriction above. Native code should be built for `arm64-v8a`, packaged through Gradle under `jniLibs/arm64-v8a` or an external native build, and loaded through Android's supported native-library path. Android documents that Package Manager finds libraries at `/lib/<abi>/lib*.so` and copies them to the app's native library directory; [native library loading/package layout](https://developer.android.com/ndk/guides/abis) is the acceptance check.

Any host-side launcher, PRoot, EGL/GL bridge, audio bridge, or JNI shim must therefore be Android/Bionic `arm64-v8a` code. A Debian/Ubuntu glibc executable or the current Termux Bionic executable is a guest/host distinction that must be preserved. Termux explicitly documents that its native packages use Android Bionic, while Debian-style rootfs programs use glibc and need PRoot/QEMU or another compatibility layer ([execution environment](https://github.com/termux/termux-packages/wiki/Termux-execution-environment)). PRoot itself is an unprivileged ptrace-based chroot-like layer ([PRoot README](https://github.com/termux/proot/blob/master/README.md)); it does not turn the Android app into a normal Linux process environment.

Android 10 also disallows writable-file modifications to executable code mapped by `dlopen()` and rejects text relocations for modern targets. All JNI/bridge libraries must be rebuilt, signed as part of the APK, and tested for linker namespace behavior. If targeting newer Android versions, check all prebuilt native libraries for 16 KB ELF alignment; Android's [16 KB page-size guidance](https://developer.android.com/guide/practices/page-sizes) requires rebuilding incompatible NDK/prebuilt libraries.

## Runtime options

### Option A: exact Linux/Termux-style runtime

This would package a custom Termux-like Android host, a package-ID-specific Bionic prefix, PRoot, a glibc rootfs, the Java 17 runtime, the exact JAR, X11, virgl, and PulseAudio. It is the closest byte-for-byte match to the current setup but has the largest proof burden:

- Rebuild every Termux package for the new application package name and private paths, or patch all hardcoded paths and RPATHs. The official Termux docs say packages cannot be mixed across package names.
- Prove a legal Android 10+ launch path for PRoot/JVM/bridge binaries that does not `execve()` from writable app data. APK `jniLibs` are native libraries, not a general Linux `/usr/bin` filesystem; a native bootstrap may need to load Android-built libraries in-process.
- Prove the guest glibc loader can resolve its JVM and Linux JNI dependencies, while Android-side components satisfy Bionic linker namespace rules. This must be tested with the exact Java 17 build, not inferred from the successful Termux shell.
- Replace the separate Termux:X11 application with an in-process Android surface/X server or accept that the result still requires another APK. The documented shared-UID variant only runs as part of Termux and requires matching signing keys; it is not a generic embedding mechanism.
- Rebuild virgl/ANGLE and expose the required EGL/GLES path to the guest. The package's build itself disables GLX/X11 and targets EGL, so preserve those assumptions rather than copying a desktop Mesa stack.
- Replace the current PulseAudio TCP setup with a native audio path or package a fully working Android PulseAudio bridge. Android's recommended low-latency native interfaces are AAudio/Oboe; [Android NDK audio guidance](https://developer.android.com/ndk/guides/audio) explains that AAudio is the native API and Oboe is the compatibility wrapper.

This option should be considered blocked until a tiny APK can launch the exact JAR on API 29+ with no Termux, no second APK, and no external shell. A rootfs-only “copy into assets then execute” demo is insufficient because it does not test Android's executable-path and linker rules.

### Option B: Pojav-style Android JVM compatibility layer

This is an alternative to investigate if Option A fails; compatibility with this game remains unproven. Pojav's first-party source demonstrates the required shape: it ships Android-built OpenJDK JREs, loads Android-native helper libraries with `System.loadLibrary`, sets a private runtime home and library path, provides a custom GLFW surface bridge, selects GLES-compatible renderers, and uses Android-specific process-launch workarounds. See [Pojav build instructions](https://github.com/PojavLauncherTeam/PojavLauncher), [JRE17 Android release](https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases), and [`JREUtils.java`](https://github.com/PojavLauncherTeam/PojavLauncher/blob/v3_openjdk/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java).

For PokeWilds, the feasibility question is whether its exact 0.8.11 JAR runs with that type of Android JRE and adapted LWJGL3 stack. The required adaptations may include:

- GLFW calls mapped to an Android `SurfaceView`/`Surface` instead of an X11 window.
- OpenGL desktop calls mapped to GLES through a tested translation layer or a custom LWJGL renderer.
- OpenAL calls mapped to Android audio. Android documents AAudio/Oboe for native low-latency audio; a PulseAudio server is not implied by those APIs.
- Keyboard/controller/window-size and close-event behavior mapped into Android lifecycle/input events.
- Native library lookup redirected to APK-packaged `arm64-v8a` libraries, with no extraction to an external or world-writable directory.

Pojav is evidence that this architecture is technically achievable for some Java/LWJGL games, not evidence that PokeWilds 0.8.11 is compatible. The exact JAR and LWJGL versions must be tested, and Pojav's licenses and any reused native code must be reviewed.

## Process, lifecycle, and user-visible operation

The game, JVM, and any graphics/audio workers should remain in the foreground Activity/process while playing. If a separate service is used to keep a runtime alive, Android 8+ background-service limits and Android 12+ foreground-service launch restrictions apply; Android 14+ also requires a valid foreground-service type and permission. Sources: [background execution limits](https://developer.android.com/about/versions/oreo/background), [Android 12 foreground-service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), and [Service API](https://developer.android.com/reference/android/app/Service).

The Activity must own the Android `Surface` and route pause/resume, focus loss, Back, controller input, and clean-exit requests to the Java runtime/game. The current Termux path uses `WM_DELETE_WINDOW` and a shell helper for clean save/exit; an embedded runtime needs an in-process equivalent or a tested signal/bridge. Do not rely on `onDestroy()` for saves: Android can kill a background process without calling it. Preserve periodic saves and bounded shutdown work as a requirement.

## Minimal feasibility spike before implementation

1. Freeze the known-good game/runtime versions, native dependencies, environment, and game checksum.
2. Build a minimal ARM64 APK under a new package ID. Prove the proposed Android-native bootstrap and PRoot/guest loader path, then run `java -version`.
3. Record the target SDK, executable locations, and linker/SELinux results. A low-target prototype and a modern-target build are distinct compatibility claims.
4. Integrate the X11 surface and private VirGL/audio workers, then launch the unchanged game. Verify the renderer, controller input, audio, and AWT/Swing save prompt.
5. If the Linux-runtime path fails, evaluate an Android-compatible JVM/GLFW bridge against the same exact-JAR requirements. Do not assume Pojav's Minecraft compatibility proves this game's compatibility.
6. Test the working candidate without external Termux/X11 installations, then cover cold install, update, process death, Home/Back, save recovery, and interrupted first-run unpack.

The acceptance result is a signed ARM64 APK that launches the exact JAR from the Android launcher with no separately installed Termux, Ubuntu, Termux:X11, VirGL, PulseAudio, or user shell. Private copies of these runtime components inside the APK are allowed. If the spike needs any of those, the bundle is not standalone yet; document the remaining dependency rather than calling the APK complete.
