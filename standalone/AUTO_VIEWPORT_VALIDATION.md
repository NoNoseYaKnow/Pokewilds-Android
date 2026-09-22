# Auto viewport — prototype 0.2

Auto — match screen is now the default for new installations. Existing fixed
viewport selections are preserved on upgrade. Select Auto under Manage saves
→ Viewport; the DMG has already been updated and switched to Auto.

- 16:9: 768×432.
- 4:3: 576×432.
- Pocket DMG: approximately 496×432.
- Square/portrait: retain a 480×432 canvas with letterboxing. The unchanged
  desktop menu clips at narrower ratios, so Auto preserves its minimum 10:9.

The app uses the available game surface and adapts when its size changes.
It increases visible world area on wider screens without stretching sprites
or rendering at the full panel resolution. X11 requires widths aligned to
eight pixels, so some ratios have a tiny fit margin. Fixed 480×432, 640×576
and 960×864 remain available. Native GPU remains the handheld default.

Verified for this APK:

- 24 JVM tests pass, including five new viewport geometry tests.
- Release lint and assembly pass; APK signature/payload/native checks pass.
- Signed upgrade accepted by emulator and DMG; fixed selection preserved.
- Auto enabled and menu rendered on the physical DMG using Native GPU.
- Saved world loaded at 16:9 in Android 13 ARM64 emulator.
- Live world resized to 4:3 and square with the same game/runtime process IDs.
- Square fallback displays the world and Swing save prompt without clipping.

The original game JAR and runtime payload are unchanged. Other handheld GPU
and controller compatibility still need validation on each device; automatic
viewport sizing does not establish universal device compatibility.

[Source branch](https://github.com/NoNoseYaKnow/Pokewilds-Termux/tree/standalone-apk-plan)
