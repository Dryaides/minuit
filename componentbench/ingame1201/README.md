# Frame bench, 1.20.1 Fabric

A dev mod outside common/ and outside the minuit jar. With `config/minuit_bench.properties` in the game dir, it
opens a screen 60 ticks after the player is in a world, turns vsync and the frame cap off, and times one demo state
at a time (bench/demos) in each theme: 120 warm frames, then 600 timed frames per case. For each case it logs the
frame time (render to render, world behind included), the panel's own draw time, and the Draw calls the panel made
per frame (fills, text, blits, images, game GUI parts, items, scissors, pushes). It then logs the median of the
rounds per state, scale and theme, writes `[bench] DONE` and closes its screen. Keys and defaults: FrameBenchMod's
javadoc.

## Run (one client session)

1. Build the minuit 1.20.1 Fabric jar and the bench on the mini-PC, in one call:

       python D:/CODE_PROJECTS/KNOWLEDGE_BASE/tools/minipc/remotebuild.py --project minuit --tree D:/CODE_PROJECTS/minecraft_mods/minuit/mod/wt-v2 --mc 1.20.1 --tasks ":fabric:build :ingameBench:build -Pingame_bench" --fetch

   `-Pingame_bench` includes this folder as `:ingameBench` (the repo's settings.gradle, 1.20.1 only); it compiles
   against `:fabric`'s jar and writes `minuit-frame-bench-1.20.1-0.jar` into fabric/build/libs, so `--fetch` brings
   it to `minuit/_temp/remotebuild/jars/1.20.1/` beside `minuit-fabric-1.20.1-<version>.jar`. Builds without the
   property never see the bench.
2. (Alone, without the repo build: in mod/wt-v2/componentbench, `python build.py ingame1201 build`, with
   `-PminuitJar=<jar>` naming a minuit 1.20.1 Fabric jar; the jar lands in ingame1201/build/libs and the host's
   `jars` must then point there.)
3. Run it through the shared tool, from D:/CODE_PROJECTS/minecraft_mods/minuit:

       python D:/CODE_PROJECTS/_mc-capture/tools/run.py prep-host _temp/mctest/hosts/fb1.20.1-fabric/host.toml
       python D:/CODE_PROJECTS/_mc-capture/tools/run.py check _temp/mctest/hosts/fb1.20.1-fabric/host.toml _temp/mctest/scripts/framebench-1.20.1-fabric.txt
       python D:/CODE_PROJECTS/_mc-capture/tools/run.py launch _temp/mctest/hosts/fb1.20.1-fabric/host.toml _temp/mctest/scripts/framebench-1.20.1-fabric.txt --dry-run
       python D:/CODE_PROJECTS/_mc-capture/tools/run.py launch _temp/mctest/hosts/fb1.20.1-fabric/host.toml _temp/mctest/scripts/framebench-1.20.1-fabric.txt

   The host sets the cases in its `[config]` block (Dialog, Accordion, Card, FormField, Table; bubbly then bedrock;
   3 rounds; GUI scale 2 then 3). The script sizes the window to 1920 x 1080, waits for the bench screen to open and
   close, then quits.

To compare a patch (fold3/rounded-rows.patch, grad/gradient-runs.patch): run once, apply the patch, redo steps 1
and 3, run again. Only compare runs made on the same machine state.

## Read the result

In `minuit/_temp/mctest/runs/<tag>/latest.log`:

    grep "\[bench\] MEDIAN" latest.log

One line per state and scale:

    [bench] MEDIAN scale=2 Dialog/<state>: | bubbly:<chroma>:light 2.310 ms/frame, panel 0.840 ms, fills 1234 | bedrock:<chroma>:light 1.100 ms/frame, panel 0.200 ms, fills 210 (frame x0.48, panel x0.24 of the first)

- `ms/frame` is the whole frame; `panel` is the CPU time of the panel's draw call alone, where 1.20.1 pays for each
  fill. `x` figures compare each theme with the first one listed.
- `fills` is the fill count per frame: a patch that folds fills shows here first; the ms show whether it paid.
- The per-case lines (`grep "\[bench\] case"`) give the spread between rounds and the other call counts.
- `[bench] SCRIPT` or `[bench] THREW` lines name a demo script that failed in game; its numbers are not comparable.
