# A container screen

```java
public class CrateScreen extends MinuitContainerScreen<CrateMenu> {
    public CrateScreen(CrateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    protected PanelComponent build(int leftPos, int topPos) {
        return new CratePanel(Themes.byId("mymod"), I18n::get, leftPos, topPos); // Words is one method: key to text
    }
}

public class CratePanel extends PanelComponent {
    public CratePanel(Theme theme, Words words, int left, int top) {
        super(theme, words, left, top);
        add(new SlotGridComponent(at(SlotLayout.grid("crate", 8, 18, 3, 9)), 3, 9));
        add(new PlayerInventoryComponent(at(SlotLayout.playerInventory("inv", 8, 84))));
    }

    @Override public int width() { return 176; }
    @Override public int height() { return 166; }
}
```

Register the screen for your menu type as your loader asks. To replace a vanilla screen instead, call
`MinuitMenuScreens.over((menu, vanilla) -> menu instanceof MerchantMenu m ? new TradeScreen(m, vanilla) : null)`
from the client entry point; null keeps the vanilla screen.

- Use the same `SlotLayout` boxes in the menu (both sides) and in the screen, so slots and wells line up.
- The game keeps every click. Slot components draw the wells under the game's items.
- `showsSlot(Slot)` hides a menu slot: no item, hover, click or drag. The menu's own rules (a shift-click)
  still reach it, so make the menu refuse moves that must not happen.
- A slot no component places stays hidden (`placedByPanel`). Place armour and offhand slots with
  `EquipmentComponent` when you keep them.
- `containerLeft()` and `containerTop()` move the container; `build(leftPos, topPos)` gets that corner.
- `drawsBackground()`, `drawsLabels()` and `drawsCarried()` returning false hide the dim, the titles and
  the carried stack.
- For JEI, REI and EMI: `itemUnderMouse()`, `extraAreas()` and `dropTargets()`.

Slot placement and the expandable hotbar are in [API.md](../../API.md#minecraft-parts).
