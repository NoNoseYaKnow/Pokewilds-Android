# Field move wheel and zoom provenance

The source in `src/com/pkmngen/game/OdinAgent.java` adapts the game-side wheel
action and camera zoom logic from the user-provided
`PokeWilds-v0.8.11-RadialWheel-ZoomControls.patch` in the project folder. It is
built against the exact official PokeWilds 0.8.11 game JAR, verified by SHA-256
in `build.sh`. The official game JAR is not modified or bundled in the APK.

The proposal's `OdinInputHook` and its replacement Android `TouchControls` are
not included. This agent has no bytecode transformer or general input hook. It
only accepts wheel and zoom commands from the Android app; the existing touch
overlay and gamepad mappings remain in place. None of the proposal's image
assets are included.

The proposal did not establish redistribution terms. This record describes
technical origin and is not a license grant.
