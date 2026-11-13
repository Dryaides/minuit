# Scroll boxes

```java
ScrollComponent list = add(new ScrollComponent(at(new Box("list", 10, 30, 180, 120))));
for (int i = 0; i < pets.size(); i++) {
    list.add(new ButtonComponent(at(new Box("pet" + i, 10, 30 + i * 22, 180 - ScrollComponent.BAR, 20)), pets.get(i), () -> { }));
}
```

The declared height (120 here) is the most the box takes, and the layout reserves it.

- Content up to that height shows whole; the box hugs it and draws no bar.
- Past it, the box drops to 80% of the declared height (`ScrollComponent.NORMAL`) and scrolls, so a bar
  always hides a fifth or more of the content.
- Set `fit = false` on a box that fills a fixed region (a screen body, a dialog or drawer view): it keeps
  its declared height and scrolls on any overflow.
- Add children at their unscrolled place, in screen coordinates, and leave `BAR` (6 px) free on the right.
- `step` sets the pixels per wheel notch; `bottomRoom` keeps room under the last child for a shadow.
- `reveal(box)` scrolls a box into view; `setScroll(value)` jumps.
