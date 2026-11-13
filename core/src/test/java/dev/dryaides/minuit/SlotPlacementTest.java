package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.ContainerLogic;
import dev.dryaides.minuit.internal.HostCore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Oracles: vanilla's {@code InventoryMenu} slot corners and its HUD hotbar layout, container at (0, 0). */
class SlotPlacementTest {

    /** Vanilla InventoryMenu item corners, as the game lays them out. */
    static List<SlotLayout.Cell> playerMenu() {
        List<SlotLayout.Cell> out = new ArrayList<>();
        out.add(new SlotLayout.Cell(0, 154, 28));
        out.add(new SlotLayout.Cell(1, 98, 18));
        out.add(new SlotLayout.Cell(2, 116, 18));
        out.add(new SlotLayout.Cell(3, 98, 36));
        out.add(new SlotLayout.Cell(4, 116, 36));
        for (int k = 0; k < 4; k++) {
            out.add(new SlotLayout.Cell(5 + k, 8, 8 + 18 * k));
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                out.add(new SlotLayout.Cell(9 + 9 * r + c, 8 + 18 * c, 84 + 18 * r));
            }
        }
        for (int c = 0; c < 9; c++) {
            out.add(new SlotLayout.Cell(36 + c, 8 + 18 * c, 142));
        }
        out.add(new SlotLayout.Cell(45, 77, 62));
        return out;
    }

    static PanelComponent panel(int width, BaseComponent... parts) {
        return new PanelComponent(Themes.MINIMALIST, null, 0, 0) {
            {
                for (BaseComponent widget : parts) {
                    add(widget);
                }
            }

            @Override
            public int width() {
                return width;
            }

            @Override
            public int height() {
                return 166;
            }

            @Override
            protected void drawBackground(Draw d, int mx, int my) {
            }
        };
    }

    private static final Box INVENTORY = new Box("inv", 7, 83, 162, 76);

    @Test
    void inventoryKitOverThePlayerMenuPlacesOnlyTheInventory() {
        SlotPlacement placement = SlotPlacement.of(panel(176, new PlayerInventoryComponent(INVENTORY)), playerMenu());
        for (int i = 0; i <= 45; i++) {
            assertEquals(i >= 9 && i <= 44, placement.placed(i), "slot " + i);
            assertEquals(0, placement.shiftX(i));
            assertEquals(0, placement.shiftY(i));
        }
    }

    @Test
    void hudHotbarShowsTheMenuHotbarOnTheBar() {
        // Vanilla Gui: a 182 px bar centred on 88, item i at 88 - 90 + 20i + 2 = 20i, level with the menu's 142.
        PanelComponent kit = panel(176, new PlayerInventoryComponent(INVENTORY, true));
        SlotPlacement placement = SlotPlacement.of(kit, playerMenu());
        for (int i = 0; i < 9; i++) {
            assertTrue(placement.placed(36 + i));
            assertEquals(20 * i - (8 + 18 * i), placement.shiftX(36 + i), "hotbar " + i);
            assertEquals(0, placement.shiftY(36 + i));
        }
        for (int i = 9; i <= 35; i++) {
            assertTrue(placement.placed(i));
            assertEquals(0, placement.shiftX(i));
        }

        List<SlotLayout.Cell> hudMenu = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            hudMenu.add(new SlotLayout.Cell(36 + i, 20 * i, 142));
        }
        SlotPlacement built = SlotPlacement.of(kit, hudMenu);
        for (int i = 0; i < 9; i++) {
            assertTrue(built.placed(36 + i));
            assertEquals(0, built.shiftX(36 + i), "hotbar " + i);
            assertEquals(0, built.shiftY(36 + i));
        }
    }

    @Test
    void armourOutlineHasOneOwnerAndUnplacedSlotsAreNeverFound() {
        // The owner's 2.0 rules: the theme draws a placed slot's game outline unless its part sets its own;
        // the pointer finds no slot a part leaves out; a screen without a panel keeps every slot.
        SlotComponent helmet = new SlotComponent(new Box("helmet", 7, 7, 18, 18));
        helmet.empty = SpriteIcon.item("minuit:helmet_outline");
        SlotComponent chest = new SlotComponent(new Box("chest", 7, 25, 18, 18));
        SlotPlacement placement = SlotPlacement.of(panel(176, new PlayerInventoryComponent(INVENTORY), helmet, chest), playerMenu());
        List<String> sprites = new ArrayList<>();
        Draw d = new Draw() {
            @Override protected void rect(int x0, int y0, int x1, int y1, int argb) { }
            @Override protected void string(String text, int x, int y, int argb) { }
            @Override public int width(String text) { return 6 * text.length(); }
            @Override public String cut(String text, int maxWidth) { return text; }
            @Override public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH, float alpha) { }
            @Override protected void pushPose(float x, float y, float scale) { }
            @Override protected void popPose() { }
            @Override public void scissor(int x0, int y0, int x1, int y1) { }
            @Override public void endScissor() { }
            @Override public void tooltip(List<String> lines, int x, int y) { }
            @Override public void sprite(String atlas, String sprite, int x, int y, int w, int h, float alpha) {
                sprites.add(sprite + "@" + x + "," + y);
            }
        };
        placement.drawGhost(d, 5, SpriteIcon.gui("minecraft:container/slot/helmet", 16, 16), 8, 8);
        placement.drawGhost(d, 6, SpriteIcon.gui("minecraft:container/slot/chestplate", 16, 16), 8, 26);
        assertEquals(List.of("minecraft:container/slot/chestplate@8,26"), sprites);

        assertTrue(placement.finds(5));
        assertTrue(placement.finds(20));
        assertFalse(placement.finds(0), "craft result");
        assertFalse(placement.finds(7), "leggings");
        assertFalse(placement.finds(45), "offhand");
        assertTrue(SlotPlacement.NONE.finds(45));
    }

    @Test
    void widgetsOnTopHideSlotsAndPassiveOnesDoNot() {
        PanelComponent[] holder = new PanelComponent[1];
        DropdownComponent dropdown = new DropdownComponent(new Box("sort", 100, 20, 60, 14), "Sort", 100, 60,
                list -> holder[0].overlay = list);
        PanelComponent kit = panel(176, new PlayerInventoryComponent(INVENTORY),
                new ToggleComponent(new Box("auto", 8, 8, 100, 14), "Auto sort", false, on -> { }),
                new ProgressComponent(new Box("meter", 8, 84, 16, 16)), dropdown);
        holder[0] = kit;
        SlotPlacement placement = SlotPlacement.of(kit, playerMenu());

        assertFalse(placement.reachable(12, 12));
        assertTrue(placement.reachable(12, 88));
        assertTrue(placement.reachable(60, 120));

        kit.mouseClicked(110, 25, 0);
        Box list = kit.overlay().box();
        assertFalse(placement.reachable(list.x() + 2, list.y() + 2));
    }

    @Test
    void panelPastTheContainerBoxDoesNotThrow() {
        ContainerLogic logic = new ContainerLogic(new HostCore(null), new ContainerLogic.MenuView() {
            @Override
            public ItemView hoveredSlotItem() {
                return null;
            }

            @Override
            public Box container() {
                return new Box("container", 0, 0, 176, 166);
            }
        });
        PanelComponent wide = panel(236);
        assertFalse(logic.clickedOutside(wide, 200, 50, true));
        assertTrue(logic.clickedOutside(wide, 260, 50, true));
    }
}
