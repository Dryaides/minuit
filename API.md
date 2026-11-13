# minuit API

Mod id `minuit`. Players install minuit as its own mod: depend on it, never bundle it (no jar-in-jar,
no `include`). Accept any 2.x: `[2.0,3.0)` in `mods.toml` or `neoforge.mods.toml`, `">=2.0.0 <3.0.0"` in
`fabric.mod.json`. See the wiki's Getting started page for the Gradle lines per loader.

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

MinuitScreen.show(new PetScreen());
```

`MinuitScreen.show(screen)` opens a screen on every loader and version; `MinuitScreen.show(null)`
closes it. `build` runs again on every resize. Always get the theme through `Themes.byId("mymod")`: it returns
the look the player picked for your mod. Every `MinuitScreen` draws the theme tab that opens the
theme page; override `showsThemeTab()` to return false where it must not show (read on every
frame, so a screen can hide it while a drawer folds). `MinuitContainerScreen` has the same hook.
To hide the tab over screens you do not own, call `MinuitClient.hideThemeTab(MyScreen.class)` (the
class and its subclasses) or `MinuitClient.hideThemeTabIn("com.example.mymod.")` (every screen class in
that package). Players turn the tab off with `containerTab` in `config/minuit-client.json`.

## Components

```java
add(new TabsComponent(at(new Box("tabs", 0, 0, 200, 18)), List.of("A", "B"), 0, i -> page = i));
add(new ListComponent<Pet>(at(new Box("pets", 0, 20, 200, 80)), 14, renderer, pet -> open(pet)));
add(new StepperComponent(at(new Box("rounds", 0, 104, 80, 16)), 1, 20, 3, v -> { }).onCommit(v -> save(v)));
```

| Group | Classes |
|---|---|
| Buttons and choices | `ButtonComponent` (`role`: `HERO`, `PRIMARY`, `SECONDARY`, `DESTRUCTIVE`; `selected`), `ToggleComponent`, `ToggleGroupComponent`, `TabsComponent`, `DropdownComponent` |
| Values | `SliderComponent` (`onChange` while dragging, `onCommit` on release), `StepperComponent`, `TextFieldComponent`, `ColorPickerComponent`, `RatingComponent`, `CopyFieldComponent`, `SwatchPickerComponent`, `CoordinatesFieldComponent` (X Y Z fields; `yRange`, `decimals`, `relativeToggle`, `here(supplier)`; `parse` reads a pasted "x y z") |
| Layout | `ScrollComponent`, `SectionComponent`, `GridComponent`, `ListComponent`, `WindowComponent` (movable, place saved in `LayoutStore`) |
| Feedback | `MeterComponent`, `ProgressComponent`, `WaveComponent`, `SpinnerComponent`, `RadialComponent`, `ReelComponent`, `CountdownComponent` |
| Pictures | `AsyncImage`, `PlayerHeadComponent`, `PlayerSkins.of(uuid, name)`, `Draw.image` |
| Minecraft | `ItemPickerComponent`, `RichTextComponent`, `ToastSpec` with `client.Toasts.show(modId, spec)`, `EntityPreviewComponent`, `KeybindFieldComponent`, `KbdComponent` |
| Inventory | `SlotLayout`, `SlotComponent`, `SlotGridComponent`, `PlayerInventoryComponent`, `EquipmentComponent`, `HotbarComponent`, `ExpandableHotbarComponent`, `HotbarDrag`, `client.MinuitContainerScreen` |
| Forms | `CheckboxComponent`, `RadioGroupComponent`, `NumberFieldComponent`, `OtpInputComponent`, `TextAreaComponent`, `RangeSliderComponent`, `FormFieldComponent`, `FormSummaryComponent` |
| Pickers and search | `ComboBoxComponent`, `TagInputComponent`, `DatePickerComponent`, `CalendarComponent`, `CommandPaletteComponent` |
| Lists and tables | `TableComponent`, `TreeComponent`, `TreeTableComponent`, `ReorderListComponent`, `KanbanComponent`, `TransferComponent`, `ItemRenderers` |
| Navigation | `BreadcrumbComponent`, `PaginationComponent`, `MenubarComponent`, `ContextMenuComponent`, `SidebarComponent`, `StepsComponent`, `AccordionComponent`, `SplitPaneComponent` |
| Surfaces | `DialogComponent`, `DrawerComponent`, `PopoverComponent`, `HoverCardComponent` (placed with `SurfaceSide`, `SurfaceAlign`), `CardComponent`, `AlertComponent`, `HudBubble` |
| Pictures and people | `Picture`, `PictureComponent`, `CarouselComponent`, `LightboxComponent`, `AvatarComponent`, `AvatarGroupComponent`, `BadgeComponent`, `SkeletonComponent` |
| Data | `ChartComponent`, `DonutChartComponent`, `GaugeComponent`, `HeatmapComponent`, `SparklineComponent`, `TimelineComponent`, `WheelComponent`, `StatComponent`, `DescriptionListComponent` |
| Incubating | `BalanceComponent`, `BingoBoardComponent`, `Carry`, `Cart`, `CartComponent`, `CheckoutComponent`, `Currency`, `EquipmentComponent`, `ExpandableHotbarComponent`, `HotbarDrag`, `HudAnchor`, `HudBar`, `HudCounter`, `HudLayer`, `HudPanel`, `HudPart`, `HudWidget`, `MapComponent`, `MapMarker`, `MapSource`, `MapWaypoint`, `MenuComponent`, `Mesh3D`, `ModelPreviewComponent`, `MultiStopToggleComponent`, `OverlayLayer`, `PictureBytes`, `PressStyle`, `PriceTag`, `Product`, `ProductCardComponent`, `Scene3DComponent`, `ScreenBackground`, `SeriesPalette`, `ShopFilter`, `ShopFilterBar`, `ShopToast`, `SideRailComponent`, `SlotLayout.Well`, `SlotPlacement`, `StackHand`, `VanillaHud`, `VoxelModel`, `ZoneGaugeComponent`; members of `BaseComponent` (`menuWells`, `enterFrom`), `ButtonComponent.Role.pressWeight`, `ChartComponent`, `DonutChartComponent` and `WheelComponent` (`seriesPalette`), `SlotGridComponent.menuKey`, `SlotLayout` (`hudHotbar`, `playerCellsHud`, `playerMenuEquipment`), `UiSound.press(Role)` |

A custom component draws through `Draw`. `Draw.scissor` takes its box in the same coordinates as a fill
at that point: inside a `Draw.push` the clip moves and scales with what it clips, on every game version.

A component's default texts ("Nothing here", "No results") come from the `minuit.*` lang keys; a setter
gives a mod its own text.

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
- `KeybindFieldComponent` over `client.MinuitKeys.binding(KeyMapping)`: a key or mouse button, with
  Ctrl, Shift or Alt held first on NeoForge and Forge (`Ctrl + M`); Fabric binds the key alone. A
  modifier let go on its own binds itself. Escape unbinds, a clash with another binding shows in red.
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
- Slot placement on a `MinuitContainerScreen`: `placedByPanel(Slot)` is true when a slot part of the
  panel draws that menu slot's well at the menu's place. `showsSlot` defaults to it (every slot on a
  screen without a panel), so a kit that only lays out the inventory hides the armour, offhand and
  crafting slots; the pointer never finds a slot no part places, even one `showsSlot` shows. The theme
  draws a placed empty slot's outline from the game's sprite (the armour and offhand ones) as it draws a
  `SlotComponent.empty`, and the game's grey one is never drawn; a part that sets `empty` keeps its own.
  Over the game's player menu, `@Incubating EquipmentComponent(name, x, y, leftPos, topPos, layout,
  crafting)` places the four armour slots and the offhand (and the 2x2 grid with its result) anywhere,
  in a `COLUMN` or a `ROW`, with the theme's ghost of the game's outlines. A kit that leaves them out
  still keeps them safe: while an armour or offhand slot of the menu is unplaced, a shift-click on a
  player slot goes rows to hotbar or hotbar to rows by left clicks instead of the menu's quick move,
  and the offhand key does nothing. A modded menu's quick move into its own unplaced slots stays the
  server's: place those slots or make the menu refuse them.
  `@Incubating placement()` gives the frame's `SlotPlacement`: `placed(index)`, `finds(index)`,
  `shiftX/shiftY(index)`, `reachable(x, y)` (false under the open overlay, a window or an interactive
  widget, where the screen finds no slot) and `covers(x, y)` (true over any part of the panel, so a click
  there does not throw the carried stack). A custom component lists its wells with the `@Incubating`
  `menuWells()` (`SlotLayout.Well`: a box and a shift). `@Incubating labelsFollowPanel()` returning true
  puts the inventory title on the panel's `PlayerInventoryComponent` on every `init`. A menu built for the
  HUD look takes `SlotLayout.playerCellsHud(box)` and `hudHotbar(box)`, both `@Incubating`; the screen
  moves the hotbar slots onto the bar by itself. In a development environment the kit logs a line for
  menu slots no component places and for a title drawn under a widget.
- `@Incubating ExpandableHotbarComponent`: a bar at the screen's edge that unfolds 1 to 3 inventory rows
  on hover. `ExpandableHotbarComponent.bottom(width, height, rows, hud)` (or `top(width, rows, hud)`),
  then `menuSlots(box)` with `SlotLayout.playerInventory` at the menu's own slot positions: the menu's
  player slots show in the bar and its rows, wherever they sit. `targets(() -> drops)` lists the scene's
  `ItemDropTarget.Drop` places; the bar lights the one the carried stack fits (`cued(x, y)`). On a
  `MinuitContainerScreen` the screen feeds `carried` and settles a drag with `HotbarDrag`: a stack picked
  from a slot and released or clicked on a target goes to `Drop.acceptDrop`. When it returns true the
  target owns the move: minuit empties the cursor on the client only, and the target removes the stack
  from the player's carried stack on the server through its own form or menu packet (inserting it
  without taking it from the cursor duplicates it). A stack the target refuses, or released over nothing,
  goes back with a left click on a slot that takes all of it: the slot it came from, else the first
  player inventory slot that does; a result slot (crafting, trade, stonecutter, furnace output) is never
  clicked, and with no such slot the stack stays on the cursor. A stack picked any other way (right
  click, a key) and clicked outside is thrown, as in vanilla. Picking and placing stay the game's slot
  clicks. `hold(true)` keeps the rows open; override `hotbarHoldKey()` with a `KeyMapping` your mod
  registers and the rows stay open while it is held (minuit registers no key of its own).
- `drawsBackground()` returning false removes only the dim and blur; the panel, wells, hover and
  dropdowns still draw. `drawsLabels()` returning false hides the title and the inventory title (your
  scene draws its own). The labels pass is `final` because the panel overlay (open dropdowns) draws in
  it, after the slot items and before the carried item: do not override the vanilla label method.
- For item list mods (JEI, REI, EMI): `itemUnderMouse()`, `extraAreas()` and `dropTargets()` on both
  screens.

Set `tooltip(...)` on any widget. Tab, the arrows, Enter and Escape work on every panel; a custom
widget joins by overriding `navigable` and `activate`. Animations read `BaseComponent.clock()` and
stop under `BaseComponent.reducedMotion()`, which the player sets in `config/minuit-client.json`.

`Glide` slides anything in and out, eased on the clock: `new Glide(Slide.DOWN, 170)`, then `show()`
and `hide()`, which turn back mid-way without a jump, `replay()` for something that opens, and
`snap(shown)` for no motion. `widget.glide(glide, 58)` draws a widget 58 px off its place while away;
it takes no click or keyboard stop until it rests at its place. For your own drawing, read
`offsetX(distance)`, `offsetY(distance)` or `progress()` on each frame. Easings: `OUT_CUBIC`
(default), `IN_OUT_CUBIC`, `LINEAR`. Tab pages glide the same way.

## Speech bubbles

```java
WorldBubbles.say(pet.getId(), "Sit!");                                  // over an entity, client side
add(new BubbleComponent(at(new Box("hint", 10, 30, 160, 40)), "Pick a card")); // inside a panel
```

A new phrase over the same entity replaces its bubble. `say(id, text, lifeTicks)` sets how long it
stays; `clear(id)` and `clearAll()` remove bubbles.

## Server side

```java
Form form = Form.custom("mymod:settings")
        .title(Text.lit("Settings"))
        .toggle("pvp", Text.lit("PvP"), false)
        .button("save", Text.lit("Save"), Role.PRIMARY)
        .build();
MinuitServer.forms().open(player, form, new FormHandler() {
    @Override public void pressed(FormSession session, String id) {
        if (id.equals("save")) save(session.value("pvp"));
    }
});
```

`MinuitServer.forms()` shows a form on a player's screen. minuit draws it when the player's client has
minuit, else the player gets a vanilla dialog. Call it on the server thread. `Form.menu(...)` builds a
list of buttons, `Form.notice(...)` a yes or no question. Form types live in
`dev.dryaides.minuit.form`.

`MinuitServer.client(player)` returns the player's `ClientInfo`, empty when their client has no
minuit: `MinuitServer.client(player).filter(c -> c.has(ClientInfo.FORMS))`.

A Paper plugin opens the same forms through `PaperForms.of(plugin).forms()`.

### Paper plugins

A plugin shades `minuit-paper` and `minuit-protocol` into its jar and relocates both under its own
package, so its copy never resolves to another plugin's classes:

```groovy
plugins { id 'com.gradleup.shadow' version '<current>' }
dependencies {
    implementation 'dev.dryaides:minuit-paper-26.1.2:2.0.1'
    compileOnly 'io.papermc.paper:paper-api:26.1.2.build.74-stable'
}
shadowJar {
    relocate 'dev.dryaides.minuit', 'com.example.myplugin.libs.minuit'
}
```

Several plugins on one server can each shade a copy. The copies find each other through one Bukkit
service of the JDK type `ConcurrentMap`, which relocation leaves alone: they share the session ids
and the record of which plugin's form each player sees, so a form from one plugin ends another's as
`REPLACED` and only the plugin that showed a form reads its input. A copy built on another wire
protocol than the first one enabled throws at `PaperForms.of`; update the plugins together.

A form encodes to at most 256 KiB on `minuit:screen`, under the 1 MiB that vanilla and Bukkit allow
for a plugin message. `open` throws `IllegalArgumentException` for a bigger form and leaves the shown
one in place; split it into tabs or several forms.

`DialogCommand` shows any of the plugin's forms as the vanilla dialog, to check the fallback:
register it under a command such as `/minuit dialog <form id> [player]` (permission `minuit.dialog`).

## What is API

The API is stable within 2.x: no 2.x minor release breaks it. That covers public types in
`dev.dryaides.minuit`, `dev.dryaides.minuit.client`, `dev.dryaides.minuit.server` and
`dev.dryaides.minuit.form` (forms and `ClientInfo`). Not API: anything in a package named `internal`,
`dev.dryaides.minuit.wire` (see `FORMS_WIRE.md`), and types marked `@Internal`, which are minuit's host and
loader glue, public only because another minuit package names them. Host state (sound cues, clock, held
keys, motion) is read through `BaseComponent`'s static getters; only minuit's host sets it. Methods added to `Draw` or `PanelComponent`
within 2.x always have a body, so subclasses keep compiling.

Types marked `@Incubating` may change in a minor release; they leave incubation by losing the mark.
`FrameStyle` parts added after 1.0 (`plate`, `dot`, `row`, `popup`, the mixed `checkbox`, `radio`)
have a default drawn from the palette and `corner()`, so a family needs no new code.

Themes: see `THEMES.md`.

## Credits

The first widgets were inspired by the mod Laura by Vyrriox (MIT). Its licence ships in the jar at
`META-INF/licenses/mods-mc-laura-MIT.txt`.
