# HUD

```java
HudBar thirst = new HudBar("mymod", "thirst", HudAnchor.BOTTOM_CENTER, 81).label("Thirst").tone(HudBar.Tone.ACCENT);
HudLayer.main().add(thirst.visibleWhen(() -> Thirst.enabled()));
// on each client tick
thirst.value(Thirst.level() / 20F);
```

minuit draws `HudLayer.main()` on every frame outside screens, between the game's HUD and its chat, each
part in its mod's theme.

| Part | Use |
|---|---|
| `HudBar(modId, id, anchor, width)` | a filled bar: `label`, `text`, `icon`, `tone`, `color`, `fillColor`, `value(0..1)` |
| `HudPanel(modId, id, anchor, maxLines)` | a box of text lines: `title`, `line(index, words)`, `widths(min, max)` |
| `HudWidget(modId, id, anchor, component)` | any component, such as a `MeterComponent` |
| `HudBubble` | a speech bubble a HUD part draws itself, where no panel or entity can host one |

- A part starts at its `HudAnchor` (nine places), moves past the vanilla HUD and the parts added before it
  with 2 px between, then stays on screen. `at(anchor, dx, dy)` adds an offset; `z(order)` sets the draw order.
- A part added with the same mod and id replaces the old one; `remove(modId, id)` takes it out.
- Render thread only. Update values from a tick; do not rebuild parts.
