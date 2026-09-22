# Save migration from Termux

Save and quit PokeWilds in the existing Termux installation first. From
Termux, run:

```sh
./standalone/migration/export-termux.sh
```

This exports `settings.txt`, `mods`, every world, and each matching `.sav.zip`
backup. To export one world and its matching backup, pass its basename:

```sh
./standalone/migration/export-termux.sh my-world.sav
# `.sav` may be omitted; `my-world.sav.zip` also selects `my-world.sav`.
```

The script validates the selected world and JSON save members, refuses
traversal and symlinks, writes `/data/data/com.termux/files/home/pokewilds-saves.zip`
only after the archive is complete, and never removes or moves the source.

To make the archive visible to Android, use the Termux share command and
choose a Downloads/files destination:

```sh
termux-share "$HOME/pokewilds-saves.zip"
```

If `termux-share` is unavailable, run `termux-setup-storage` once, then copy
the archive to shared Downloads:

```sh
cp "$HOME/pokewilds-saves.zip" "$HOME/storage/downloads/"
```

In PokeWilds Standalone, open **Manage saves → Import saves** and select the
archive from Downloads. The standalone app stages and validates the import;
existing worlds/settings are not overwritten.
