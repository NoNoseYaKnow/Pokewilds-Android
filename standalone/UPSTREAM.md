# Standalone runtime inputs

The game stays at the official **PokeWilds v0.8.11** release. The prototype embeds
a private Linux runtime; it does not port or decompile the game.

- Game, Ubuntu Base, Temurin JRE, and Ubuntu packages: exact URLs and SHA-256
  hashes in `runtime/manifest.json`; corresponding package metadata/notices
  travel in the payload. Package indexes were resolved using Ubuntu's apt
  metadata; subsequent builds verify the locked artifact hashes.
- X11: `termux/termux-x11` commit
  `bd1cfadd74f98b548662a48b6e1d564aa434c86e`, including its pinned submodules.
  `prepare.sh` fetches it and applies `patches/x11-gl-header.patch`.
  The patch makes upstream patch application reliable on macOS, avoids an
  `Xlocale.h`/`xlocale.h` collision on case-insensitive filesystems, and allows
  the command entry point to load the app's extracted native library.
  Its GPLv3 license is included in APK assets under `notices/`.
- Android PRoot, talloc, and libandroid-shmem: versions, checksums, and patches
  in `native/build-proot.sh`. These are built for the app; PRoot contains no
  compiled Termux application prefix. The tiny runtime loader remains an
  executable even though APK packaging names it `libproot-loader.so`.
- Android VirGL/ANGLE/PulseAudio and dependency closure: package URLs, versions,
  and SHA-256 hashes in `packaging/host-packages.lock.json`. Package scripts are
  not executed. `packaging/host_packages.py` removes the extracted
  `lib/libbinder_ndk.so` Termux compatibility shim so Android's framework
  binder library is used. It also rewrites the pinned VirGL binary's absolute
  `/data/data/com.termux/files/usr/opt/angle-android/{gl,vulkan,vulkan-null}`
  literals to `./opt/angle-android/{gl,vulkan,vulkan-null}`, preserving ELF
  offsets and failing closed if the expected literals change. The prototype
  supplies library directories, PulseAudio module paths, and private socket
  locations explicitly; this is a pinned relocation step, not a claim that a
  complete Termux prefix is generally movable.

## Current probe boundary

The Android Gradle target is SDK 33. A separate modernprobe APK reached the
unchanged v0.8.11 JAR with llvmpipe on an Android 13 emulator without Termux
apps. The original private APK demonstrated generated-world creation and
movement, while its AWT quit path still lacks `libbrotlidec` and guest
PulseAudio still has a memfd negotiation failure. These are active fixes and
do not establish release or device-complete behavior for this standalone APK.

The runtime graphics profiles remain diagnostic: `native` is the default
accelerated profile, `software` selects llvmpipe, and `angle-gl`/
`angle-vulkan` exercise the relocated ANGLE paths. A fresh build should run
`build.sh` with `POKEWILDS_CLOSE_HELPER` unset so the pinned Docker close-helper
recipe produces the Linux ARM64 helper; an independently supplied helper is an
explicit override for verified local development.

Keep source URLs, patches, notices, and build recipes alongside distributed
builds. This inventory is not a determination of the game's distribution terms.
