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
import dev.dryaides.minuit.SlotComponent;
import dev.dryaides.minuit.SlotGridComponent;
import dev.dryaides.minuit.internal.port.KeyStroke;
import dev.dryaides.minuit.internal.port.Platform;

import java.util.List;

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
    /** The mouse at the last frame, screen coordinates. */
    private double mouseX = -1;
    private double mouseY = -1;

    public ContainerLogic(HostCore host, MenuView menu) {
        this.host = host;
        this.menu = menu;
    }

    /** The wells, under the items the game draws a layer up. */
    public void drawBase(Draw d, PanelComponent current, int mx, int my) {
        if (current == null) {
            return;
        }
        this.host.drawTab(d, current.theme(), current.bounds(), mx, my);
        current.drawBase(d, mx, my, this.host.frame());
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

    public void mouseReleased(PanelComponent current, double mx, double my, int gameButton) {
        int button = KeyRouter.button(gameButton, Platform.keyCodes());
        if (current != null && button >= 0) {
            current.mouseReleased(mx, my, button);
        }
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
