# PokeWilds for Android

A standalone Android app for PokeWilds 0.8.11. The APK includes the Linux
runtime, display, graphics bridge, and audio support, but does not include the
PokeWilds game files. On first launch, download the pinned official game
release or select a game ZIP or extracted game folder that you already have.
The app copies the game files into its private storage. No companion app or
separate runtime installation is needed.

The current build targets ARM64 Android and has been exercised on the Android 13
AYANEO Pocket DMG. Touch controls and mod transfer have also been tested on a
Samsung SM-S901U running Android 16. Broader device compatibility needs
separate testing. This is still a prototype, not a general Android release.

## Install and play

Download the signed Android APK from the
[v1.0.0 release](https://github.com/NoNoseYaKnow/Pokewilds-Android/releases/tag/v1.0.0),
or see [Build from source](#build-from-source) to build it locally.

On first launch, the app starts downloading the pinned PokeWilds 0.8.11 release
from its [official GitHub release page](https://github.com/SheerSt/pokewilds/releases/tag/v0.8.11).
You can cancel or retry the download, or choose **Choose game ZIP** or **Choose game folder** and select
a copy of the official 0.8.11 files that you already have. The ZIP must match
the pinned release archive; an extracted folder is checked for the expected
game files. Setup shows progress. Once it finishes, **Start / resume** opens the game; later
launches go straight to the game. The APK can also be imported into Android
game frontends as a normal app.
The app deletes its temporary ZIP copy after extraction and removes incomplete
downloads on the next launch. A ZIP you selected from device storage stays in
its original location.

The D-pad or left stick moves, A and B act as the game's action buttons, Start
opens PokeWilds's in-game menu, and L1/R1 zoom or cycle materials according to
the current game mode and enabled patches. For phones,
optional on-screen controls provide the same actions. They appear automatically
when no game controller is connected, and can be set to On or Off in App
settings. The overlay's small top button hides or restores it.

Press **R3** (right stick click) to show or hide the Android keyboard. The
shortcut is configurable in App settings. On-screen controls use **R2** when
the selected shortcut button is absent from the overlay; choosing Off disables
the shortcut for both touch and gamepad. Select and L2 are reserved for game
actions. Android Back opens a Game menu with
**Show keyboard** for use when the controls are hidden.

## Save, quit, and manage settings

Press Android Back and choose **Quit PokeWilds**. If the game asks whether to
save, use Left/Right to focus **Yes** or **No**, then press A. You can also tap
the choice. Android Home leaves the session running; it does not save the game.
The Android Back menu captures controller input while it is open, so navigating
it does not move the character in the background.

Long-press the app icon and choose **App settings**, or open it from the Game
menu. This screen includes graphics and viewport choices, on-screen controls,
the keyboard shortcut, and **PokeWilds game settings**. The game settings editor
covers non-keybinding options in `settings.txt`; changes apply when the next
game session starts. Stop the game before changing runtime or game settings.

Use **Export saves** after saving and quitting, then choose a destination in
Android's document picker. **Import saves ZIP** accepts an app export ZIP or a
ZIP containing one PokeWilds 0.8.11 `.sav` world folder. **Import .sav folder**
accepts the world folder directly, including its JSON ZIP files and PNG. Export
worlds before uninstalling: uninstalling removes the
app's private game files and saves. See [save migration](standalone/migration/README.md)
for transferring existing worlds.

## Mods

After quitting the game, open **App settings** and choose **Import mods ZIP**
or **Import mods folder**. The selected files are copied into the game's
`mods/` folder; a ZIP can contain a top-level `mods/` folder, a single enclosing
folder with `mods/` inside, or the mod files directly. Files at matching paths
replace the installed copies. Use **Export mods ZIP** first if you want a
backup. Mods are loaded when the game starts
again. This installs PokeWilds file mods such as sprites and music, not Java
plugins.
To remove installed files, choose **Browse / remove installed mods** in App
settings. Open folders, select files or folders, and confirm the removal. A
folder can contain files from more than one imported ZIP; export a mods backup
before deleting if you may want to restore them later.

## Patches

After quitting the game, open **App settings** to manage eight included
PokeWilds 0.8.11 patches: directional mod sprites, Ho-Oh, Pokémon on separate
building floors, eggs laid on upper floors, a field move wheel, shoulder
zoom, a Select map shortcut, and controller button prompts. All eight are on by default; each switch takes effect on the next game start.
Hold L2 to choose a field move, or use L1/R1 on a controller or the touch overlay
to zoom. The existing on-screen controls and gamepad bindings remain available;
L1/R1 still cycle materials during BUILD and DIG. The app runs selected patches
from bundled Java agents and keeps the verified official game JAR unchanged.
With the map shortcut enabled, Select opens the map and Select or B returns to
gameplay. Controller prompts use the on-screen button labels; Start confirms
nickname and sign text, while entering text still requires a keyboard. Both
patches can be disabled independently and do not change the save format.
When Pokémon on different floors share coordinates, the floor fix
stores their exact placements in an extra entry of the map save ZIP. Older
PokeWilds versions read only a legacy projection, where some Pokémon may be
moved or absent; saving there removes the exact placements. The launcher warns
about this and blocks starting without the floor fix while an affected world is
installed. Export a save backup before opening the world in an older version.

## Build from source

The build recipe and complete prerequisites are in the
[standalone APK guide](standalone/README.md). From `standalone/`, run:

```sh
export ANDROID_HOME=/path/to/android-sdk
./build.sh
```

This assembles a debug APK at
`standalone/android-app/build/outputs/apk/debug/android-app-debug.apk`. A
release build needs a private signing key; follow the guide's
[signed local build](standalone/README.md#signed-local-build) instructions.

The APK packages third-party runtime components but no PokeWilds game files.
Review the
[upstream provenance and notices](standalone/UPSTREAM.md) before distributing
a build. Implementation details and validation evidence are in
[the standalone guide](standalone/README.md) and
[validation notes](standalone/VALIDATION.md).
