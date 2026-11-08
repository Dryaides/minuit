# minuit per Minecraft version

How the code is split so one library builds for 1.18.2, 1.19.2, 1.20.1, 1.21.1 and 26.1.2 to 26.3, and how to add or fix a version.

## Layers

| Layer | Path | Holds | Minecraft on the classpath |
|---|---|---|---|
| core | `core/src/main/java` | every behaviour: widgets, themes, input routing, glide, bubble maths, toast timing, nine-slice, part tables. Java 17. | none |
| protocol | `protocol/` | wire format of the data-driven screens. Java 17. | none |
| shared common | `common/src/main` | classes whose Minecraft names are the same on every version (`Screen`, `Entity`, `ItemStack`, `KeyMapping`, `ServerPlayer`). Compiles unchanged against all variants. Java 17 source. | yes |
| port dirs | `common/src/port<dir>/java` and `/resources` | glue only: turn a game object into a core type, call core, apply the result. | yes |
| loader glue | `fabric/src/port<dir>`, `neoforge/src/port<dir>`, `forge/src/port<dir>` | entry points and registration; `forge/src/main` keeps only `mods.toml`. | yes |

Packages keep their names across layers (`dev.dryaides.minuit.client` has classes in core, common and a port dir). A public class keeps its fully qualified name when it moves.

Core reaches the game through the bridges in `core/.../internal/port` (`GamePlatform`, `ScreenBridge`, `TextureBridge`, `SkinBridge`, `ToastBridge`, `WorldView`, `ClientWire`, `KeyCodes`, `KeyStroke`, `CameraPose`, `Speaker`). `Platform` holds one slot per bridge with a no-op default, so benches and the site run without a game. The variant's `port/mc/Bridges.install()` fills the slots, and each loader's client entry calls it first.

## Versions

| Minecraft | `port_dirs` | `port_variant` | Loaders | Java |
|---|---|---|---|---|
| 26.1.2 | `26x,26` | `26` | NeoForge, Fabric, Paper | 25 |
| 26.2 | `26x,26_2` | `26_2` | NeoForge, Fabric, Paper | 25 |
| 26.3 | `26x,26_3` | `26_3` | NeoForge, Fabric, Paper | 25 |
| 1.21.1 | `12x,1211` | `1211` | NeoForge, Fabric | 21 |
| 1.20.1 | `12x,1201` | `1201` | Forge, Fabric | 17 |
| 1.19.2 | `11x,1192` | `1192` | Forge, Fabric | 17 |
| 1.18.2 | `11x,1182` | `1182` | Forge, Fabric | 17 |

`gradle/versions/<mc>.properties` sets these. `port_dirs` lists folder suffixes, generation first, version last; the last must equal `port_variant` (`common/build.gradle` throws otherwise). A generation dir (`26x`, `12x`, `11x`) holds what the versions of that generation share, a version dir holds what differs. `11x` is the generation before `GuiGraphics`: `GuiDraw` wraps the frame's `PoseStack` and the `GuiComponent` statics, and carries the pose to the model-view stack for each item draw, which the item renderer of those versions reads instead; the 1.18.2 and 1.19.2 dirs differ only in `GameScreens` (text components, FOV option) and `ThemeReload` (resource listing). A class lives in the generation dir or the version dir, never both.

`settings.gradle` reads `-Pmc_version` (default 26.1.2) and includes `:forge` when the properties file has `forge_version`, `:neoforge` otherwise, and `:paper` when it has `paper_api_version`. The loader modules (`multiloader-loader.gradle`) recompile the sources of `:core`, `:protocol` and `:common` against their own Minecraft, add `src/port<dir>` for each `port_dirs` entry, and write `META-INF/minuit/build.properties` (`mc_version`, `loader`, `port_variant`, `port_dirs`).

## Build one version

```
./gradlew build -Pmc_version=26.1.2 --no-daemon
```

Replace `26.1.2` with `26.2`, `26.3`, `1.21.1`, `1.20.1`, `1.19.2` or `1.18.2`. Use `-Dorg.gradle.jvmargs=-Xmx2G` and at most 4 workers. `build` includes `:common:fence`.

## Fences

`python tools/fence.py <tree>` (also `:common:fence`, run by `check`) enforces:

- `common/fence.tsv`: per file in `common/src/main`, how many distinct Minecraft names it uses (`net.minecraft` and `com.mojang` imports and fully qualified names). A file over its row fails, and a file with no row that names Minecraft fails. The numbers only go down.
- `src/main` stays Java 17 source: no `Math.clamp`, `getFirst`, `getLast`, `removeFirst`, `removeLast`, guarded or record patterns in `case`, or unnamed `_`.
- `common/port-fence.tsv`: per port dir, the code line count (comments and strings skipped). It only goes down. A port file with no Minecraft type fails (move it to core), two version dirs of one generation holding a file with the same code fails (move it to the generation dir), and one class in both a generation dir and a version dir fails.

Before raising a ceiling, move the logic to core or the file to the generation dir. If the growth is real glue (a new port dir, a new bridge implementation), run `python tools/fence.py <tree> --write`, read the diff of both `.tsv` files, and commit it with the change that justifies it. A new port dir gets its row the same way.

## Obfuscated versions

26.x ships unobfuscated. 1.21.1 and older do not.

- Fabric: `fabric/build.gradle` reads `fabric_obfuscated=true` and applies `dev.architectury.loom-remap` with `loom.officialMojangMappings()`, otherwise `loom-no-remap`. The remapping variant sets `useLegacyMixinAp = true` and `defaultRefmapName = 'minuit.refmap.json'`, the refmap that the 12x and 11x `minuit.mixins.json` name. NeoForge on 1.21.1 runs Mojang names and needs no refmap.
- Forge (1.18.2 to 1.20.1): `forge/build.gradle` applies `net.neoforged.moddev.legacyforge` with `version = <minecraft_version>-<forge_version>`. The jar is reobfuscated to SRG, so the Mixin processor (`org.spongepowered:mixin:0.8.5:processor`) writes the refmap at compile, and the manifest carries `MixinConfigs` because Forge 40 to 47 do not read mixin configs from `mods.toml`. The published jar is `reobfJar` in `build/libs`; `build/devlibs` has Mojang names.
- `:common` compiles against plain vanilla: NeoForm on 1.21.1 and 26.x, ModDevGradle `legacyforge` with `mcpVersion` on 1.18.2 to 1.20.1 (no NeoForm there).

The Forge compile floors are `forge_version=47.1.0` (1.20.1), `43.2.0` (1.19.2) and `40.2.0` (1.18.2). A later patch's API is a compile error on purpose.

## Vanilla light parts

The vanilla family draws the game's own textures so resource packs apply. `core/.../internal/PartTable` maps each `GuiPart` (button idle, hover, disabled, slot, hotbar, tooltip, checkbox, slider, tab, scroller) to art:

| Table | Art | Used by |
|---|---|---|
| `PartTable.ATLAS` | GUI sprite id (`minecraft:widget/button`, `minecraft:container/slot`), drawn with `blitSprite`; the sprite `.mcmeta` does the nine-slice | 26x, 1211 |
| `PartTable.WIDGETS` | region of `textures/gui/widgets.png` or a container texture, with a nine-slice border, drawn by core `NineSlice` | 1201 |
| `PartTable.LEGACY_WIDGETS` | as `WIDGETS`, but the slider uses the button art (no `slider.png` in those jars) and tabs have no row (no `tab_button.png`) | 11x |

The variant's `GuiDraw.guiPart` resolves the part. Renderers without a game (site, bench) get `false` and draw their pixel copies. If 1.21.1 lacks a sprite id, give that part a `Region` row in the 1211 table.

## Add a Minecraft version

1. Pick the generation. Same generation as an existing one (26.4 after 26.3): reuse its generation dir. A new API generation: a new `<gen>x` dir.
2. Create `gradle/versions/<mc>.properties`. Copy the nearest file and set `minecraft_version`, the range keys, `neoform_version` and `neoforge_version` (or `forge_version`), `fabric_version`, `fabric_loader_version`, `port_variant`, `port_dirs`, the `pack_*` formats, `paper_api_version` (26.x), and `fabric_obfuscated=true` if the jar is obfuscated.
3. Create `common/src/port<variant>/java` (and `fabric/src/port<variant>`, `neoforge/src/port<variant>` where the loader glue differs). Start from the nearest version's files.
4. Build with `./gradlew build -Pmc_version=<mc> --no-daemon`. Fix compile errors in the version dir. If the same fix is needed on two versions of a generation, put the file in the generation dir.
5. Add the row to `common/port-fence.tsv` with `python tools/fence.py . --write`.
6. Check the key codes: `KeyCodes` is filled from the version's own `InputConstants.KEY_*`; never write a number by hand.
7. Run the checks below, then update the version table here and in `README.md`.

## Fix a version

Find where the failing symbol lives. In `src/main`, it exists on every version: fix it there. In a generation dir, check the other versions of that generation. In a version dir, it affects only that version. Behaviour (a rule, a number, an order) belongs in core with a test, never in a port dir.

## Before merging

- `./gradlew build -Pmc_version=<v> --no-daemon` for every version the change touches (all seven for a change in `src/main`, `core` or a bridge). The fence runs inside.
- Core tests: `./gradlew :core:test -Pmc_version=26.1.2 --no-daemon`. The tests have no Minecraft, so one version is enough. `:common:test` only when dialogs change; it runs on 26.x only.
- Testbench (needs the showcase site sources, see the header of `testbench/run.sh`): `bash testbench/run.sh [shots folder]` draws every widget, window, form, reel, bubble and slot scene against the 26.1.2 merged jar. Look at the shots.
