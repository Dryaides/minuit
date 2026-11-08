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

## The player's choice

`config/minuit-client.json`:

```json
{"global": {"family": "slate", "chroma": "teal", "variant": "auto"},
 "mods": {"mymod": {"family": "leather", "chroma": "paper", "variant": "light", "accent": "midnight"}}}
```

A mod with no entry wears `global`; with no `global`, the theme it registered. `auto` takes the dark
variant when the chroma has one. `accent` replaces only the accent colours; the ids are `blue`,
`green`, `orange`, `rose`, `violet` and `midnight`. The player edits all this on the theme page.

## A mod's own look

```java
Themes.register(Themes.defaultTheme().as("mymod"));                 // follow the kit's default (Vanilla, light)
Themes.register(Themes.LEATHER.withIcon(Icon.CLOSE, myArt).as("mymod")); // a preset with your icon
Theme screenTheme = Themes.byId("mymod");                           // what your screens open with
new ThemeScreen(parentScreen, "mymod");                             // the theme page, for your mod
```

Register at client init. The registered theme is the default until the player picks one. A family
that only your theme page offers is a `family.json` in your own namespace with `"private": true`.
