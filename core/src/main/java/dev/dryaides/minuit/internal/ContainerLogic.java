package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.ItemDropTarget;
import dev.dryaides.minuit.ItemSource;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.KeyPress;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.PlayerInventoryComponent;
import dev.dryaides.minuit.ScreenAreas;
import dev.dryaides.minuit.Shapes;
import dev.dryaides.minuit.SlotComponent;
import dev.dryaides.minuit.SlotGridComponent;
import dev.dryaides.minuit.SlotLayout;
import dev.dryaides.minuit.SlotPlacement;
import dev.dryaides.minuit.internal.port.KeyStroke;
import dev.dryaides.minuit.internal.port.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;

/**
 * What {@code client.MinuitContainerScreen} does with its panel. The game keeps every slot rule
 * through the menu; this decides which clicks and keys the panel takes. A false return lets the
 * screen hand the event to the game. Not API.
 */
public final class ContainerLogic {

    /** The menu as the logic reads it. */
    public interface MenuView {
        /** The stack of the menu slot under the mouse, or null. */
        ItemView hoveredSlotItem();

        /** The vanilla container box, screen coordinates. */
        Box container();

        /**
         * The menu slot under the mouse that the screen shows, with its item corner where the screen draws
         * it (offset included), screen coordinates; null over none.
         */
        default SlotLayout.Cell hoveredCell() {
            return null;
        }
    }

    /** What a key does on a kit screen opened over a menu, after the panel passed on it. */
    public enum OverKey { NONE, TO_VANILLA, CLOSE }

    /**
     * The inventory key shows the vanilla screen the kit replaced; a movement or jump key closes the
     * menu, as walking away does, when the screen asks for it.
     */
    public static OverKey overKey(boolean inventoryKey, boolean movementKey, boolean hasVanilla, boolean closesOnMovement) {
        if (inventoryKey && hasVanilla) {
            return OverKey.TO_VANILLA;
        }
        return movementKey && closesOnMovement ? OverKey.CLOSE : OverKey.NONE;
    }

    /** The one wheel axis the panel reads: some systems turn a shift-held wheel into a horizontal scroll. */
    public static double wheel(double scrollX, double scrollY, boolean shift) {
        return scrollY == 0 && shift ? scrollX : scrollY;
    }

    private final HostCore host;
    private final MenuView menu;
    private IntPredicate shown = i -> true;
    /** The mouse at the last frame, screen coordinates. */
    private double mouseX = -1;
    private double mouseY = -1;
    /** The menu slots at the menu's place, screen coordinates, kept from {@link #init}. */
    private List<SlotLayout.Cell> slots = List.of();
    private SlotPlacement placement = SlotPlacement.NONE;
    /** One bit per game button whose press the panel took. */
    private int panelPresses;
    /** Screen classes already given their dev notes, so a screen opened again stays quiet. */
    private static final Set<String> NOTED = ConcurrentHashMap.newKeySet();

    public ContainerLogic(HostCore host, MenuView menu) {
        this.host = host;
        this.menu = menu;
    }

    /**
     * The screen's init: keeps the menu slots ({@code slots}, item corners at the menu's place, screen
     * coordinates), places them, forgets presses from before, and in a development environment writes the
     * notes for the modder once per screen class. {@code shown} tells which slots the screen shows;
     * {@code title} is the vanilla title's box, null for none.
     */
    public void init(PanelComponent current, List<SlotLayout.Cell> slots, IntPredicate shown, String screen, Box title) {
        this.slots = List.copyOf(slots);
        this.shown = shown;
        this.placement = SlotPlacement.of(current, this.slots);
        this.panelPresses = 0;
        if (current != null && Platform.development() && NOTED.add(screen)) {
            for (String note : notes(current, this.placement, this.slots, shown, screen, title)) {
                Platform.note(note);
            }
        }
    }

    /**
     * The first visible widget the title overlaps that a player uses: one with keyboard stops, or a slot part,
     * found through the wells of the slots it places since {@code menuWells} is not visible from here.
     */
    private static BaseComponent underTitle(PanelComponent current, SlotPlacement placement, List<SlotLayout.Cell> slots, Box title) {
        for (BaseComponent widget : current.widgets()) {
            if (!widget.visible || !widget.box().intersects(title)) {
                continue;
            }
            if (!widget.stops().isEmpty()) {
                return widget;
            }
            for (SlotLayout.Cell slot : slots) {
                Box well = new Box("well", slot.x() - 1, slot.y() - 1, SlotLayout.PITCH, SlotLayout.PITCH);
                if (placement.placed(slot.index()) && well.intersects(title) && widget.box().contains(well)) {
                    return widget;
                }
            }
        }
        return null;
    }

    static List<String> notes(PanelComponent current, SlotPlacement placement, List<SlotLayout.Cell> slots,
                              IntPredicate shown, String screen, Box title) {
        List<String> out = new ArrayList<>();
        List<Integer> loose = new ArrayList<>();
        for (SlotLayout.Cell slot : slots) {
            if (shown.test(slot.index()) && !placement.placed(slot.index())) {
                loose.add(slot.index());
            }
        }
        loose.sort(null);
        if (!loose.isEmpty()) {
            out.add("minuit: " + screen + " shows menu slots no panel component places: " + ranges(loose)
                    + ". Return placedByPanel(slot) from showsSlot to hide them.");
        }
        BaseComponent under = title == null ? null : underTitle(current, placement, slots, title);
        if (under != null) {
            out.add("minuit: " + screen + " draws its title under " + under.box().name()
                    + "; move the widget, the title (titleLabelX/Y) or turn labels off (drawsLabels).");
        }
        return out;
    }

    /** Runs of indices as the note prints them: 0-8, 45. */
    static String ranges(List<Integer> sorted) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            int start = sorted.get(i);
            while (i + 1 < sorted.size() && sorted.get(i + 1) == sorted.get(i) + 1) {
                i++;
            }
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(start == sorted.get(i) ? String.valueOf(start) : start + "-" + sorted.get(i));
        }
        return out.toString();
    }

    /** The placement of the last frame; {@link SlotPlacement#NONE} before {@link #init} or without a panel. */
    public SlotPlacement placement() {
        return this.placement;
    }

    /**
     * Where the first visible player inventory of the panel draws its label, screen coordinates, as its own
     * {@code label} does; null when the panel has none.
     */
    public Box inventoryLabel(PanelComponent current) {
        if (current == null) {
            return null;
        }
        for (BaseComponent widget : current.widgets()) {
            if (widget.visible && widget instanceof PlayerInventoryComponent) {
                return new Box("inventoryLabel", widget.x + 1, widget.y + 1 - SlotLayout.LABEL_GAP, 0, 0);
            }
        }
        return null;
    }

    /** Vanilla's answer, unless the point lies on the panel: a click on what the player sees never throws the stack. */
    public boolean clickedOutside(PanelComponent current, double x, double y, boolean vanilla) {
        return vanilla && !SlotPlacement.of(current, List.of()).covers(x, y);
    }

    /** The wells, under the items the game draws a layer up, and the theme's hover on a shown slot no component places; places the kept slots for this frame first. */
    public void drawBase(Draw d, PanelComponent current, int mx, int my) {
        this.placement = SlotPlacement.of(current, this.slots);
        if (current == null) {
            return;
        }
        this.host.drawTab(d, current.theme(), current.bounds(), mx, my);
        current.drawBase(d, mx, my, this.host.frame());
        SlotLayout.Cell hovered = this.menu.hoveredCell();
        if (hovered != null && this.shown.test(hovered.index()) && !this.placement.placed(hovered.index())) {
            d.fill(hovered.x(), hovered.y(), hovered.x() + 16, hovered.y() + 16, Shapes.SLOT_HOVER_BACK);
        }
    }

    /** After the items, a layer up, so an open dropdown covers them; the carried item still comes after it. */
    public void drawOverlay(Draw d, PanelComponent current, int mx, int my) {
        if (current != null && current.overlay() != null) {
            d.nextLayer();
            current.drawOverlay(d, mx, my, 1F);
        }
    }

    /** The tooltips, last. */
    public void drawTop(Draw d, PanelComponent current, int mx, int my) {
        this.mouseX = mx;
        this.mouseY = my;
        if (current == null) {
            return;
        }
        current.drawTooltip(d, mx, my);
        this.host.drawTabTip(d, current.bounds(), mx, my);
    }

    /** Vanilla's labels in the theme's text colour; vanilla hard-codes a dark grey. */
    public int labelColor(PanelComponent current) {
        return current == null ? 0xFF404040 : current.theme().palette().text();
    }

    /** A display slot or item icon of the panel, else the menu slot's stack; null over nothing. */
    public ItemView itemUnderMouse(PanelComponent current) {
        if (current != null) {
            ItemView shown = ItemSource.under(current, this.mouseX, this.mouseY);
            if (shown != null) {
                return shown;
            }
        }
        return this.menu.hoveredSlotItem();
    }

    public List<Box> extraAreas(PanelComponent current) {
        return current == null ? List.of() : ScreenAreas.outside(current, this.menu.container());
    }

    public List<ItemDropTarget.Drop> dropTargets(PanelComponent current) {
        return current == null ? List.of() : ItemDropTarget.all(current);
    }

    /** True when no widget is under the mouse, or the topmost one is a slot part over menu slots: the game takes that click. */
    static boolean onMenuSlots(PanelComponent current, double mx, double my) {
        List<BaseComponent> widgets = current.widgets();
        for (int i = widgets.size() - 1; i >= 0; i--) {
            BaseComponent widget = widgets.get(i);
            if (widget.isOver(mx, my)) {
                if (widget instanceof SlotGridComponent) {
                    return ((SlotGridComponent) widget).holdsMenu();
                }
                if (widget instanceof PlayerInventoryComponent) {
                    return ((PlayerInventoryComponent) widget).holdsMenu();
                }
                return widget instanceof SlotComponent && ((SlotComponent) widget).holdsMenu();
            }
        }
        return true;
    }

    public boolean mouseClicked(PanelComponent current, double mx, double my, int gameButton) {
        if (current == null) {
            return false;
        }
        boolean took = takesClick(current, mx, my, gameButton);
        if (took && gameButton >= 0 && gameButton < 32) {
            this.panelPresses |= 1 << gameButton;
        }
        return took;
    }

    private boolean takesClick(PanelComponent current, double mx, double my, int gameButton) {
        if (current.capturing() && current.rawKey(KeyPress.mouse(gameButton))) {
            return true;
        }
        int button = KeyRouter.button(gameButton, Platform.keyCodes());
        boolean toPanel = button >= 0 && (current.overlay() != null || !onMenuSlots(current, mx, my));
        if (toPanel && current.mouseClicked(mx, my, button)) {
            return true;
        }
        return button >= 0 && this.host.clickTab(current.theme(), current.bounds(), mx, my);
    }

    /**
     * Gives the release to the panel; true when the panel took the press before it, and the screen then keeps
     * the release from the game, which would otherwise act on a press it never saw.
     */
    public boolean mouseReleased(PanelComponent current, double mx, double my, int gameButton) {
        boolean pressed = gameButton >= 0 && gameButton < 32 && (this.panelPresses & 1 << gameButton) != 0;
        if (pressed) {
            this.panelPresses &= ~(1 << gameButton);
        }
        int button = KeyRouter.button(gameButton, Platform.keyCodes());
        if (current != null && button >= 0) {
            current.mouseReleased(mx, my, button);
        }
        return pressed;
    }

    public boolean mouseDragged(PanelComponent current, double mx, double my, int gameButton, double dx, double dy) {
        int button = KeyRouter.button(gameButton, Platform.keyCodes());
        return current != null && button >= 0 && current.mouseDragged(mx, my, button, dx, dy);
    }

    public boolean mouseScrolled(PanelComponent current, double mx, double my, double scrollY) {
        return current != null && current.mouseScrolled(mx, my, scrollY);
    }

    /**
     * The panel first; while one of its fields holds the keyboard every key stays with it, so typing
     * neither closes the screen on the inventory key nor swaps hotbar slots.
     */
    public boolean keyPressed(PanelComponent current, KeyStroke stroke) {
        return current != null && (this.host.keyPressed(current, stroke) || current.focus() != null);
    }

    /** A controller's back button reaches the screen's {@code onClose} without a key: it closes an open overlay first. */
    public boolean closeOverlay(PanelComponent current) {
        return current != null && current.closeOverlay();
    }

    public boolean charTyped(PanelComponent current, int codepoint) {
        return current != null && current.charTyped(codepoint);
    }

    public void tick(PanelComponent current) {
        if (current != null) {
            current.tickAll();
        }
    }

    /**
     * Back from the theme page the game builds the screen again on the same menu, so nothing closes:
     * false then, and the screen skips its own {@code removed}.
     */
    public boolean removed(PanelComponent current) {
        if (this.host.away()) {
            return false;
        }
        if (current != null) {
            current.closing();
        }
        return true;
    }
}
