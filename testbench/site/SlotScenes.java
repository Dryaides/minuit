package site;

import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.HotbarComponent;
import dev.dryaides.minuit.SpriteIcon;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.Palette;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.PlayerInventoryComponent;
import dev.dryaides.minuit.SlotComponent;
import dev.dryaides.minuit.SlotGridComponent;
import dev.dryaides.minuit.SlotLayout;
import dev.dryaides.minuit.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * The inventory parts in display mode: a 2 by 9 grid, the player inventory with its hotbar and label,
 * and single slots (a storage count drawn short, an empty armour ghost). Same 300 by 180 frame as
 * {@link SiteScenes.Scene}; the inventory label sits over its box, so the scene lists it as a text box.
 */
public final class SlotScenes {

    /** Where the mouse rests in {@link #slotsScene}: over grid cell 4. */
    public static final int MX = 12 + 4 * SlotLayout.PITCH + 9;
    public static final int MY = 26 + 9;

    private SlotScenes() {
    }

    public static final class SlotScene extends PanelComponent {
        private final String title;
        private final List<Box> labels = new ArrayList<>();

        SlotScene(Theme theme, String title) {
            super(theme, SiteScenes.WORDS, (SiteScenes.W - 300) / 2, (SiteScenes.H - 180) / 2);
            this.title = title;
        }

        @Override
        public int width() {
            return 300;
        }

        @Override
        public int height() {
            return 180;
        }

        Box place(Box relative) {
            return at(relative);
        }

        <T extends dev.dryaides.minuit.BaseComponent> T put(T widget) {
            return add(widget);
        }

        @Override
        public List<Box> textBoxes() {
            List<Box> boxes = new ArrayList<>(super.textBoxes());
            boxes.add(at(new Box("title", 12, 10, 276, 10)));
            boxes.addAll(this.labels);
            return boxes;
        }

        @Override
        protected void drawBackground(Draw d, int mx, int my) {
            Palette p = this.theme.palette();
            this.theme.frame().panel(d, p, this.left, this.top, 300, 180);
            d.textIn(this.title, at(new Box("title", 12, 10, 276, 10)), p.text());
        }
    }

    /** No cell marked, as in the game; counts 1, 16 and 64; mouse over grid cell 4 ({@link #MX}, {@link #MY}). */
    public static SlotScene slotsScene(Theme theme) {
        SlotScene s = new SlotScene(theme, "Slots");
        Box chest = SlotLayout.grid("chest", 13, 27, 2, 9);
        s.put(new SlotGridComponent(s.place(chest), 2, 9)
                .items(List.of(ItemView.of("minecraft:diamond"), ItemView.of("minecraft:iron_ingot", 16),
                        ItemView.of("minecraft:cobblestone", 64), ItemView.of("minecraft:apple", 3),
                        ItemView.of("minecraft:oak_log", 32)))
                .onClick((cell, button) -> { }));
        int top = SlotLayout.inventoryTop(27, 2);
        Box inv = SlotLayout.playerInventory("inv", 13, top);
        List<ItemView> all = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            all.add(null);
        }
        all.set(0, ItemView.of("minecraft:iron_sword"));
        all.set(1, ItemView.of("minecraft:bread", 12));
        all.set(9, ItemView.of("minecraft:torch", 64));
        all.set(20, ItemView.of("minecraft:redstone", 7));
        PlayerInventoryComponent player = s.put(new PlayerInventoryComponent(s.place(inv)).items(all).label("Inventory"));
        s.labels.add(s.place(new Box("label", 13, top - SlotLayout.LABEL_GAP, 162, 9)));
        s.put(new SlotComponent(s.place(new Box("storage", 200, 26, 18, 18)))
                .item(ItemView.of("minecraft:cobblestone"), 1_234));
        s.put(new SlotComponent(s.place(new Box("bulk", 222, 26, 18, 18)))
                .item(ItemView.of("minecraft:dirt"), 34_000_000));
        SlotComponent ghost = s.put(new SlotComponent(s.place(new Box("helmet", 244, 26, 18, 18))));
        ghost.empty = SpriteIcon.gui("minecraft:container/slot/helmet", 16, 16);
        return s;
    }

    /** The game's HUD hotbar alone (slot 2 held) over a player inventory whose hotbar is the HUD bar too. */
    public static SlotScene hudScene(Theme theme) {
        SlotScene s = new SlotScene(theme, "HUD hotbar");
        Box at = s.place(SlotLayout.hudHotbar("hud", 59, 26));
        HotbarComponent bar = HotbarComponent.hud("hud", at.x(), at.y());
        bar.selected = 2;
        bar.items(List.of(ItemView.of("minecraft:iron_sword"), ItemView.of("minecraft:bread", 12),
                ItemView.of("minecraft:torch", 64), ItemView.of("minecraft:oak_log", 32)));
        s.put(bar);
        Box inv = SlotLayout.playerInventory("inv", 13, 70);
        List<ItemView> all = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            all.add(null);
        }
        all.set(0, ItemView.of("minecraft:iron_sword"));
        all.set(1, ItemView.of("minecraft:bread", 12));
        all.set(2, ItemView.of("minecraft:torch", 64));
        all.set(9, ItemView.of("minecraft:diamond"));
        all.set(20, ItemView.of("minecraft:redstone", 7));
        s.put(new PlayerInventoryComponent(s.place(inv), true).items(all).label("Inventory"));
        s.labels.add(s.place(new Box("label", 13, 70 - SlotLayout.LABEL_GAP, 162, 9)));
        return s;
    }
}
