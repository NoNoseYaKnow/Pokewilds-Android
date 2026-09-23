# Standalone prototype validation

## 0.6 save import and App settings shortcut (2026-09-23)

The importer accepts an app export ZIP, a native ZIP containing one `.sav`
world folder, or a selected `.sav` folder with its JSON ZIP files and PNG.
Regression tests cover each import path, malformed or multiple-world archives,
world-name collisions, and cleanup of temporary folder-import files. The
Android JVM suite and release build passed. The signed APK verifier reported
no errors or warnings, verified the signature and runtime payload hash, and
confirmed that no PokeWilds game files are bundled.

The issue reporter's `ghost_island.sav.zip` imported on the Pocket DMG while
the app's own save export continued to import. The user confirmed the imported
world works in game. Selecting the same extracted world folder through
Android's folder picker reached the expected collision check without replacing
the installed world. On a Samsung SM-S901U, long-pressing the app icon and
choosing **App settings** opened the initial menu without launching the game.

Version code 6, version name 0.6: `PokeWilds-Android-0.6-arm64.apk` is
207,147,288 bytes, SHA-256
`086b621c6e62f9973c85456dc481dbd69a40e72ae7de9f0add24bd65ca0c133a`.

## 0.5 mods and phone controls (2026-09-22)

The app now imports a ZIP or Android-selected folder into the game's private
`mods/` directory and exports a mods ZIP. Imports stage a merged directory,
reject unsafe paths, and recover an interrupted swap. The touch overlay keeps a
finger captured when it slides off a D-pad button, allowing movement to resume
when it slides back on. Auto visibility checks complete gamepad/joystick source
masks so a phone's keyboard or touchscreen does not count as a controller.

The signed implementation was installed over the existing app on a Samsung
SM-S901U running Android 16 without clearing app data. The game launched;
on-screen controls were visible with Show controls set to Auto, D-pad movement
resumed after sliding off and back on without lifting, and mod ZIP import and
export succeeded. The exported ZIP contained the exact test file imported
through Android's document picker. The Android JVM suite passed, including
eight mod-management and three touch-input regression tests. Two mod tests
cover packs with over 10,000 entries and a single enclosing folder.

The signed version-5 APK was then installed over the same app without clearing
its data. The user's `0.8.9.v1.5.0.zip` pack has 11,877 ZIP entries and its main
`mods/` directory sits inside one enclosing folder. It imported successfully,
and the user confirmed that the game launched with the mod changes visible.

The release metadata then advanced to version code 5 and version name 0.5.
The signed release APK passed the static verifier with zero errors or warnings:
its signature is valid, its runtime payload hash matches, and it contains no
PokeWilds game files. `PokeWilds-Android-0.5-arm64.apk` is 207,146,596 bytes,
SHA-256 `7e5a8037b384d71371041c7bf1592e3d0b5eb4f6ba4a5f16db13eee558f03be6`.
The initial touch and small-mod round trip used the same implementation before
the version increment; the large-pack check used the version-5 APK.

## 0.4 game acquisition candidate (2026-09-22)

The 0.4 candidate removes PokeWilds game files from the APK. Its first launch
downloads the SHA-256 pinned official v0.8.11 ZIP, or accepts a user selected
ZIP or extracted folder. Both paths stage and verify game files before making
them active. An existing installation recognizes its official game JAR and
keeps its saves, settings, and mods without downloading again.

The offline runtime rebuild and archive verification passed. The runtime
archive is 194,071,609 bytes, declares 628,336,041 expanded bytes and
`gameBytes=0`. The signed APK is 206,134,452 bytes. The APK verifier passed
with zero warnings and errors, confirmed the signature, found the pinned
`game-source.json`, and found no game files in the payload. Host Python tests
and Android JVM tests passed, including extraction of the cached official ZIP,
archive path rejection, activation preservation, and interrupted activation
recovery. Build and verification logs are under `standalone-build-logs/`.

The signed candidate was then tested on a connected Android 13 Pocket DMG:

| 0.4 device check | Result |
| --- | --- |
| Clean install, automatic download | A separate test application downloaded and installed the pinned official game, then reached the PokeWilds menu. |
| Offline ZIP import | With Wi-Fi disabled and the test application cleared, the Android file picker accepted the official v0.8.11 ZIP from Downloads. The game reached its menu. |
| Offline folder import | With Wi-Fi disabled and the test application cleared, the Android folder picker accepted the extracted official v0.8.11 folder from Download. The game reached its menu. |
| Signed upgrade | Android installed the exact signed 0.4 APK over the existing 0.3 application without clearing its data. Version code advanced from 3 to 4; the retained game launched directly to its menu without a download or source prompt. |

The tested release APK is `PokeWilds-Android-0.4-arm64.apk`, SHA-256
`ef252bfc04b3ea6ce38194ad30d839876b66ce9744a0b14f056e6d8682dc2c88`.
Wi-Fi was restored after the offline checks. Device testing covered acquisition
and startup; extended gameplay and device compatibility remain separate checks.

The measurements and emulator results below describe earlier bundled-game
prototypes. Their APK size and storage figures do not apply to 0.4.

For prototype 0.2 Auto viewport and the initial physical DMG follow-up, see
[AUTO_VIEWPORT_VALIDATION.md](AUTO_VIEWPORT_VALIDATION.md). The report below
records the original 0.1 emulator implementation pass.

The older prototype report below separates Android emulator evidence from its
original Pocket DMG acceptance gate. The game is the unchanged official
PokeWilds 0.8.11 JAR. The 0.4 device checks are recorded above.

## Environment

- macOS Apple Silicon build host; JDK 21, Android SDK 36, NDK 29.0.14206865.
- Android 13/API 33 ARM64 emulator, 1080×1920 display, host GPU backend.
- Application ID `local.pokewilds.standalone`, target SDK 33, ARM64 only.
- No `com.termux` or `com.termux.x11` app installed in the test emulator.
- Native GPU is the APK default. The emulator uses the app's **Vulkan
  compatibility** setting; native EGL on the emulator lacks the required
  surfaceless-context extension.
- The game reports VirGL through ANGLE Vulkan on Apple M1. This proves an
  accelerated execution path, not Adreno performance or handheld frame pacing.
- Logs and screenshots for the local run are in the workspace's
  `work/standalone-build-logs/`. A compact final evidence record accompanies
  the deliverable APK.

## Verified behavior

| Check | Evidence and scope |
| --- | --- |
| Full build entrypoint | `build.sh :android-app:assembleRelease` completed host packaging, source PRoot build, Docker close helper, payload construction, JVM tests, debug and release assembly. |
| Modern Android execution | Target 33 APK launches private PRoot/JRE and the original JAR from app-private storage using packaged native executables; game menu and world render. |
| GPU | Renderer identifies VirGL/ANGLE Vulkan; generated world and movement observed. |
| Audio | Guest PulseAudio playback stream observed; final signed APK also has an active 44.1 kHz Android AudioFlinger track owned by its PulseAudio PID. This verifies playback, not handheld latency/quality. |
| Controls | Injected Android D-pad, A/B and Start events reach the game; Start activates menu Go and the focused Swing dialog button. Hardware controller behavior remains untested. |
| Save and reload | New world generated; normal Quit displayed Swing save prompt; saved `game.json.zip`, map and spawn data parse as JSON; game loaded that world after an APK update. |
| Normal quit | All Java/PRoot/X11/VirGL/Pulse child processes disappear after the game exits. |
| Home, lock, Recents and resize | Home/ordinary launch retained the same processes. Exact final APK also survived sleep/wake, Recents return and portrait/landscape display resize with all five runtime PIDs unchanged and the menu rendering correctly. |
| Force-stop recovery | Separate warning confirmed; the final APK stopped all runtime children and reported an unsaved stop. Normal Quit remains the save path. |
| Cancel quit | **Keep playing** dismisses the Android quit dialog and leaves the session running. |
| Android app crash | `am crash` removed all runtime children in debug and final signed builds; the final signed app reloaded the last saved world and then saved/quit normally. |
| Export/import round trip | Android document picker exported and imported a real generated world into the clean signed release. After game load/save/quit, all `game.json` fields, settings and mods matched the original; the game created its normal `.sav.zip` backup. ZIP CRC checks passed. |
| Collision protection and mod merge | Existing world collisions refused; final signed app imported a second real world alongside identical bundled mods, retaining settings. Conflicting-file rollback and interrupted activation have JVM regression coverage. |
| Runtime cleanup | A deliberately protected mode-000 obsolete runtime directory was removed on next startup without touching the saved world. |
| Delayed permission prompt | Left Android notification prompt open for over 80 seconds, declined it, and reached the game menu. Surface timeout counts foreground focus time. |
| Signed update | Same signing key accepted by Android; Vulkan compatibility and 640×576 viewport persisted. Bundled runtime updated beside the old one and startup succeeded. |
| Low storage | With about 1 GiB free, startup stopped before extraction with a clear error; the app remained usable. |
| Interrupted preparation | Stopped during checksum verification and again during extraction with hundreds of MiB staged; retry replaced the partial stage and activated the complete runtime with emulator networking disabled. |
| Clean signed install | Installed a signed release after removing the debug app; imported its external save and reached the accelerated game without Termux apps or networking. |

## Regression coverage

**19 JVM tests and 6 host Python tests pass.** Android release lint has no
errors; remaining warnings concern English-only UI strings, backup-rule
modernization, and the deliberately conservative usable-space check.

JVM checks exercise safe tar extraction, traversal/link restrictions, protected
directory cleanup, save-archive validation, preservation of existing settings,
world collision refusal, mod merging/conflicts/rollback, durable interrupted-import
recovery before/after activation, and versioned game seeding. Host Python checks cover deterministic payload construction and
selected-world Termux export, invalid paths, symlinks and malformed saves.
The static APK verifier streams the payload checksum, checks required AArch64
ELFs and Android manifest, and invokes `apksigner verify`.

The original 0.8.11 game handles saving. The wrapper neither rewrites its save
format nor introduces an autosave. Legacy Kryo worlds are explicitly excluded
from migration; current JSON-in-ZIP worlds are supported.

## Storage and artifact measurements

The signed prototype APK is **334,736,183 bytes (319.2 MiB)**. Its compressed
runtime asset is **322,700,310 bytes (307.8 MiB)**, with **776,925,771 bytes**
of declared extracted payload contents and a separate **148,585,965-byte**
working game distribution. Android additionally extracts about **20.4 MiB**
of native libraries. Filesystem blocks, inodes and runtime-generated files
increase the on-disk footprint beyond these content-byte totals.

Before preparing a runtime the app requires **1,328,164,920 bytes (1.24 GiB)**
free after APK installation: expanded payload + working game copy + 384 MiB
of measured-overhead headroom. A retained old runtime already consumes space
and is therefore excluded from the available-space measurement. An abandoned
preparation stage is removed before checking whether a retry fits. Keep
additional room for worlds, export staging and the Android APK installer.

The emulator update was sampled separately for APK installation and payload
activation; the final evidence JSON records the observed peaks. Sampling can
miss a brief higher peak, so these observations are not a replacement for the
conservative free-space guard. Runtime and save-data directories are separate;
old runtime removal does not delete user worlds.

Observed `/data` usage during the same-key upgrade (2-second payload sampling):

| Stage | Before | Sampled maximum | Settled |
| --- | ---: | ---: | ---: |
| APK installation | 2,702,320 KiB | 3,060,776 KiB | 3,029,228 KiB |
| Runtime activation | 2,702,376 KiB | 3,508,344 KiB | 2,702,612 KiB |

These values include other emulator/system data. Android reclaimed old APK
storage after installation; runtime activation returned close to its baseline
after deleting the obsolete runtime. They are not device-independent capacity
promises.

## Remaining Pocket DMG acceptance

These are deliberately not inferred from emulator tests:

1. Install and launch without Termux/X11 installed on the handheld; verify the
   native Adreno 740 accelerated renderer and compare frame pacing with the
   working Termux setup in the same scene and power mode.
2. At least 30 minutes of representative play, including dense areas, battle,
   generation, music/effects, saving and clean exit.
3. Built-in D-pad/stick, A/B, Start, shoulders, held input, text entry, touch
   dialogs and an external controller. Check all viewport settings on the
   actual panel.
4. ES-DE import, repeated launch, quit returning to ES-DE, Home/Recents,
   screen lock, surface recreation and background memory pressure.
5. Export a copy of the user's Termux world, import it, compare player,
   inventory, party, structures and multiple maps, then save/reload. Keep the
   original Termux installation unchanged until this passes.
6. Keep an external save export for recovery.

Broader Android/API and 16 KB native-page compatibility are separate targets.
The 0.4 acquisition paths and signed upgrade were validated on the Pocket DMG;
the broader gameplay and compatibility checks above are still outstanding.
