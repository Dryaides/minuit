# minuit API

Mod id `minuit`. Depend on `[1.0,2.0)` in `neoforge.mods.toml` or `">=1.0.0 <2.0.0"` in `fabric.mod.json`.

## A first screen

```java
// core: pure Java, runs in game and in a bench
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

// common: the game side
public class PetScreen extends MinuitScreen {
    public PetScreen() { super("mymod.pet.title"); }

    @Override
    protected PanelComponent build(int w, int h) {
        return new PetPanel(Themes.byId("mymod"), words(), (w - 200) / 2, (h - 120) / 2);
    }
}

Minecraft.getInstance().setScreen(new PetScreen());
```

`build` runs again on every resize. Always get the theme through `Themes.byId("mymod")`: it returns
the look the player picked for your mod. Every `MinuitScreen` draws the theme tab that opens the
theme page; override `showsThemeTab()` to return false where it must not show (read on every
frame, so a screen can hide it while a drawer folds). `MinuitContainerScreen` has the same hook.

## Components

```java
add(new TabsComponent(at(new Box("tabs", 0, 0, 200, 18)), List.of("A", "B"), 0, i -> page = i));
add(new ListComponent<Pet>(at(new Box("pets", 0, 20, 200, 80)), 14, renderer, pet -> open(pet)));
add(new StepperComponent(at(new Box("rounds", 0, 104, 80, 16)), 1, 20, 3, v -> { }).onCommit(v -> save(v)));
```

| Group | Classes |
|---|---|
| Buttons and choices | `ButtonComponent` (`role`: `HERO`, `PRIMARY`, `SECONDARY`, `DESTRUCTIVE`; `selected`), `ToggleComponent`, `ToggleGroupComponent`, `TabsComponent`, `DropdownComponent` |
| Values | `SliderComponent` (`onChange` while dragging, `onCommit` on release), `StepperComponent`, `TextFieldComponent`, `ColorPickerComponent` |
| Layout | `ScrollComponent`, `SectionComponent`, `GridComponent`, `ListComponent`, `WindowComponent` (movable, place saved in `LayoutStore`) |
| Feedback | `MeterComponent`, `ProgressComponent`, `WaveComponent`, `SpinnerComponent`, `RadialComponent`, `ReelComponent` |
| Pictures | `AsyncImage`, `PlayerHeadComponent`, `PlayerSkins.of(uuid, name)`, `Draw.image` |
| Minecraft | `ItemPickerComponent`, `RichTextComponent`, `ToastSpec` with `client.Toasts.show(modId, spec)`, `EntityPreviewComponent`, `KeybindFieldComponent` |
| Inventory | `SlotLayout`, `SlotComponent`, `SlotGridComponent`, `PlayerInventoryComponent`, `HotbarComponent`, `client.MinuitContainerScreen` |

A button takes an item or a texture as its icon (`ItemView`, `SpriteIcon`); the item shrinks to the
largest crisp size the button fits.

## Minecraft parts

- `ItemPickerComponent`: a search grid of every item. Plain words search the name, `@mod` the
  namespace, `#tag` an item tag. Sort by game order, name or mod, ascending or descending, remembered
  per box name; `sortBy(key, label, comparator)` adds a mode of yours (by count, by price).
- `RichTextComponent`: the game's colour and style codes, written with `§` or `&`; a backslash prints
  the next character as is (`\&`). `[label](https://...)` is a link, opened through the game's
  confirm screen.
- `ToastSpec` and `client.Toasts.show(modId, spec)`: a toast in the game's own stack, in the player's
  theme. A spec with a key replaces the toast on screen with the same key.
- `EntityPreviewComponent`: an entity or a block in 3D; drag to turn it, or let it follow the mouse.
- `KeybindFieldComponent` over `client.MinuitKeys.binding(KeyMapping)`: one key or mouse button,
  Escape unbinds, a clash with another binding shows in red.
- Inventory parts: `SlotLayout` gives one layout to a menu (both sides) and its screen.
  `SlotGridComponent`, `PlayerInventoryComponent`, `HotbarComponent` and `SlotComponent` draw the
  wells under the game's items in a `MinuitContainerScreen`, where the game keeps every click. Outside
  a menu they show items with a `long` count (1.2k, 34M) and a click only calls back.
- On a `MinuitContainerScreen`, override `showsSlot(Slot)` to hide menu slots (a merchant's payment
  slots, rows of a folded drawer). The screen reads it on every frame and every pointer event, and
  treats a hidden slot as inactive: no item, hover, tooltip, click, drag or number key on it. The
  menu's own rules still reach it, so a shift-click or a double-click gather can move items in or out
  as the server decides; make the menu refuse them if that must not happen. `drawsCarried()` returning
  false hides the stack the game draws at the pointer (your scene shows it instead).
  `containerLeft()` and `containerTop()` place the container's corner, read on every `init`; vanilla
  centres it, and `build(leftPos, topPos)` gets your corner. `slotOffsetX(Slot)` and
  `slotOffsetY(Slot)` move where the game draws a slot's item, read on every frame; the pointer finds
  the slot where it is drawn. Feed them the `Glide` that slides the slots' wells (below).
- `drawsBackground()` returning false removes only the dim and blur; the panel, wells, hover and
  dropdowns still draw. `drawsLabels()` returning false hides the title and the inventory title (your
  scene draws its own). The labels pass is `final` because the panel overlay (open dropdowns) draws in
  it, after the slot items and before the carried item: do not override the vanilla label method.
- For item list mods (JEI, REI, EMI): `itemUnderMouse()`, `extraAreas()` and `dropTargets()` on both
  screens.

Set `tooltip(...)` on any widget. Tab, the arrows, Enter and Escape work on every panel; a custom
widget joins by overriding `navigable` and `activate`. Animations read `BaseComponent.clock` and
stop under `BaseComponent.reducedMotion`, which the player sets in `config/minuit-client.json`.

`Glide` slides anything in and out, eased on the clock: `new Glide(Slide.DOWN, 170)`, then `show()`
and `hide()`, which turn back mid-way without a jump, `replay()` for something that opens, and
`snap(shown)` for no motion. `widget.glide(glide, 58)` draws a widget 58 px off its place while away;
it takes no click or keyboard stop until it rests at its place. For your own drawing, read
`offsetX(distance)`, `offsetY(distance)` or `progress()` on each frame. Easings: `OUT_CUBIC`
(default), `IN_OUT_CUBIC`, `LINEAR`. Tab pages and screens opened with `MinuitScreen.show(screen,
Slide)` glide the same way.

## Speech bubbles

```java
WorldBubbles.say(pet.getId(), "Sit!");                                  // over an entity, client side
add(new BubbleComponent(at(new Box("hint", 10, 30, 160, 40)), "Pick a card")); // inside a panel
```

A new phrase over the same entity replaces its bubble. `say(id, text, lifeTicks)` sets how long it
stays; `clear(id)` and `clearAll()` remove bubbles.

## Server side

```java
MinuitServer.client(player).filter(c -> c.has(ClientInfo.FORMS)).ifPresent(c -> ...);
```

Empty when the player's client has no minuit.

## What is API

Public types in `dev.dryaides.minuit`, `dev.dryaides.minuit.client`, `dev.dryaides.minuit.server`
and `ClientInfo` do not break within 1.x. Not API: anything in a package named `internal`, and
`dev.dryaides.minuit.wire` (see `FORMS_WIRE.md`). Reserved for the data-driven screens after 1.0:
`dev.dryaides.minuit.form`, `.layout`, `.formui` and `.client.form`. Methods added to `Draw` or
`PanelComponent` within 1.x always have a body, so subclasses keep compiling.

Themes: see `THEMES.md`.

## Credits

The first widgets were inspired by the mod Laura by Vyrriox (MIT). Its licence ships in the jar at
`META-INF/licenses/mods-mc-laura-MIT.txt`.
