# Android launcher icon research

## Findings

Android launchers apply an OEM-selected mask to adaptive icons, so the same artwork can appear circular on one device and squircle-shaped on another. An adaptive icon is composed from separate foreground and background layers; each layer uses a 108 × 108 dp canvas, with a 66 × 66 dp central safe zone and 18 dp reserved around each edge for masking and launcher effects. Android recommends a clean foreground without a baked-in outer mask or background shadow. [Android adaptive icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)

Starting with Android 13, launchers that support themed icons can recolor the icon when an adaptive resource provides a monochrome layer. Without that layer, older Android versions and launchers may show the regular icon; Android 16 QPR 2 adds system fallback theming. [Android adaptive icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)

`android:roundIcon` is optional. Circular launchers use it with a circular mask, so a separate round asset is useful only when the design needs a circular-specific treatment. [Android adaptive icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)

## Repository findings and implementation

Before this change, the standalone app pointed both `android:icon` and `android:roundIcon` to `@drawable/ic_pokewilds`, a single 1254 × 1254 RGB PNG at `standalone/android-app/src/main/res/drawable-nodpi/ic_pokewilds.png`. The static home-screen shortcut also used this bitmap directly. The app's `minSdk` is 26, so all supported Android versions can use adaptive icons.

The PNG fills its entire square canvas with forest artwork; it does not have a transparent outer margin or a pre-rounded border. The reported “small square inside a large round icon” is consistent with a launcher fitting the legacy square bitmap inside its circular icon treatment. The exact launcher was not available to inspect, so this is an inference.

The application and its static shortcut now reference `@mipmap/ic_pokewilds`, an adaptive icon with the existing full-canvas art as foreground, a dark green background, and a simple monochrome Poké Ball layer for themed launchers. Android applies the launcher mask directly to the adaptive layers, so the square boundary of the legacy bitmap is no longer presented as the icon outline.

## Verification

The release APK built successfully. `aapt2 dump xmltree` confirms both application icon attributes resolve to `@mipmap/ic_pokewilds`; `aapt2 dump resources` confirms that resource resolves to compiled XML. The DMG uses ES-DE instead of the reported launcher, so its home screen cannot verify that launcher's mask. An emulator or that user's launcher can provide the final visual check.

## Primary sources

- [Android Developers: Adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)
