# PokeWilds 0.8.11 `settings.txt` values

## Scope and evidence

This audit covers the settings read by the bundled `game/pokewilds.jar` from
the `runtime-brotli.tar.gz` payload. The archive contains the default
`game/settings.txt`; the JAR contains the authoritative `Game.loadSettings`
and `TextSpeed.parse` implementations. The exact commands and focused
bytecode excerpts are recorded in
[`settings-values-agent.log`](../../standalone/standalone-build-logs/settings-values-agent.log).

The default file has 20 entries. Nine are keyboard bindings, two are gamepad
button bindings, and the nine non-binding entries below are the settings the
Android editor should expose. Defaults are the values in the JAR's
`Game.defaultSettings` table and the bundled `game/settings.txt`.

## User-facing non-binding settings

| Key | Default | Runtime conversion | Accepted values / fallback | Suitable Android control |
| --- | --- | --- | --- | --- |
| `muteMusic` | `false` | `Boolean.valueOf` | `true` (case-insensitive) means enabled; every other string becomes `false`. A missing key is filled with `false`. | Boolean list: `false`, `true` |
| `specPhysSplitEnabled` | `true` | `Boolean.valueOf` | Same boolean behavior. Missing falls back to `true`; malformed text does not raise an error, it becomes `false`. | Boolean list: `false`, `true` |
| `photosensitiveMode` | `true` | `Boolean.valueOf` | Same boolean behavior. Missing falls back to `true`. | Boolean list: `false`, `true` |
| `battleAnims` | `true` | `Boolean.valueOf` | Same boolean behavior. Missing falls back to `true`. | Boolean list: `false`, `true` |
| `textSpeed` | `mid` | `TextSpeed.parse` | Canonical values are `slow`, `mid`, `fast`, and `inst`. The parser explicitly recognizes lowercase `slow`, `fast`, and `inst`; every other string, including `mid` and uppercase spellings, resolves to `MID`. | List: `slow`, `mid`, `fast`, `inst` |
| `gamepadDeadZone` | `0.3` | `Double.valueOf` | Any Java `double` spelling that parses is accepted. There is no range or finite-number check. The value is compared against both positive and negative controller axes. Since controller axes are normalized around `[-1, 1]`, `0.0` through `1.0` are the meaningful range; values outside it are accepted but can disable or over-trigger direction detection. | List of safe values, for example `0.0`, `0.1`, `0.2`, `0.3`, `0.4`, `0.5`, `0.6`, `0.7`, `0.8`, `0.9`, `1.0` |
| `zoom` | `1.0` | `Float.valueOf(value + "f")` | Any syntactically valid float string is parsed. Only values greater than `0` are applied; `0` and negatives leave the starting scale unchanged. There is no explicit upper bound. A malformed value throws during settings loading. The value multiplies the display scale selected by the desktop launcher (base scale `2`, `3`, or `6`). | Validated positive decimal input, with suggested choices such as `0.5`, `0.75`, `1.0`, `1.25`, `1.5`, `2.0` |
| `drawReflections` | `true` | `Boolean.valueOf` | Same boolean behavior. Missing falls back to `true`. | Boolean list: `false`, `true` |
| `drawOverlays` | `true` | `Boolean.valueOf` | Same boolean behavior. Missing falls back to `true`. | Boolean list: `false`, `true` |

For the five booleans whose default is `true`, the game does not validate a
value against the text `true`/`false`: Java's `Boolean.valueOf` returns true
only for a case-insensitive `true`, and returns false for all other strings.
The editor should therefore offer only the two canonical strings.

## Additional recognized key

`frameBuffersEnabled` is read if it is present, using the same
`Boolean.valueOf` behavior, and controls the static frame-buffer capability
flag. It is **not** in `Game.defaultSettings`, is absent from the bundled
default file, and is not regenerated when the game creates a missing/default
settings file. It is therefore an advanced/legacy key rather than a normal
editor field. Its default in the class initializer is `true` when the key is
absent.

The command-line arguments `dev`, `cin`, `skipUpdateCheck`, and `dangerous`
are launcher flags, not `settings.txt` keys, and should not be added to the
game settings screen.

## Parsing and fallback details

`Game.loadSettings` removes text after `//`, trims the whole line, splits on
`=`, and stores keys in a case-sensitive `HashMap`. It fills missing entries
from `Game.defaultSettings` before converting the values. If the settings file
cannot be opened, the caller writes all 20 default entries and loads them.
Consequently, a missing visible key has the table default, while a present
but invalid boolean silently becomes false and a present invalid numeric can
abort loading.

`gamepadDeadZone` is parsed only inside the `gamepad != null` branch. With no
controller attached, its malformed value is not converted during that load,
but the settings editor should still constrain it to the safe list above.

The nine non-binding entries are independent of the excluded keys
`keyboard-*`, `gamepad-A`, and `gamepad-B`.

## Primary artifacts

* Payload: `standalone/runtime/build/assets/runtime-brotli.tar.gz`, member
  `game/settings.txt` and member `game/pokewilds.jar`.
* Classes inspected: `com.pkmngen.game.Game`,
  `com.pkmngen.game.TextSpeed`, `com.pkmngen.game.InputProcessor`, and
  `com.pkmngen.game.desktop.DesktopLauncher`.
* Release provenance: `standalone/runtime/manifest.json` records the pinned
  PokeWilds `v0.8.11` release asset and official release URL.
