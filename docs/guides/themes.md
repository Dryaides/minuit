# Your theme

```java
// client init
Themes.register(Themes.defaultTheme().as("mymod"), "My Mod");
```

Your screens wear the registered theme until the player picks another one on the theme page. The page shows
it as the "Default" card, under the name you pass (without one, the id with a capital).

## Your own palette

```java
Palette mine = new Palette(/* every role, in the order of the roles table in THEMES.md */);
Themes.register(new Theme("mymod", mine, FrameStyles.SLATE), "My Mod");
```

A theme is a palette (colours by role) and a `FrameStyle` (the shapes). The theme page offers your palette
as a chroma of its own, in the family that draws with your frame style (`Themes.ownFamily("mymod")`), and
minuit derives its glass mode. A dark `surface` makes it a dark palette.

You can ship the palette as a file instead of code: a chroma in your jar at
`assets/mymod/minuit/chromas/mine.json`, and a family with `"private": true` that recommends it. See
[THEMES.md](../../THEMES.md#a-chroma).

## Rules

- Read the theme with `Themes.byId("mymod")` each time you build a screen.
- `Themes.LEATHER.withIcon(Icon.CLOSE, myArt).as("mymod")` swaps one icon of a preset.
- `new ThemeScreen(parentScreen, "mymod")` opens the theme page for your mod.
- A second `register` under the same id replaces the first.
