# Standalone source and notice checklist

This index is intentionally factual. It does not grant rights to the
PokeWilds game or infer a license where the upstream release does not state
one. The authoritative input lock is
[`../runtime/manifest.json`](../runtime/manifest.json); package-level host
inputs are in [`../packaging/host-packages.lock.json`](../packaging/host-packages.lock.json).

## Files already present in generated payloads

- `rootfs/usr/share/doc/**`: package copyright and other documentation
  preserved from the Ubuntu Base archive and the 133 extracted Ubuntu Ports
  packages.
- `rootfs/opt/pokewilds/jre/NOTICE` and
  `rootfs/opt/pokewilds/jre/legal/**/LICENSE`: notices shipped by the exact
  Temurin JRE archive.
- `host/share/doc/**`: documentation shipped by the exact 29-package Termux
  host closure (27 AArch64 packages and 2 architecture-independent packages).
  `host/packages.lock.json` records package names, versions, filenames, URLs,
  and SHA-256 values.
- `notices/SOURCES.json`: generated URL/hash index for the four manifest input
  groups. It is metadata and is not a license bundle.
- `android-app/src/main/assets/notices/Termux-X11-LICENSE.txt`: the GPLv3
  license text used with the pinned Termux:X11/Lorie integration.
- `android-app/src/main/assets/notices/native/`: exact local notice texts for
  PRoot, libandroid-shmem, talloc's license statement and LGPL-3 text, plus
  the license files found in the pinned Termux:X11/Lorie submodules. The
  asset `native/README.txt` records source URLs, commits, archive hashes,
  submodule commits, and patch hashes.

The PokeWilds archive contributes its own `README.txt` and the JAR's
`META-INF/LICENSE`/`META-INF/NOTICE` dependency notices. Those files do not
identify a PokeWilds distribution license.

## Sources that are currently build-only

The native build downloads and modifies these sources. Their license texts are
packaged in the APK notice assets, but the source trees and generated build
products are not:

- Termux PRoot `v5.1.107.92` (`COPYING` is present in the source checkout).
- Samba talloc `2.4.2`.
- Termux libandroid-shmem `v0.7` (the source contains a license file).
- Termux:X11/Lorie at commit `bd1cfadd74f98b548662a48b6e1d564aa434c86e`,
  including submodules, plus the checked-in and upstream vendored patches.

Before release, add the exact corresponding-source location for each generated
binary. The notice asset is evidence of what was copied from the pinned local
checkout; it is not a complete corresponding-source bundle. Do not replace
this requirement with a generic project URL or with a broad claim that all
Termux packages are relocatable or uniformly licensed.

## Game distribution gate

The official PokeWilds `v0.8.11` release asset is pinned by SHA-256 in the
manifest. The inspected release/repository snapshot provides no top-level
PokeWilds license or redistribution grant. A public APK containing that game
artifact therefore requires an explicit terms decision from the game
copyright holder or a documented basis for distribution.
