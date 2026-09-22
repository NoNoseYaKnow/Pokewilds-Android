# Standalone Android source audit

Date: 2026-09-21

Scope: background audit of a possible source port. The selected implementation instead bundles the unchanged JAR and its private runtime; missing source does not block that approach.

## Result

An exact, source-built standalone Android PokeWilds 0.8.11 APK is blocked by the
official source snapshot. The `v0.8.11` tag at
[`SheerSt/pokewilds`](https://github.com/SheerSt/pokewilds/tree/v0.8.11) exposes
the game assets and README, but no Java source tree, Gradle files, Android
module, manifest, or top-level license file. The upstream issue
[`#51`](https://github.com/SheerSt/pokewilds/issues/51) records the same source
availability problem (“I only see assets and a little bit of asm”). A direct
clone could not be completed in this environment because DNS was unavailable;
the tag page and issue are the primary-source checks used here. The tag
resolves to commit `2e1ad7126e57bd293b5610def7d9dd04e0c555f1`; the parent audit
also captured the recursive tree and root listing as
`work/upstream-v0811-root.json` and `work/upstream-v0811-tree.json`.

This means a parity-preserving Android port needs source from the author or a
separately licensed source repository. Decompiling the shipped JAR would be a
new reconstruction project and cannot be treated as a normal upstream Android
build.

## What the official v0.8.11 artifact contains

The official release asset is
[`pokewilds-otherplatforms.zip`](https://github.com/SheerSt/pokewilds/releases/tag/v0.8.11).
The inspected local copy is `/tmp/poke-release/pokewilds-v0.8.11-otherplatforms`.
Its JAR manifest names `com.pkmngen.game.desktop.DesktopLauncher` as the main
class. The JAR contains 7,312 class files and the complete bundled asset tree,
including roughly 1,055 OGG files, but it is an object artifact rather than a
buildable source distribution.

The runtime is desktop-specific:

- `DesktopLauncher` constructs `Lwjgl3Application` and
  `Lwjgl3ApplicationConfiguration`, sets a 160×144 base window, and installs a
  desktop window listener. Its save-directory warning and stale-save close
  prompt use AWT/Swing (`JOptionPane`, `JTextArea`).
- The JAR contains `com.badlogic.gdx.backends.lwjgl3` and
  `com.badlogic.gdx.controllers.desktop` classes, plus LWJGL GLFW/OpenGL/OpenAL
  native libraries. It contains **zero** `com.badlogic.gdx.backends.android`
  classes.
- The shipped `linux/arm64/org/lwjgl/*` libraries and `libgdxarm64.so` files
  are Linux AArch64 ELF objects. They are useful to the current Ubuntu/PRoot
  route but are not Android JNI libraries or an Android application backend.
- `Game.create()` has an `ApplicationType.Android` branch and the JAR includes
  `DrawMobileControls`, but that dormant code does not supply the missing
  Android backend, launcher, lifecycle integration, or build configuration.

## Gameplay, input, audio, and save seams

The compiled game core is heavily reusable conceptually, but the Android seam
would need an explicit port:

- `InputProcessor` reads libGDX keyboard, touch, and (on desktop) the current
  controller from `com.badlogic.gdx.controllers.Controllers`. Settings include
  `keyboard-*`, `gamepad-*`, and dead-zone entries. The current Pocket DMG
  success comes through the desktop/X11 input path; it does not prove Android
  controller mapping in a native port.
- `AudioLoader.loadMusic/loadSound` delegates to `Gdx.audio.newMusic/newSound`
  and resolves mods from `mods/<path>` before internal assets. The release
  assets are OGG, so an Android backend can potentially handle them, but the
  exact Android audio behavior and latency are unverified here.
- `Save.saveData` writes `<path>.json.zip` with a `data.json` entry using Gson;
  reads fall back to the legacy compressed Kryo format. `saveMinimap` writes a
  PNG. `Game.saveGame()` delegates to `PkmnMap.saveToFileNew`. A native APK
  must deliberately migrate/import the existing PRoot save directory and
  preserve both JSON and legacy Kryo compatibility; changing the app-local
  path alone would make existing saves appear missing.
- `Dirs` uses appdirs with data/config version `1.0.0`, while the current
  PRoot launch keeps the game directory at `/root/pokewilds`. The Android port
  should define a stable app-private data path and a one-time import/export
  path rather than silently changing save location.

## Licensing and distribution gate

The inspected JAR has `META-INF/LICENSE` and `META-INF/NOTICE`, but those are
dependency notices (the NOTICE begins with Objenesis and the license is Apache
2.0). They do not establish a PokeWilds source or asset license. The official
repository tag page has no top-level `LICENSE` entry. Before distributing a bundled APK, document the applicable game distribution
terms and preserve third-party notices; this audit does not determine the game’s
own copyright or fan-work licensing status.

## Practical plan boundary

The selected [implementation plan](../standalone-android-plan.md) preserves this
JAR and its desktop behavior inside a private runtime. It requires execution,
display, GPU, audio, lifecycle, and save-migration proofs, without obtaining
source or introducing an Android libGDX backend. The source-port findings above
explain the alternative's constraints; they are not prerequisites for bundling.
