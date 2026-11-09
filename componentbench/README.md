# minuit component bench

Builds the library's components (in `core`) and previews them with no Minecraft and no Gradle: a Java2D renderer
draws every demo state in every theme to PNG.

## Commands

Run from `componentbench/`. Needs a JDK 17 or newer (`$MINUIT_JDK` is its folder, else `java` and `javac` on PATH),
Python 3, and a Gson jar (`$MINUIT_GSON`, else the newest one in `~/.gradle`'s cache). Nothing is downloaded.
Game textures and the font come from the Minecraft jar in `$BENCH_MC_JAR`, else from Loom's cache under
`$GRADLE_USER_HOME` or `~/.gradle`.

| What | Command |
|---|---|
| Check the tools, draw the test pictures into `bench/images` (not in git) | `python build.py setup` |
| Compile core, protocol and the bench | `python build.py compile` |
| Render the gallery | `python build.py gallery` |
| Render one demo, some themes, another scale | `python build.py gallery --demo Button --themes soft:grey:light,glass:clear:dark --scale 3` |
| Compile the Fabric 26.3 dev mod | `python build.py ingame build` |
| Run it in game | `python build.py ingame runClient` |

Run `setup` once before the first `gallery`: the picture demos read the test pictures it makes. The gallery is
`build/gallery/index.html`. `build/gallery/sheets/<Demo>.png` holds one demo's states in one picture. The renderer
runs one JVM per theme, each with `-Xmx768m`.

## In game

`python build.py ingame runClient` starts a Fabric 26.3 dev client with the bench. No account is needed. The bench
opens once at the title screen; the `<` and `>` buttons walk through the states and the theme tab changes the look.
Add `-Pshots=2` to save a screenshot of 2 states per demo to `ingame/run/screenshots` and quit (0 means every state).
The dev mod needs a 26.3 Fabric jar built from this branch (`-PminuitJar=<path>`), since the components are part of
core: see `ingame/build.gradle`, which also takes the game, loader and Fabric API versions from
`gradle/versions/26.3.properties`.

## Add a component

1. Put the component in `core/src/main/java/dev/dryaides/minuit/`. Core is Java 17 with no Minecraft on the classpath. Use the public package only, not `internal`, `client` or `port`.
2. Put one demo class in `bench/demos/`, package `bench.demos`. It is found on its own.

```java
package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.SliderComponent;

public final class SliderDemo implements Demo {
    @Override
    public void states(Stage stage) {
        stage.state("dragged", 180, 36, s -> {
            s.add(new SliderComponent(new Box("v", 8, 10, 164, 16), 0, 100, 0, v -> { }));
            s.drag(10, 18, 131, 18);
        });
    }
}
```

Each state is drawn once per theme. Coordinates start at the picture's top left. What a script can call on `Scene`:

- Build: `add`, `paint((draw, theme) -> ...)`, `noSurface`, `overlay`, `theme`, `palette`, `words`
- Mouse: `move`, `press`, `release`, `click`, `rightClick`, `hold` (drag, still held), `drag`, `scroll`
- Keys: `key(InputKey)`, `key(InputKey, shift)`, `type("text")`, `focus(widget)`, `held(ctrl, shift)`
- Time: `tick(n)`, `waitMs(ms)`, `settle(ms)` (animation time before the picture, 500 ms by default)

## Bench and game differ

- **Renderer:** Java2D, not the game's. Colours, rectangles and scissors follow the same calls, but nothing here is checked against the game.
- **Text order:** the bench draws strictly in call order. Recent game versions batch GUI drawing, so text may sort above later rectangles; use `d.nextLayer()` for anything drawn over text.
- **Font:** the game's bitmap font, read from the jar in Loom's cache. Without that jar a Java font with a 6 px advance stands in and layouts shift; the gallery header says which one was used.
- **Backdrop:** a painted gradient, not the dimmed, blurred world. Glass looks different in game.
- **Vanilla family:** `guiPart` is not implemented, so it uses the library's fallback drawing, not the game's sprites.
- **Items and entities:** items are the library's stand-ins, entities do not draw. Textures not found show in magenta and black.
- **Tooltips:** an approximation of the game's position and wrapping.
- **Time:** simulated, so pictures repeat exactly. The clock starts at a fixed value.
- **Package-private access:** components share the library's package, so `javac` allows package-private members. Those are not public API: do not use them.
