# Themes in minuit

A theme is a family (the shapes: frames, bevels, buttons), a chroma (the colours) and a variant
(`light`, `dark` or `auto`). Any chroma dresses any family. Both are data files under
`assets/<namespace>/minuit/` in a mod jar or a resource pack, reloaded with F3+T. The kit ships the
families `bedrock`, `slate`, `glass`, `bubbly` and `soft`.

## A chroma

`assets/mypack/minuit/chromas/moss.json`:

```json
{"order": 0,
 "variants": {
   "light": {"surface": "#FFEBE3F7", "text": "#FF271D3B", "primary": "#FF6236C4", "...": "..."},
   "dark":  {"surface": "#FF1E1830", "text": "#FFF1EBFF", "primary": "#FFA98BFF", "...": "..."}}}
```

Each variant lists every `Palette` role, as `#RRGGBB` or `#AARRGGBB`. Copy the kit's
`core/src/main/resources/assets/minuit/minuit/chromas/lilac.json` and change the values. A chroma
may ship one variant. The name on the theme page is the lang key `minuit.chroma.moss`.

## A family

`assets/mypack/minuit/themes/moss/family.json`:

```json
{"style": "slate", "order": 80, "chromas": ["moss", "teal"],
 "override": {"light": {"surface": "#99"}}}
```

`style` names a drawing style from `FrameStyles` (`bedrock`, `slate`, `leather`, `glass`, `soft`, `bubbly`, `vanilla`);
a new shape needs Java there. `chromas` lists the recommended ones, the first is the default.
`override` changes roles in every chroma the family draws: `"#AA"` sets only the alpha. The name is
the lang key `minuit.family.moss`. A file that does not parse is skipped with one log line naming
the file and the field.

## Chart series colours

A chart, donut or wheel colours its series from a `SeriesPalette`, set per widget with
`seriesPalette(...)`. `THEME`, the default, follows the theme, and a chroma variant may list its own
with `"series": ["#FF3E8E1C", "..."]`. `SHADES` follows the theme's accent. `CONTRAST`, `VIVID` and
`PASTEL` are fixed lists, and code can pass its own with `SeriesPalette.of(...)`. A mod or pack adds a
named one at `assets/mypack/minuit/palettes/autumn.json`:

```json
{"colors": ["#C0392B", "#E67E22", "#F1C40F", "#27AE60"]}
```

Code picks it with `SeriesPalette.byId("mypack:autumn")`. After the last colour the list starts
again.

## The player's choice

`config/minuit-client.json`:

```json
{"global": {"family": "slate", "chroma": "teal", "variant": "auto"},
 "mods": {"mymod": {"family": "leather", "chroma": "paper", "variant": "light", "accent": "midnight"}}}
```

A mod with no entry wears `global`; with no `global`, the theme it registered. `auto` takes the dark
variant when the chroma has one. `accent` replaces only the accent colours; the ids are `blue`,
`green`, `orange`, `rose`, `violet` and `midnight`. A pack adds one with `assets/mypack/minuit/accents/moss.json`,
`{"order": 70, "light": "#FF4D7C0F", "dark": "#FF84CC16"}` (`order` optional); the picker shows it after the
built-ins, tooltip from the lang key `minuit.picker.accent.moss`. The player edits all this on the theme page.

## A mod's own look

```java
Themes.register(Themes.defaultTheme().as("mymod"));                 // follow the kit's default (Vanilla, light)
Themes.register(Themes.LEATHER.withIcon(Icon.CLOSE, myArt).as("mymod")); // a preset with your icon
Theme screenTheme = Themes.byId("mymod");                           // what your screens open with
new ThemeScreen(parentScreen, "mymod");                             // the theme page, for your mod
```

Register at client init. The registered theme is the default until the player picks one. A family
that only your theme page offers is a `family.json` in your own namespace with `"private": true`.

## Layout and pixel rules

Rules for anyone who writes a component or a family. Each one lives in one shared place; a component calls it
rather than drawing its own version.

**R2. Nested content keeps 1 px of face inside the frame.** A popup's rows, the ring around its lit row and its
scroll bar start at the frame's inner edge (`panelInsets`, per side) plus 2: one pixel of face, one for the ring
(`PopupList.inset`). Cases checked: vanilla insets 3 on every side, rows now at 5 (the keyboard ring used to sit
on the light bevel); bubbly 3, 3, 3, 4, rows at 5 left, top and right as before, 6 at the bottom (the ring sat on
the last face row); slate 3, rows at 5; bedrock and glass 2, 1, 1, 1, rows at 4 on top (under the lit rule) and 3
on the sides; soft 2, rows at 4 (its face starts at 1, so it keeps 2 px). Users: ContextMenu, ComboBox, TagInput,
CommandPalette, DatePicker, and the 1.0 DropdownComponent (`listHeight(frame, lines)`; the old `listHeight(lines)` answers
for vanilla).

**R3. A cut over a pixel picture falls on a whole source pixel.** AvatarGroup steps so that what the next head
leaves of a skin face ends on a face-pixel edge of the 8 px face drawn at the head's side (`faceEdge`, sampled at
pixel centres as the game does), taking the first edge at or past a third of overlap. A 20 px head steps 16:
15 px of face, six whole face pixels.

**R4. A ring around a rounded tile reads whole.** A head under another one ends in a straight cut on the next
ring, so the ring runs straight from top to bottom and then follows the next tile's corner, with no sliver of
the head beneath showing past the corner (AvatarGroup, every family; bubbly and soft show it most).

**R5. A ring shares the corner of what it surrounds.** The panel's focus ring is one 1 px line 2 px out, of
radius `focusCorner(frame) + 2`. A button answers the family's `buttonCorner` (vanilla and bedrock 0, glass 1,
the others 3), so the vanilla ring around a square button is square. A chosen card's 2 px ring is
`panelEdge`, which follows the panel's own outline: vanilla's stepped corners, the rounded corner elsewhere.
Dialog rings its buttons through its inner panel, so it gets the same ring. The ring goes round `focusBox(frame)`,
which a button and the Dropdown grow by the family's `bleed`: bedrock's ring closes under the dark band, not across it.

**R6. A scroll bar sits inside the content column.** Its drawn track's outer edge lies on the right edge of the
content above it (`ScrollBar.x(frame, right)`, with each family's `scrollerGap`: 2 px of blank in the default
3 px track, 0 for vanilla's 6 px sprite), and it spans the same top and bottom insets as the rows. Cases checked:
CommandPalette, ComboBox and ContextMenu in vanilla and bubbly, where the track now ends under the field's right
edge. In a well or a field (List, Table, Tree, TextArea) the column ends one pixel of face inside the 1 px border
(`ScrollBar.wellX`, rows give up `wellRoom`); a Kanban column ends at its card margin; Accordion, Steps and the Donut
legend have no frame, so the track ends on the box's own right edge.

**R7. A variant draws like its 1.0 sibling.** RangeSlider's game look is the slider's bar, handles and label,
with no fill of its own; the label takes the stretch between the handles, else the wider one outside them, and
hides when neither holds it, so it never sits under a handle. RadioGroup draws like the Checkbox: in vanilla the round mark takes checkbox.png's
outline (white when lit), dark well and white dot. NumberField already matches TextField: the family's field, its
text and hint inks, the library field doing the editing.

**R8. Smooth mode is the pixel shape at screen resolution, anti-aliased.** `smooth(true)` samples the same
centre and radii as the pixel shape at one cell per screen pixel and gives each edge cell its coverage as alpha (6
levels, then 3), from the cell centre's distance to the edge; arc ends and zone cuts are radial lines with the same
coverage. One ladder serves every smooth shape (`Smooth.ladder`); a budget of rectangles per unit of GUI scale
decides when it takes fewer levels, then coarser cells. Cases checked: Gauge (bench 86 x 70, the stair-stepped
inner edge, the jagged fill end and the blob at the red cap gone), Chart lines and areas, Sparkline. Scene3D and
ModelPreview already anti-alias their edges; Donut and Wheel have no smooth mode.

**R9. Arrows are odd and round shapes may be.** An arrow, caret or pointer is `Shapes.arrow`: row `i` from the tip
is `2i + 1` px wide, so the tip is one centred pixel and both sides step alike. A disc of `side` cells is centred
at `side / 2`, on a pixel corner when even, on a pixel centre when odd (`PixelArt.span(r, k, odd)`); the parity is
chosen per shape for the cleanest result. Cases checked: the Wheel is odd (diameter `2 * radius + 1`), so its
pointer's tip, the line under it and the hub share one pixel column, and the wheel mirrors exactly about it;
popover arrow 1-3-5, Accordion 7-5-3-1, SplitPane 1-3-5, the 9 px CARET, NEXT and PREV glyphs (ComboBox,
Dropdown, Tree, Breadcrumb, ContextMenu, Carousel) and TreeTable's arrows already met the rule. Gauge and Donut
stay even, their text centred on the disc box.

**Chart baseline.** The zero line stays 1 px, as the grid lines and the series lines are: it stands out by a
stronger ink (alpha 0x70 against 0x24), and a 2 px line would sit half under the bars it carries.

**Tooltips that would hide what they name.** A widget can return `tooltipY` to lay its tip under itself;
AvatarGroup does, so a name never covers the heads beside it.
