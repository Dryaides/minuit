# Popups

```java
ButtonComponent more = add(new ButtonComponent(at(new Box("more", 10, 10, 60, 20)), words.get("mymod.more"), () -> pop.toggle()));
pop = add(new PopoverComponent(more, this).title(words.get("mymod.more")).text(words.get("mymod.more.body")));
pop.add(new ToggleComponent(new Box("grid", 0, 0, 120, 12), words.get("mymod.grid"), true, on -> { }), 8, 34);
```

Popovers, dropdown lists, combo boxes, date pickers and context menus draw in the panel's `OverlayLayer`:
over every widget, outside every scissor, and first to get input. A popup opened inside a `ScrollComponent`
or a window needs no extra code.

- A click outside closes the popup and presses nothing beneath; `passClicks(true)` lets it through.
- Escape closes it and gives the focus back.
- A popover flips to the other side of its anchor when it does not fit. `side(SurfaceSide)` and
  `align(SurfaceAlign)` place it; `area(box)` limits where it may go.
- A child's position is relative to the card's top left.

## Your own popup

In a custom component's draw, queue the popup instead of drawing it:

```java
d.overlay(this, mx, my, (dd, x, y) -> drawList(dd, x, y))
        .keys()                         // takes every key while open
        .openWhile(() -> this.open)
        .onClose(() -> this.open = false);
```

The root panel runs the painter after the screen, under the transform in effect when you queued it.
