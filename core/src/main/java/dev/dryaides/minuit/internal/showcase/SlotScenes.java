package dev.dryaides.minuit.internal.showcase;

import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.EquipmentComponent;
import dev.dryaides.minuit.HotbarComponent;
import dev.dryaides.minuit.SpriteIcon;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.Palette;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.PlayerInventoryComponent;
import dev.dryaides.minuit.SlotComponent;
import dev.dryaides.minuit.SlotGridComponent;
import dev.dryaides.minuit.SlotLayout;
import dev.dryaides.minuit.StackHand;
import dev.dryaides.minuit.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * The inventory parts: a 2 by 9 grid and the player inventory with its hotbar and label, played with the mouse,
 * and single slots (a storage count drawn short, an empty armour ghost). Same 300 by 180 frame as
 * {@link SiteScenes.Scene}; the inventory label sits over its box, so the scene lists it as a text box.
 */
public final class SlotScenes {

    /** The mouse over chest cell 4 of {@link #slotsScene}, scene-local: the oak log. */
    public static final int MX = 13 + 4 * SlotLayout.PITCH + 8;
    public static final int MY = 27 + 8;

    private SlotScenes() {
    }

    public static final class SlotScene extends PanelComponent {
        private final String title;
        private final List<Box> labels = new ArrayList<>();
        /** Set when the scene's slots are played; its carried stack is drawn at the mouse. */
        StackHand hand;
        /** The last mouse position over the canvas: the carried stack stays there while the pointer is away. */
        private int mx = -1000;
        private int my = -1000;

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

        @Override
        protected void drawForeground(Draw d, int mx, int my) {
            if (mx > -500 && my > -500) {
                this.mx = mx;
                this.my = my;
            }
            if (this.hand != null) {
                this.hand.drawCarried(d, this.mx, this.my);
            }
        }

        /** No item tooltip while a stack is carried, as in the game. */
        @Override
        public List<String> tooltipAt(double mx, double my) {
            return this.hand != null && this.hand.carried() != null ? null : super.tooltipAt(mx, my);
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            boolean used = super.mouseDragged(mx, my, button, dx, dy);
            if (this.hand != null) {
                this.hand.mouseDragged(mx, my);
                return true;
            }
            return used;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            boolean used = super.mouseReleased(mx, my, button);
            if (this.hand != null) {
                this.hand.mouseReleased(mx, my, button);
            }
            return used;
        }

        /**
         * Keys 1 to 9 and Q over a slot, at the last mouse position (the keys carry none). The page passes
         * characters, not key codes, so the character stands for the default binding's GLFW code.
         */
        @Override
        public boolean charTyped(int codepoint) {
            if (super.charTyped(codepoint)) {
                return true;
            }
            int code = codepoint >= '1' && codepoint <= '9' ? codepoint : codepoint == 'q' || codepoint == 'Q' ? 81 : -1;
            return code >= 0 && this.hand != null && this.hand.keyPressed(this.mx, this.my, code);
        }
    }

    /**
     * No cell marked, as in the game; counts 1, 16 and 64. The chest and the inventory are played as in the
     * game ({@link StackHand}, wired as vanilla's chest menu): slots 0 to 17 the chest, 18 to 53 the inventory
     * hotbar first; swords stack to 1, everything else to 64. Q throws into no world: the item is gone.
     */
    public static SlotScene slotsScene(Theme theme) {
        SlotScene s = new SlotScene(theme, "Slots");
        StackHand hand = new StackHand(54, id -> id.endsWith("_sword") ? 1 : 64);
        hand.set(0, new StackHand.Stack("minecraft:diamond", 1))
                .set(1, new StackHand.Stack("minecraft:iron_ingot", 16))
                .set(2, new StackHand.Stack("minecraft:cobblestone", 64))
                .set(3, new StackHand.Stack("mekanism:alloy_infused", 3))
                .set(4, new StackHand.Stack("minecraft:oak_log", 32))
                .set(18, new StackHand.Stack("minecraft:iron_sword", 1))
                .set(19, new StackHand.Stack("minecraft:bread", 12))
                .set(27, new StackHand.Stack("minecraft:torch", 64))
                .set(38, new StackHand.Stack("mekanism:ingot_osmium", 7));
        Box chest = SlotLayout.grid("chest", 13, 27, 2, 9);
        hand.bind(s.put(new SlotGridComponent(s.place(chest), 2, 9)), 0);
        int top = SlotLayout.inventoryTop(27, 2);
        Box inv = SlotLayout.playerInventory("inv", 13, top);
        hand.bind(s.put(new PlayerInventoryComponent(s.place(inv)).label("Inventory")), 18);
        hand.containerOver(0, 18, 18);
        s.hand = hand;
        s.labels.add(s.place(new Box("label", 13, top - SlotLayout.LABEL_GAP, 162, 9)));
        s.put(new SlotComponent(s.place(new Box("storage", 200, 26, 18, 18)))
                .item(ItemView.of("minecraft:cobblestone"), 1_234));
        s.put(new SlotComponent(s.place(new Box("bulk", 222, 26, 18, 18)))
                .item(ItemView.of("minecraft:dirt"), 34_000_000));
        SlotComponent ghost = s.put(new SlotComponent(s.place(new Box("helmet", 244, 26, 18, 18))));
        ghost.empty = SpriteIcon.gui("minecraft:container/slot/helmet", 16, 16);
        return s;
    }

    /**
     * {@link EquipmentComponent} both ways: a column with the 2x2 craft grid (chestplate worn, the rest empty
     * with the game's outlines in the theme's ghost) and a row without it (shield in the offhand).
     */
    public static SlotScene equipmentScene(Theme theme) {
        SlotScene s = new SlotScene(theme, "Equipment");
        Box menu = s.place(new Box("menu", 0, 0, 0, 0));
        Box col = s.place(new Box("col", 13, 27, 0, 0));
        EquipmentComponent column = s.put(new EquipmentComponent("col", col.x(), col.y(), menu.x(), menu.y(),
                EquipmentComponent.Layout.COLUMN, true));
        ghosts(column);
        column.armour[1].item(ItemView.of("minecraft:iron_chestplate"));
        column.craft[0].item(ItemView.of("minecraft:oak_planks", 2));
        column.result.item(ItemView.of("minecraft:stick", 4));
        Box row = s.place(new Box("row", 150, 27, 0, 0));
        EquipmentComponent line = s.put(new EquipmentComponent("row", row.x(), row.y(), menu.x(), menu.y(),
                EquipmentComponent.Layout.ROW, false));
        ghosts(line);
        line.armour[0].item(ItemView.of("minecraft:diamond_helmet"));
        line.offhand.item(ItemView.of("minecraft:shield"));
        Box both = s.place(new Box("rowcraft", 150, 63, 0, 0));
        ghosts(s.put(new EquipmentComponent("rowcraft", both.x(), both.y(), menu.x(), menu.y(),
                EquipmentComponent.Layout.ROW, true)));
        return s;
    }

    private static void ghosts(EquipmentComponent e) {
        e.ghosts(SpriteIcon.gui("minecraft:container/slot/helmet", 16, 16),
                SpriteIcon.gui("minecraft:container/slot/chestplate", 16, 16),
                SpriteIcon.gui("minecraft:container/slot/leggings", 16, 16),
                SpriteIcon.gui("minecraft:container/slot/boots", 16, 16),
                SpriteIcon.gui("minecraft:container/slot/shield", 16, 16));
    }

    /** The game's HUD hotbar (slot 2 held) over a player inventory whose hotbar is the same nine items on the HUD bar. */
    public static SlotScene hudScene(Theme theme) {
        SlotScene s = new SlotScene(theme, "HUD hotbar");
        Box at = s.place(SlotLayout.hudHotbar("hud", 59, 26));
        HotbarComponent bar = HotbarComponent.hud("hud", at.x(), at.y());
        bar.selected = 2;
        s.put(bar);
        Box inv = SlotLayout.playerInventory("inv", 13, 70);
        List<ItemView> all = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            all.add(null);
        }
        all.set(0, ItemView.of("minecraft:iron_sword"));
        all.set(1, ItemView.of("minecraft:bread", 12));
        all.set(2, ItemView.of("minecraft:torch", 64));
        all.set(3, ItemView.of("minecraft:oak_log", 32));
        all.set(9, ItemView.of("minecraft:diamond"));
        all.set(20, ItemView.of("mekanism:ingot_steel", 7));
        bar.items(all.subList(0, 9));
        s.put(new PlayerInventoryComponent(s.place(inv), true).items(all).label("Inventory"));
        s.labels.add(s.place(new Box("label", 13, 70 - SlotLayout.LABEL_GAP, 162, 9)));
        return s;
    }
}
