package dev.dryaides.minuit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which menu slot each slot part of a kit panel places, for one frame, and where the pointer can reach a
 * menu slot. {@code MinuitContainerScreen.placement()} gives the screen's current one; a recipe viewer
 * plugin reads it to find a slot where the kit draws it.
 */
@Incubating
public final class SlotPlacement {

    /** Nothing placed, every point reachable, nothing covered: a screen without a panel. */
    public static final SlotPlacement NONE = new SlotPlacement(null, Map.of());

    private final PanelComponent panel;
    /** Placed slot index to its shift and ghost flag, packed as {x, y, ghost}. */
    private final Map<Integer, int[]> shifts;

    private SlotPlacement(PanelComponent panel, Map<Integer, int[]> shifts) {
        this.panel = panel;
        this.shifts = shifts;
    }

    /**
     * Places each of {@code slots} (a menu slot's index and its item corner at the menu's place, screen
     * coordinates, offsets left out) on the first well that holds its 16 px item, walking the panel's
     * visible widgets topmost first and each widget's {@link BaseComponent#menuWells} in order.
     */
    public static SlotPlacement of(PanelComponent panel, List<SlotLayout.Cell> slots) {
        if (panel == null) {
            return NONE;
        }
        List<BaseComponent> widgets = panel.widgets();
        Map<Integer, int[]> shifts = new HashMap<>();
        for (SlotLayout.Cell slot : slots) {
            Box item = new Box("item", slot.x(), slot.y(), 16, 16);
            SlotLayout.Well well = wellFor(widgets, item);
            if (well != null) {
                shifts.put(slot.index(), new int[] {well.shiftX(), well.shiftY(), well.ghost() ? 1 : 0});
            }
        }
        return new SlotPlacement(panel, shifts);
    }

    private static SlotLayout.Well wellFor(List<BaseComponent> widgets, Box item) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            BaseComponent widget = widgets.get(i);
            if (!widget.visible) {
                continue;
            }
            for (SlotLayout.Well well : widget.menuWells()) {
                if (well.box().contains(item)) {
                    return well;
                }
            }
        }
        return null;
    }

    /** True when a slot part of the panel draws this menu slot's well. */
    public boolean placed(int slotIndex) {
        return this.shifts.containsKey(slotIndex);
    }

    /**
     * True when the pointer finds this menu slot and the screen shows it by default: a slot part places it,
     * or the screen has no panel. A slot no part places is never under the pointer: no hover, tooltip,
     * click, drag or key reaches it, whatever {@code showsSlot} says.
     */
    public boolean finds(int slotIndex) {
        return this.panel == null || placed(slotIndex);
    }

    /** True when the part that places this slot draws its own empty outline ({@code SlotComponent.empty}). */
    public boolean drawsGhost(int slotIndex) {
        int[] shift = this.shifts.get(slotIndex);
        return shift != null && shift[2] == 1;
    }

    /**
     * Draws a menu slot's empty outline, {@code icon} (the game's armour or offhand sprite), as the theme draws
     * a slot part's {@code empty}, its 16 px item area at {@code x, y}. A kit screen calls it from the slot draw
     * in place of the game's grey one, which it never draws; nothing when the slot's part draws its own, or
     * on a screen without a panel ({@link #NONE}), where the screen leaves the game's.
     */
    public void drawGhost(Draw d, int slotIndex, IconArt icon, int x, int y) {
        if (this.panel != null && !drawsGhost(slotIndex)) {
            SlotComponent.ghost(d, this.panel.theme(), icon, x, y);
        }
    }

    /** How far the panel shows this slot's item from the menu's place, across: non-zero on a HUD hotbar and an expandable one. */
    public int shiftX(int slotIndex) {
        int[] shift = this.shifts.get(slotIndex);
        return shift == null ? 0 : shift[0];
    }

    /** How far the panel shows this slot's item from the menu's place, down: non-zero on a HUD hotbar and an expandable one. */
    public int shiftY(int slotIndex) {
        int[] shift = this.shifts.get(slotIndex);
        return shift == null ? 0 : shift[1];
    }

    /**
     * False where the panel hides the menu slots under the point: inside the open overlay, inside a visible
     * window, or on a part of a widget the keyboard stops on (a button, a toggle, a field). True on the
     * panel's slot parts and under passive widgets (a picture, a meter, a frame), which a modder may lay
     * over wells on purpose. Read live from the panel.
     */
    public boolean reachable(double x, double y) {
        if (this.panel == null) {
            return true;
        }
        BaseComponent open = this.panel.overlay();
        if (open != null && open.isOver(x, y)) {
            return false;
        }
        List<BaseComponent> widgets = this.panel.widgets();
        for (int i = widgets.size() - 1; i >= 0; i--) {
            BaseComponent widget = widgets.get(i);
            if (!widget.isOver(x, y)) {
                continue;
            }
            if (!widget.menuWells().isEmpty()) {
                return true;
            }
            if (widget instanceof WindowComponent) {
                return false;
            }
            for (BaseComponent stop : widget.stops()) {
                if (stop.isOver(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** True when the point lies on the panel, one of its windows, its open overlay or any visible widget. */
    public boolean covers(double x, double y) {
        if (this.panel == null) {
            return false;
        }
        for (Box area : ScreenAreas.outside(this.panel, null)) {
            if (area.contains(x, y)) {
                return true;
            }
        }
        for (BaseComponent widget : this.panel.widgets()) {
            if (widget.visible && widget.box().contains(x, y)) {
                return true;
            }
        }
        return false;
    }
}
