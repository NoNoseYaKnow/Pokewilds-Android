# PokeWilds for Android

A standalone Android APK for the unchanged PokeWilds 0.8.11 game. The APK
includes its own game runtime, display, graphics bridge, and audio support.
It does not need a companion app or a separate runtime installation.

The current build targets ARM64 Android 13 and has been exercised on the
AYANEO Pocket DMG. Support for other devices and Android versions needs
separate testing. This is still a prototype, not a general Android release.

## Install and play

**[Download the standalone APK](https://github.com/NoNoseYaKnow/Pokewilds-Android/releases/download/android-v0.3.0-preview.1/PokeWilds-Android-0.3-preview.1-arm64.apk)**
for ARM64 Android 13. Install the downloaded APK on your device. This is a
preview build; see the [release notes](https://github.com/NoNoseYaKnow/Pokewilds-Android/releases/tag/android-v0.3.0-preview.1)
for its checksum and supported-device details.

Open **PokeWilds** and choose **Start / resume** on the first launch. The app
prepares its bundled files locally and shows progress. Later launches open the
game directly. The APK can also be imported into Android game frontends as a
normal app.

The D-pad or left stick moves, A and B act as the game's action buttons, Start
opens PokeWilds's in-game menu, and L1/R1 provide C/V. For phones,
optional on-screen controls provide the same actions. They appear automatically
when no game controller is connected, and can be set to On or Off in App
settings. The overlay's small top button hides or restores it.

Press **R3** (right stick click) to show or hide the Android keyboard. The
shortcut is configurable in App settings. When on-screen controls are visible,
their **Keyboard** control also opens it. Android Back opens a Game menu with
**Show keyboard** for use when the controls are hidden.

## Save, quit, and manage settings

Press Android Back and choose **Quit PokeWilds**. If the game asks whether to
save, use Left/Right to focus **Yes** or **No**, then press A. You can also tap
the choice. Android Home leaves the session running; it does not save the game.

Long-press the app icon and choose **App settings**, or open it from the Game
menu. This screen includes graphics and viewport choices, on-screen controls,
the keyboard shortcut, and **PokeWilds game settings**. The game settings editor
covers non-keybinding options in `settings.txt`; changes apply when the next
game session starts. Stop the game before changing runtime or game settings.

Use **Export saves** after saving and quitting, then choose a destination in
Android's document picker. **Import saves** accepts compatible PokeWilds 0.8.11
JSON save archives. Export worlds before uninstalling: uninstalling removes the
app's private game data. See [save migration](standalone/migration/README.md)
for transferring existing worlds.

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

The APK packages third-party components. Review the
[upstream provenance and notices](standalone/UPSTREAM.md) before distributing
a build. Implementation details and validation evidence are in
[the standalone guide](standalone/README.md) and
[validation notes](standalone/VALIDATION.md).
