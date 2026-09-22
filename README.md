# PokeWilds on Android with Termux

Run PokeWilds 0.8.11 on an Android handheld with Termux, Ubuntu, Termux:X11, and VirGL GPU acceleration. Includes a small **PokeWilds launcher APK** for Android frontends such as ES-DE, plus a **Quit** notification that requests a normal game exit.

Tested on an **AYANEO Pocket DMG running Android 13**. The game reported `virgl (Adreno (TM) 740)`, and gameplay was confirmed smooth with the built-in D-pad and A button. Other devices may need different graphics settings.

The launcher APK is a shortcut into this setup. It does **not** contain the game or replace Termux, Ubuntu, or Termux:X11.

## Install

1. Install [Termux](https://github.com/termux/termux-app/releases) and [Termux:X11](https://github.com/termux/termux-x11/releases/tag/nightly) from their official releases. On the Pocket DMG, use the ARM64 Termux APK and the regular universal Termux:X11 APK.
2. Open both apps once to finish their first-run setup.
3. In Termux, run:

   ```sh
   pkg update
   pkg install git
   git clone --branch main https://github.com/NoNoseYaKnow/Pokewilds-Termux.git
   cd Pokewilds-Termux
   bash pokewilds_install.sh
   ```

The installer downloads the game from [SheerSt's official release](https://github.com/SheerSt/pokewilds/releases/tag/v0.8.11), installs its runtime, and copies the launch and quit scripts into your Termux home directory. It reuses an existing Ubuntu installation and recognizes the old `/root/pokewilds` game directory. It does not overwrite the game's settings or saved worlds.

Run the installer from the complete repository checkout; downloading only the installer script is no longer sufficient.

## Display setup and play

For the Pocket DMG, these settings provide a fullscreen view with nearest-neighbor scaling and a low rendering resolution. Open Termux:X11 once before applying them:

```sh
am start -n com.termux.x11/.MainActivity
termux-x11-preference fullscreen:true showAdditionalKbd:false displayResolutionMode:custom displayResolutionCustom:480x432
~/pokewilds.sh
```

The launcher opens Termux:X11 automatically. If Android keeps Termux in front, open Termux:X11 manually. For subsequent launches, just run `~/pokewilds.sh` or use the APK below. The handheld runs the game independently of a computer.

The window-fitting commands in `pokewilds.sh` also use 480×432; change both the X11 setting and those dimensions if choosing another resolution.

## Install the Android launcher APK

Install [bin/PokeWilds-Launcher.apk](bin/PokeWilds-Launcher.apk) on the same device after completing the setup above.

The APK uses Termux's supported [RUN_COMMAND interface](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent). To enable this integration, run in Termux:

```sh
mkdir -p ~/.termux
printf '\nallow-external-apps = true\n' >> ~/.termux/termux.properties
termux-reload-settings
```

Open **PokeWilds** and allow it to run commands in Termux. If Android does not present that prompt, grant **Run commands in Termux environment** under Android Settings → Apps → PokeWilds → Permissions → Additional permissions. Also allow notifications for the Quit control.

This permission allows the authorized app to execute commands in Termux. The included APK sends only the fixed `~/pokewilds.sh` and `~/pokewilds-quit.sh` commands; it does not accept executable paths or shell commands from incoming intents.

### ES-DE

In current ES-DE versions, use **Utilities → Game importer**, select **PokeWilds**, and import it as an Android game. The import needs to be refreshed after installing a new app. Older installations using an Android-app importer can import the same PokeWilds app.

- App/package: `local.pokewilds.launcher`
- Launcher activity: `local.pokewilds.launcher.MainActivity`

See [ES-DE's Android documentation](https://gitlab.com/es-de/emulationstation-de/-/blob/master/ANDROID.md#launching-native-android-apps-and-games).

## Exit cleanly

1. Swipe down from the top of the screen to open Android notifications; fullscreen mode may require a second swipe.
2. Expand the **PokeWilds** notification and tap **Quit**.
3. If the game asks to save progress, choose **Save/Yes**. The prompt appears when the game considers its last save stale.
4. Press the device's Home button to return to your launcher after the game closes.

Quit sends `WM_DELETE_WINDOW` to the game so its normal save-and-close callback runs. It does not force-kill Java or destroy the X11 window. The game has no Quit entry in its own Start menu.

If you dismiss the notification or cancel a save-and-exit prompt, reopening the PokeWilds APK restores the notification. The launcher reuses an already running game and prevents simultaneous starts.

Without the APK, run `~/pokewilds-quit.sh` in another Termux session. You can also manually save from the game's Start menu before exiting.

## Files and diagnostics

| Location | Purpose |
| --- | --- |
| Termux `~/pokewilds.sh` | Start/resume the game |
| Termux `~/pokewilds-quit.sh` | Request a clean exit |
| Termux `~/.local/bin/pokewilds-close` | Native X11 close-request helper |
| Termux `~/pokewilds-launch.log` | Most recent game launch log |
| Termux `~/pokewilds-gpu.log` | GPU bridge log |
| Ubuntu `/root/pokewilds/` | Default game, `settings.txt`, and save directory |
| Ubuntu `/root/pokewilds-download/pokewilds-v0.8.11-otherplatforms/` | Also supported for the initial Pocket DMG setup |

To reach the game files, run `proot-distro login ubuntu` in Termux, then `cd /root/pokewilds` (or the alternate path above). Back up the game directory before uninstalling Termux; the game and saves are stored in its private app data.

### Graphics compatibility

The original setup forced CPU rendering. On the tested Pocket DMG, Ubuntu's Mesa 26.0.8 LLVMpipe renderer crashed with an illegal instruction; the same crash reproduced in `glxgears`. Softpipe avoided the crash but gameplay was very slow.

The updated launcher starts the official Termux `virglrenderer-android` bridge and selects `GALLIUM_DRIVER=virpipe` in Ubuntu. The game log should report `virgl (Adreno (TM) 740)` on the Pocket DMG rather than `softpipe` or `llvmpipe`. The game settings file did not need changes to achieve smooth gameplay.

The previous VNC launcher is no longer generated or maintained by this installer. Existing VNC files are left untouched.

## Build the APK

Source is in `launcher/`. The app needs no Gradle or downloaded Java dependencies. On Linux or macOS, install a JDK (17 or newer), Android SDK Platform 36, Android Build Tools 35.0.0, and `zip`, then run:

```sh
export ANDROID_SDK_ROOT=/path/to/Android/sdk
bash launcher/build.sh
```

The result is `bin/PokeWilds-Launcher.apk`. `ANDROID_BUILD_TOOLS_VERSION` can select another installed build-tools version. The build generates an ignored local signing keystore on first use; keep it to sign subsequent updates. Set `POKEWILDS_KEYSTORE` and `POKEWILDS_STORE_PASSWORD` to use your own signing identity. A different signing key cannot update an already installed APK without uninstalling that launcher first; uninstalling the launcher does not remove Termux's game files.

The compiled close helper is built on-device by the installer from `pokewilds-close.c`.

## Launcher opens a blank display after quitting

The graphics processes can survive after their filesystem sockets disappear. The launcher checks both X11 connectivity and the X11/VirGL socket files, restarts stale servers, and waits for their sockets before starting Java. Reinstall the current `pokewilds.sh` into Termux home to update an older installation; the shortcut APK does not need rebuilding for this fix. Startup diagnostics are written to `~/pokewilds-launch.log`, `~/pokewilds-x11.log`, and `~/pokewilds-gpu.log`.
