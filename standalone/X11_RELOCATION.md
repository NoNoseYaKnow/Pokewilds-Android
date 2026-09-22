# Termux:X11/Lorie relocation audit

The embedded display is built from Termux:X11 commit
`bd1cfadd74f98b548662a48b6e1d564aa434c86e` and its initialized submodules.
The standalone build applies `patches/x11-gl-header.patch`; its Java hunk
adds `POKEWILDS_NATIVE_DIR` support so `CmdEntryPoint` loads
`libXlorie.so` from the APK native-library directory. The APK also embeds the
`com.termux.x11` classes, so that package name is an internal class namespace,
not a request to launch an installed Termux:X11 app.

## Evidence

`work/native-runtime/xlorie-strings.log` is the `strings` inventory captured
from the probed `libXlorie.so` (`work/native-runtime/apk/libXlorie.so`). It
contains these Termux-specific literals:

| Literal | Meaning in upstream Xlorie | Standalone handling | Status |
| --- | --- | --- | --- |
| `/data/data/com.termux/files/usr/share/xkeyboard-config-2` | Termux package default for xkeyboard-config data | `RuntimeService` supplies `XKB_CONFIG_ROOT` to the embedded `app_process`; the payload also carries guest XKB data at `rootfs/usr/share/X11/xkb` | The old literal remains a fallback. Verify on every Xlorie update that the environment override is honored. |
| `/data/data/com.termux/files/usr/tmp` | Termux default temporary directory | `TMPDIR` and `XDG_RUNTIME_DIR` are set to the app-private `files/shared-tmp` directory for host processes | The old literal remains in the native binary; no generic binary rewrite is performed. |
| `/data/data/com.termux/files/usr/share/X11/xkb` | Termux X11 xkb fallback | Same `XKB_CONFIG_ROOT` override as above | Fallback retained; runtime probe required after native updates. |
| `/data/data/com.termux/files/usr/lib/libtermux-exec.so` | Optional Termux exec integration path referenced by upstream native code | The standalone APK ships no `libtermux-exec.so` and does not set a Termux prefix or preload path | Unresolved optional path; a device probe must show it is not required by the selected code path. |
| `com.termux.x11` | Java package and command entry point name | `RuntimeService` invokes the embedded `com.termux.x11.CmdEntryPoint` through Android `app_process` | Intentional internal namespace; it is not an external package dependency. |
| `TERMUX_X11_*` and `XKB_CONFIG_ROOT` | Upstream configuration/debug environment names | `XKB_CONFIG_ROOT` is set for host display startup; guest Java receives a clean `env -i` and explicit display/socket values | Debug variables are not set by the standalone supervisor. |

The report distinguishes a literal from an active path. A string in an ELF
file is not proof that the code path executes; the fallback paths above remain
release test targets because the standalone app does not patch arbitrary
Xlorie machine code.

## Explicit path overrides

For the host display process, `RuntimeService` sets:

```text
TMPDIR=<app files>/shared-tmp
XDG_RUNTIME_DIR=<app files>/shared-tmp
XKB_CONFIG_ROOT=<installed payload>/rootfs/usr/share/X11/xkb
POKEWILDS_NATIVE_DIR=<Android nativeLibraryDir>
CLASSPATH=<standalone APK>
```

It starts the display as `/system/bin/app_process / com.termux.x11.CmdEntryPoint
:0 -ac -nolisten tcp`, waits for the private `shared-tmp/.X11-unix/X0`
socket, and does not invoke `com.termux.x11/.MainActivity` or an external X11
package. Guest processes are entered through PRoot with `shared-tmp` bound to
`/tmp`; the guest gets `DISPLAY=:0`, `XDG_RUNTIME_DIR=/tmp`, and
`PULSE_SERVER=unix:/tmp/pulse-native`.

The embedded Lorie library still contains the upstream Termux strings listed
above. A complete relocation claim requires a fresh source checkout/build and
an Android probe covering keyboard discovery, temporary files, display socket
creation, and the close path. Current evidence establishes deliberate active
overrides, not universal relocatability of Termux:X11 binaries.
