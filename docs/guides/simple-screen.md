# A simple screen

```java
// core: plain Java, no Minecraft type
public class PetPanel extends PanelComponent {
    public PetPanel(Theme theme, Words words, int left, int top) {
        super(theme, words, left, top);
        add(new ToggleComponent(at(new Box("sit", 10, 30, 180, 20)), words.get("mymod.sit"), false, on -> { }));
        add(new SliderComponent(at(new Box("speed", 10, 56, 180, 20)), 0, 10, 5, v -> { }).step(1));
        add(new ButtonComponent(at(new Box("ok", 10, 90, 80, 20)), words.get("gui.done"), () -> { })
                .role(ButtonComponent.Role.PRIMARY));
    }

    @Override public int width() { return 200; }
    @Override public int height() { return 120; }

    @Override
    protected void drawBackground(Draw d, int mx, int my) {
        theme.frame().panel(d, theme.palette(), left, top, width(), height());
    }
}

// client: the game side
public class PetScreen extends MinuitScreen {
    public PetScreen() { super("mymod.pet.title"); }

    @Override
    protected PanelComponent build(int w, int h) {
        return new PetPanel(Themes.byId("mymod"), words(), (w - 200) / 2, (h - 120) / 2);
    }
}

MinuitScreen.show(new PetScreen());
```

- `at(box)` places a box relative to the panel's corner.
- `build` runs again on every resize, so keep state outside the panel.
- `Themes.byId("mymod")` returns the look the player picked for your mod. Read it in `build`, never keep a
  `Theme` across screens.
- `MinuitScreen.show(screen)` opens a screen on every loader and version; `show(null)` closes it.
- Every `MinuitScreen` draws the theme tab. Override `showsThemeTab()` to hide it.
- Tab, the arrows, Enter and Escape move between widgets. Set `tooltip(...)` on any widget.
