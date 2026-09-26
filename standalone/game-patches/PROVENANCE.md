# PokeWilds 0.8.11 bug fixes

This directory vendors the four-fix implementation from
[`Divinakra140/PokeWilds-0.8.11-BugFix-Patcher`](https://github.com/Divinakra140/PokeWilds-0.8.11-BugFix-Patcher),
commit `93f633a0fa7b6afe5b5318cff7868320fdddb505` (version 1.0, 2026-09-25).
The Java sources and source tests are kept under their original package and
retain their original copyright and SPDX notices. The floor patch is extended
locally with a versioned exact-placement entry in map save ZIPs and a loader
for that entry. The legacy `data.json` remains present. These changes make the
patched bytecode intentionally different from upstream v1.0. Sources are
licensed under MIT; see [LICENSE](LICENSE).

`build.sh` builds `dist/bugfix.jar`, which is both a Java agent and includes the
upstream offline patcher entry point. ASM 9.7 (`asm`, `asm-commons`, and
`asm-tree`) is fetched from Maven Central and checked against the SHA-256 values
pinned in the script. ASM is relocated into `local.pokewilds.bugfix.asm` to
avoid conflicts with the ASM copy bundled in PokeWilds. Its BSD-3-Clause notice
is included in the resulting JAR at `META-INF/licenses/ASM-LICENSE.txt`.

Build and run the upstream bytecode, class-verification, agent-loading, and
floor-map logic checks against the locally obtained official 0.8.11 JAR:

```sh
./build.sh
./test.sh /path/to/pokewilds.jar
```

The generated `dist/bugfix.jar` is the asset the Android app can bundle and
stage for the guest JVM. No PokeWilds game files are included here.
