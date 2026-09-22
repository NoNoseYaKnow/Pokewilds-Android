# Standalone runtime provenance and notice inventory

This file records where the standalone APK gets its upstream material and
what a generated payload actually carries. It is an engineering inventory,
not a legal opinion or a determination that the PokeWilds release may be
redistributed. The APK and generated runtime payload do not contain PokeWilds
game files. The app starts a first-launch download of the pinned official
release, or accepts a user selected ZIP or extracted game folder. Runtime
payload inputs are checked against the SHA-256 values in
[`runtime/manifest.json`](runtime/manifest.json). The generated
`notices/SOURCES.json` repeats those runtime input URLs and digests inside the
runtime archive.

## Inputs and provenance

| Material | Pinned input | Enters the build as | Notice/source evidence |
| --- | --- | --- | --- |
| PokeWilds | First-launch fetch from the [official `v0.8.11` release](https://github.com/SheerSt/pokewilds/releases/tag/v0.8.11), asset `pokewilds-otherplatforms.zip`, SHA-256 `5c0aca7f447ee6b4ed587f3ab2cefaf445219059d790862a7121c56a72fb22ba`; users may instead select a copy of those official files as a ZIP or extracted folder | Acquired on device after install and stored in app-private game storage; not copied into the APK or runtime payload. The download and selected ZIP are checked against the release hash; an extracted folder's game JAR is checked against the pinned JAR hash. | The release archive has `README.txt`; the JAR has `META-INF/LICENSE` and `META-INF/NOTICE` for dependencies. The release and inspected `v0.8.11` repository tree do not provide a PokeWilds project license or redistribution grant. This provenance record does not establish permission to redistribute the game or its assets. |
| Ubuntu guest base | Ubuntu Base 24.04.3 ARM64, SHA-256 `7b2dced6dd56ad5e4a813fa25c8de307b655fdabc6ea9213175a92c48dabb048` | `rootfs/` | Files supplied by the base archive remain in `rootfs`, including its package documentation where present. |
| Ubuntu guest packages | 133 exact Ubuntu Ports `.deb` artifacts listed under `ubuntu-noble-mesa-x11-arm64-package-closure` | Extracted data files under `rootfs/` | Extraction retains each package's `/usr/share/doc/<package>/copyright` and other shipped documentation. The manifest and `notices/SOURCES.json` retain each artifact URL, version, architecture, and digest. No package installer scripts run. |
| Eclipse Temurin | Linux AArch64 JRE `17.0.15+6`, SHA-256 `c89467f543bd434b71f3b748adeeeb1b2692f90242824b78205be1ae72ba385f` | `/opt/pokewilds/jre` inside `rootfs/` | The archive contains `NOTICE` and the per-module `legal/*/LICENSE` files; these are preserved by extraction. |
| Termux:X11 / Lorie | Repository commit `bd1cfadd74f98b548662a48b6e1d564aa434c86e`, including initialized submodules, as selected by `prepare.sh` | Android native `libXlorie.so` and the embedded `com.termux.x11` Java classes | The APK carries the root GPLv3 text plus license files found in the pinned Lorie submodules under `notices/native/termux-x11/`. The source checkout and generated corresponding source are not copied. See [`X11_RELOCATION.md`](X11_RELOCATION.md) for the checked hard-coded path inventory. |
| PRoot | Termux PRoot tag `v5.1.107.92`, source commit `7266fb3e8516535682f5a9c8f3a7e70f6506eddb`; archive SHA-256 `29385d1ddb619a9c4449ab512bfd55032034b22f724ddf98fc95ff300ea32135` | Rebuilt Android `libproot.so` and `libproot-loader.so` | The APK carries the exact pinned `COPYING` text under `notices/native/proot/`. The complete source checkout and build correspondence remain external to the APK. |
| talloc | Samba talloc `2.4.2`, archive SHA-256 `85ecf9e465e20f98f9950a52e9a411e14320bc555fa257d87697b7e7a9b1d8a6` as locked by `native/build-proot.sh` | Rebuilt `libtalloc.so.2.4.2` in the Android host closure | The APK carries the license statement from the pinned `talloc.h`, the exact LGPL-3 text supplied in the pinned Ubuntu guest, and the source `NEWS` license-history excerpt. This is notice evidence, not the talloc source tree. |
| libandroid-shmem | Termux `libandroid-shmem` tag `v0.7`, archive SHA-256 `1e5ff8459bc0a8c229dd8a94b27d119987e09ef3414331c2b5ebfff20b98e867` | Rebuilt `libandroid-shmem.so` | The APK carries the exact pinned source `LICENSE` under `notices/native/libandroid-shmem/`; the build also applies `native/patches/libandroid-shmem-runtime-tmp.patch` (SHA-256 `c276c03e38cb020d0c49b1be9ef3e5af3adb74de22da7f5f29f09ebbf73b0cbe`). The source tree itself is not copied. |
| Termux host package closure | 29 exact Termux packages in `packaging/host-packages.lock.json` (27 AArch64 packages and 2 architecture-independent packages), including ANGLE, VirGL, and PulseAudio | `host/` libraries, modules, configuration, and data | The extracted `host/share/doc/` tree carries package copyright/license files where the package ships them. The lock file is copied to `host/packages.lock.json`; it is provenance metadata, not a substitute for the notices or source of those packages. |
| VirGL, ANGLE, PulseAudio | VirGL package `1.3.0-1`, ANGLE package `2.1.24923-f09a19ce-2`, PulseAudio package `17.0-4`; package URLs and digests are in the host lock. The manifest also records the upstream VirGL, ANGLE commit, and PulseAudio source references used to select the packages. | Repacked host binaries (`libvirgl_test_server_android.so`, `libpulseaudio.so`) and their dependency closure | Package `share/doc` content is retained. The VirGL binary is modified by a checked, length-preserving ANGLE path rewrite; this does not make the complete package source or build recipe part of the APK. |
| Close helper | Checked-in `pokewilds-close.c`, SHA-256 `c4cdb336033eb873f131156198d542e922eaceb4e080b35b900a4660b25aa8ee` | Linux ARM64/glibc `rootfs/usr/local/bin/pokewilds-close` | This repository contains the source and the Docker build recipe. The helper links against the guest X11 libraries; its source has no separate third-party notice. |

The manifest's `license` fields describe upstream component metadata recorded
by the project. They do not classify the aggregate APK, do not settle the
game's terms, and do not replace each component's copyright and license text.

## What is carried today

The payload builder currently does the following:

1. It extracts the Ubuntu base, Ubuntu `.deb` data, and JRE without deleting
   their embedded documentation. It does not include the PokeWilds release.
2. It copies the Termux host tree's `share/` directory, which is why the
   generated archive includes `host/share/doc/*` for the package closure.
3. It writes `notices/SOURCES.json` with the manifest input list, URLs, and
   hashes. This is a source index, not a collection of license texts or
   corresponding source.
4. It packages the standalone APK's Termux:X11 root and subcomponent notices,
   plus the locally built PRoot, talloc, and libandroid-shmem notice texts,
   as Android assets under `notices/native/`. These assets are separate from
   `notices/SOURCES.json` and from the guest `/usr/share/doc` tree.

The existing `runtime/out/runtime.tar.gz` can therefore be audited by looking
for `rootfs/usr/share/doc/`, `rootfs/opt/pokewilds/jre/legal/`,
`rootfs/opt/pokewilds/jre/NOTICE`, `host/share/doc/`, and
`notices/SOURCES.json`. These paths show preserved upstream files; they do not
prove that every binary in the APK has a complete corresponding-source offer.

## Missing release essentials

The following must be resolved before publishing a distributable standalone
APK:

- The game files are fetched directly to the device on first launch, or
  supplied by the user through the document picker.
  The pinned hash establishes the official download's identity; it does not
  grant permission to redistribute the game or its assets. This app build
  contains neither. Keep that distinction clear in any release description.
- Ship or make available the corresponding source for the rebuilt PRoot,
  talloc, and libandroid-shmem binaries, including the checked-in patches.
  Their APK notice texts are now present, but build caches are not source
  artifacts.
- Preserve the complete Termux:X11 source/patch offer for `libXlorie.so` and
  its vendored components. The APK now carries the discovered license texts,
  but those notices are not a corresponding-source bundle.
- Recheck all 29 Termux package notices against the exact locked package set
  on every release. `host/share/doc` is package-provided coverage and may omit
  notices that a package does not install there.
- Keep the manifest, source indexes, notices, and source/build recipes
  alongside every distributed build. Regenerating an APK from a later moving
  checkout is not equivalent to the pinned inputs above.

Until those items are closed, this branch is a provenance-audited prototype,
not a release authorization.
