# Press styles

```java
add(new TabsComponent(at(new Box("tabs", 0, 0, 200, 18)), List.of("Pets", "Food"), 0, i -> page = i)
        .style(PressStyle.SEPARATE));
```

`TabsComponent`, `ToggleGroupComponent` and `SideRailComponent` take `style(PressStyle)`.

| Style | Look |
|---|---|
| `OVERLAP` (default) | segments joined; the selected one sinks by the press travel, loses its lip and sits behind its neighbours |
| `RAISED_SELECTED` | every segment pushed flat; the selected one stands on its lip |
| `SEPARATE` | plain buttons apart; the selected one wears the family's selected face |

Every style draws with the family's own button, so it follows the player's theme.
