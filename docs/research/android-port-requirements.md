# Native Android APK port: requirements and plan inputs

This note records primary-source requirements for moving the current PokeWilds 0.8.11 desktop/LWJGL3 launch path into one native Android application. It is scoped to the tested target: Android 13 (API 33) on an ARM64 Pocket DMG with an Adreno 740. It does not implement the port.

## What the current setup implies

The checked-in launcher starts `pokewilds.jar` through Termux, Ubuntu/proot, Termux:X11, GLFW/LWJGL3, and a virgl bridge. The game and saves currently live under Termux's private data. The launcher APK in `launcher/` only sends fixed commands to Termux; it is not a game APK. A standalone build therefore has to replace all of these boundaries: the desktop `main()`/LWJGL3 entry point, X11 windowing, desktop OpenGL assumptions, and Termux filesystem paths.

Android apps run managed code as DEX on ART, while libGDX supplies a platform backend for the same game/core code. [ART executes DEX bytecode](https://source.android.com/docs/core/runtime), so the desktop jar is not itself a drop-in Android application. Desktop LWJGL3/GLFW natives must be excluded from the Android variant and replaced by libGDX's Android backend (and any Android-compatible native libraries it pulls in). Java 8/11 API desugaring covers a documented subset of language/library APIs; it does not provide desktop modules such as `java.desktop`, nor does it make LWJGL or Linux/X11 natives Android-compatible. See Android's [Java language/API desugaring guidance](https://developer.android.com/studio/write/java8-support).

Treat JNI dependencies as Android builds, not copied desktop files: any `.so` must be built for Android's ABI (here `arm64-v8a`), use Android/Bionic-compatible interfaces, and be packaged under the APK's standard `/lib/<abi>/lib*.so` layout so Package Manager can extract/load it. A Linux/glibc LWJGL `.so` from the current Termux path cannot satisfy that requirement. Gradle's external-native-build flow packages NDK outputs; see [native library ABI/package rules](https://developer.android.com/ndk/guides/abis) and [linking native libraries with Gradle](https://developer.android.com/studio/projects/gradle-external-native-builds). Plan a clean JNI inventory, including transitive libraries, before choosing any Android-native replacement.

For forward compatibility, check every bundled/prebuilt `.so` for Android 15 16 KB page-size support. Android requires rebuilding NDK libraries (including prebuilt dependencies) with compatible ELF alignment for 16 KB devices; NDK r28+ defaults to 16 KB alignment. This is not required by the Android 13 target today, but it should be a release gate if the app targets API 35+ or is expected to remain installable on newer 64-bit devices. See [16 KB page-size support](https://developer.android.com/guide/practices/page-sizes).

## Backend and project shape

Create a normal libGDX multi-module build with a platform-neutral `core` module and an `android` application module. Keep game rules, rendering through libGDX APIs, assets, and save serialization in `core`; keep the Activity, Android storage bridge, optional SAF import/export, and Android-only input/notifications in `android`. Keep the existing LWJGL3 desktop launcher as a separate development target until parity is proven.

libGDX's Android starter is an `Activity` subclass (`AndroidApplication`), initialized from `onCreate()` with an `ApplicationListener` and `AndroidApplicationConfiguration`; Android has no desktop-style `main()` entry point. The libGDX documentation also recommends one game Activity, because each new Activity can create a new OpenGL context and force graphical resources to reload. See [starter classes and configuration](https://libgdx.com/wiki/app/starter-classes-and-configuration) and the first-party [`AndroidApplication` source](https://github.com/libgdx/libgdx/blob/master/backends/gdx-backend-android/src/com/badlogic/gdx/backends/android/AndroidApplication.java).

The build should initially target `arm64-v8a` for the Pocket DMG. Android's ABI documentation identifies `arm64-v8a` as AArch64 and documents Gradle `abiFilters`; for Play distribution, if a 32-bit ABI is supported, the corresponding 64-bit ABI must also be present. A direct-sideload build can stay ARM64-only for this target. Inspect the final APK for `lib/arm64-v8a` entries, including libGDX's native library. Sources: [Android ABIs](https://developer.android.com/ndk/guides/abis) and [64-bit game support](https://developer.android.com/games/optimize/64-bit).

## Graphics and context migration

The current launcher forces a desktop OpenGL 3.3/Mesa path (`MESA_GL_VERSION_OVERRIDE=3.3`) through virgl. The native app will use Android's OpenGL ES implementation through libGDX; Android documents GLES 2.0 as API 8+, GLES 3.0 as API 18+, and GLES 3.2 as API 24+, but the app must still query the device/context before using a higher API. See [Android OpenGL ES](https://developer.android.com/develop/ui/views/graphics/opengl/about-opengl).

Before porting rendering, inventory every direct LWJGL/GLFW/OpenGL call and every shader. Replace desktop-only calls with libGDX abstractions or an Android-specific implementation. Test the lowest intended GLES path first; only require GLES 3 features if the game needs them and the manifest/configuration declares that requirement. The current Adreno 740 result is evidence for this target device, not a substitute for runtime capability checks.

Treat the GL surface as lossy across pause/resume. Android's `GLSurfaceView` exposes `surfaceCreated`/`surfaceDestroyed` and only *may* preserve the EGL context when `setPreserveEGLContextOnPause(true)` is used. libGDX's Android backend already wires pause/resume and GL-surface handling; game code must still be safe if GPU resources need recreation. Do not keep textures, framebuffers, or `AssetManager` instances in unmanaged static state. Sources: [GLSurfaceView API](https://developer.android.com/reference/android/opengl/GLSurfaceView), libGDX [life cycle](https://libgdx.com/wiki/app/the-life-cycle), libGDX [asset-management warning](https://libgdx.com/wiki/managing-your-assets), and [`AndroidApplication.onPause/onResume`](https://github.com/libgdx/libgdx/blob/master/backends/gdx-backend-android/src/com/badlogic/gdx/backends/android/AndroidApplication.java).

The manifest should declare only the graphics feature actually required (for example, GLES 2.0 if the port remains GLES2-compatible). A hard required feature excludes devices that do not advertise it, so verify the choice on the target hardware and on an emulator before release.

Audio also changes backend. libGDX's Android backend uses Android `SoundPool` for short `Sound` effects and `MediaPlayer` for streamed `Music`, and automatically pauses/resumes playback with the application lifecycle. Test effect concurrency, music looping/gaps, audio focus, mute/volume, and suspend/resume on the Pocket DMG; if latency or concurrent-effect limits matter, evaluate libGDX's documented asynchronous Android audio or an alternative backend such as Oboe/MiniAudio. See libGDX [audio](https://libgdx.com/wiki/audio/audio) and [streaming music](https://libgdx.com/wiki/audio/streaming-music).

## Lifecycle and save semantics

Implement and test libGDX's `create`, `resize`, `render`, `pause`, `resume`, and `dispose` callbacks. libGDX documents `pause()` on Android for events such as Home/incoming calls and explicitly calls it a good place to save game state; Android separately warns that a background process may be killed without `onDestroy()`. Write periodic safe snapshots during play and attempt a bounded flush when `pause`/`onStop` arrives, but never depend on a long shutdown operation completing; never rely on `dispose()` as the only save hook. Sources: libGDX [life cycle](https://libgdx.com/wiki/app/the-life-cycle), Android [Activity lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle), and Android [state changes/process death](https://developer.android.com/guide/components/activities/state-changes).

Use one Activity and in-game screens rather than launching activities for menus. Handle Android Back deliberately: libGDX documents catching `Input.Keys.BACK` when the game needs a confirmation or save-and-exit flow ([back/menu key handling](https://libgdx.com/wiki/input/back-and-menu-key-catching)).

## Assets, saves, and migration from Termux

Package immutable game assets under the Android module's `assets/`; libGDX packages that directory into the APK and exposes it through `Gdx.files.internal`, which is read-only. Put normal writable saves/settings in `Gdx.files.local` (private app-internal storage); use `Gdx.app.getPreferences()` for small key/value settings. libGDX documents that Android Preferences survive app updates but are deleted on uninstall. Sources: libGDX [file handling](https://libgdx.com/wiki/file-handling) and [Preferences](https://libgdx.com/wiki/preferences).

Android's scoped-storage model applies to apps targeting API 29+. App-specific internal storage is the simplest default and needs no storage permission. App-specific external storage is also permission-free on modern Android but is removed on uninstall. Sources: Android [storage overview](https://developer.android.com/training/data-storage) and [storage use cases](https://developer.android.com/training/data-storage/use-cases).

The old `/root/pokewilds` directory is inside Termux's private app data and must be imported while Termux still exists or from a user-visible backup. Do not assume the new APK can read Termux's private directory. Provide an explicit one-time import/export flow: let the user select a backup directory/file with the Storage Access Framework, copy/validate the save into app-private storage, and thereafter run from the private copy. `ACTION_OPEN_DOCUMENT_TREE` grants access only to the user-selected tree and does not require broad storage permissions; Android 11+ also blocks selecting several roots and `Android/data`, so the UI should ask for a normal backup directory (for example, `Download/PokeWilds-backup`) rather than an app-private path. Sources: Android [documents/files and SAF](https://developer.android.com/training/data-storage/shared/documents-files) and [scoped-storage migration guidance](https://developer.android.com/training/data-storage/use-cases).

If saves must survive uninstall or be shared with a desktop, expose an explicit export using `ACTION_CREATE_DOCUMENT`/SAF. Do not request `MANAGE_EXTERNAL_STORAGE` for ordinary saves; Android describes it as a special-access path for core file-manager-like use cases and Play policy evaluates it. See [manage all files](https://developer.android.com/training/data-storage/manage-all-files).

## Input on the Pocket DMG

Retain the existing keyboard/D-pad action mapping in the core input model, but test it through Android events. Android delivers controller buttons as `KeyEvent` and axes/D-pad as `MotionEvent`; use stable Android key codes/axes rather than device names or vendor IDs. Android's controller guide lists `KEYCODE_BUTTON_A`, `KEYCODE_BUTTON_B`, `KEYCODE_BUTTON_START`, `AXIS_HAT_X/Y`, sticks, and triggers, and recommends supporting wired and wireless controller classes. Sources: [controller input](https://developer.android.com/games/sdk/game-controller/controller-input) and [controller testing](https://developer.android.com/games/sdk/game-controller/testing_controller).

libGDX abstracts platform input, but its generic guidance treats touch/mouse/keyboard as separate capabilities. Test the Pocket DMG's built-in D-pad and A button in menus, text/name entry, save prompts, and Back handling, then test at least one external Bluetooth/USB pad. If analog axes are required, decide whether the core can use libGDX input alone or needs the `gdx-controllers` extension/Android-specific adapter. See libGDX [input handling](https://libgdx.com/wiki/input/input-handling) and [configuration/querying](https://libgdx.com/wiki/input/configuration-and-querying).

## Packaging, signing, and direct install

Use Gradle/Android Gradle Plugin for repeatable debug and release builds, with SDK/Build Tools pinned in the project. A debug APK is suitable for device iteration; `assembleDebug` produces an already aligned/signed APK, and `adb install path/to/app.apk` installs it. Sources: Android [command-line builds](https://developer.android.com/build/building-cmdline) and [ADB](https://developer.android.com/tools/adb).

For release APK distribution, generate and protect a signing key, zip-align before signing, run `apksigner verify`, and retain the same key for updates. Android requires every installable APK to be signed; an update with a different key will not replace the installed package. Sources: [app signing](https://developer.android.com/studio/publish/app-signing) and [apksigner](https://developer.android.com/tools/apksigner).

For Play distribution, publish an AAB and let Play generate device-specific APKs; for direct sideloading or an emulator/device test artifact, publish a signed universal/ARM64 APK. On Android 8+, a user installing from a website/file manager must allow installs from that particular source. See [Android App Bundles](https://developer.android.com/guide/app-bundle) and [publishing outside Play](https://developer.android.com/studio/publish).

## Suggested implementation sequence

1. Freeze a known-good desktop build and identify the actual game source/dependency revision behind `pokewilds-v0.8.11`; separate core code from the current LWJGL3 launcher.
2. Generate a minimal libGDX Android module and launch a no-game `ApplicationListener` on the Pocket DMG. Confirm `arm64-v8a`, APK install, Activity startup, rotation/fullscreen policy, and GLES context creation.
3. Move game assets and the smallest boot path into `core`/`android`; remove desktop LWJGL3/GLFW classes from the Android dependency graph. Resolve Java/API incompatibilities under ART and compile with the project’s selected Android-compatible Java/Gradle toolchain.
4. Port rendering and shaders from desktop OpenGL assumptions to GLES/libGDX, then validate textures, fonts, audio, window sizing, and frame pacing at the target 480x432-like logical viewport and native display sizes.
5. Port input and Back/save prompts. Exercise D-pad/A, touch fallback, keyboard, external controller, focus loss, Home/recents, notification interruption, and surface destruction/recreation.
6. Implement private-save paths plus a one-time SAF import/export flow for old Termux saves. Test update retention, uninstall behavior, malformed/partial saves, and interrupted writes (write-temp, flush, atomic replace where possible).
7. Build signed debug and release APKs, inspect manifest/ABIs/assets, install with `adb`, run a clean-device smoke test, then verify the release signature and update over the prior build. Keep the keystore outside source control.

## Architecture decision: native port versus bundling Linux

Bundling the current Linux JVM/proot/X11 route inside an APK would preserve more desktop code initially, but it would also preserve the dependencies the standalone goal removes: a Linux userland, JVM, proot, X11 server/window lifecycle, virgl/GLX translation, shell launch permissions, and private-path coordination. Android's Activity/ART lifecycle, scoped storage, and APK ABI/signing rules still apply around that bundle; it would be a self-contained compatibility launcher, not a native Android game. It also would not make the desktop LWJGL3 OpenGL path an Android backend.

The native libGDX route has a larger one-time source audit (LWJGL/GLFW, desktop GL/shaders, paths, lifecycle, input), but it removes Termux/X11/proot and can use Android's Activity, GLES, private storage, controller APIs, and normal APK update/signing model. For the stated “standalone APK” requirement, make native Android the primary plan and retain the Termux launcher only as a migration/reference path.

## Acceptance gates for the target device

- A signed ARM64 APK installs and launches directly from the Android launcher with no Termux, Ubuntu, Termux:X11, X11 display, or shell command.
- All packaged assets load from APK assets; new saves/settings work after a cold start and survive process recreation and app update.
- A user can import an existing save from a user-selected backup and export it again without broad storage access.
- Pocket DMG D-pad/A and Back work through gameplay, menus, save prompts, and exit; touch and one external controller are covered by the test matrix.
- Home/recents, incoming interruption, screen-off, rotation/configuration policy, and forced process death do not corrupt the last durable save; resumed/recreated GL resources render correctly.
- APK inspection shows the intended `arm64-v8a` native libraries and no desktop-only LWJGL/X11 runtime requirement; release signature verification passes and the same key updates the installed build.
