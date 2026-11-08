package dev.dryaides.minuit.internal;

import java.util.EnumMap;
import java.util.Map;

/**
 * Where each {@link GuiPart} lives in a game generation's textures. The variant picks its table and
 * draws what {@link #resolve} returns: a {@link Sprite} through the GUI atlas (the sprite's .mcmeta does
 * the nine-slice), a {@link Region} through {@link NineSlice}. Not API.
 */
public final class PartTable {

    /** How a part is drawn: a GUI atlas sprite, or a region of a texture cut in nine. */
    public sealed interface Art permits Sprite, Region, Framed {
    }

    /** A sprite of the GUI atlas, {@code namespace:path} under textures/gui/sprites. */
    public record Sprite(String id) implements Art {
    }

    /**
     * A {@code w} by {@code h} region at ({@code u}, {@code v}) of a {@code texW} by {@code texH} texture, with
     * the borders {@link NineSlice} keeps on each side, as 1.20.1's {@code blitNineSliced} takes them.
     */
    public record Region(String texture, int u, int v, int w, int h, int texW, int texH,
                         int left, int top, int right, int bottom) implements Art {

        public Region(String texture, int u, int v, int w, int h, int texW, int texH, int border) {
            this(texture, u, v, w, h, texW, texH, border, border, border, border);
        }

        public Region(String texture, int u, int v, int w, int h, int texW, int texH, int borderX, int borderY) {
            this(texture, u, v, w, h, texW, texH, borderX, borderY, borderX, borderY);
        }
    }

    /**
     * A {@code frame} whose corners and edges {@link NineSlice} keeps, its centre tiled from {@code fill}, a
     * plain region of the same texture: the panel of a container texture, whose own middle holds slots.
     */
    public record Framed(Region frame, Region fill) implements Art {
    }

    /** The GUI atlas of 26.1.2 and 26.2: every id is a sprite of both client jars. */
    public static final PartTable ATLAS = new PartTable(atlas());

    /**
     * The textures 1.20.1 binds for its widgets, at the rectangles its own widgets read (checked in the 1.20.1
     * client jar). Tooltips, text fields and list scrollers have no row: 1.20.1 draws them with fills.
     */
    public static final PartTable WIDGETS = new PartTable(widgets());

    /**
     * The textures 1.19.2 and 1.18.2 bind: as {@link #WIDGETS}, but their client jars have no slider.png and no
     * tab_button.png, so the slider is drawn with the button art, as their slider does, and tabs have no row.
     */
    public static final PartTable LEGACY_WIDGETS = new PartTable(legacyWidgets());

    private final Map<GuiPart, Art> rows;

    private PartTable(Map<GuiPart, Art> rows) {
        this.rows = rows;
    }

    /** Null when this generation has no art for the part: the caller draws its pixel copy. */
    public Art resolve(GuiPart part) {
        return this.rows.get(part);
    }

    private static Map<GuiPart, Art> widgets() {
        String widgets = "minecraft:textures/gui/widgets.png";
        String slider = "minecraft:textures/gui/slider.png";
        String checkbox = "minecraft:textures/gui/checkbox.png";
        String tab = "minecraft:textures/gui/tab_button.png";
        Map<GuiPart, Art> m = new EnumMap<>(GuiPart.class);
        // AbstractButton: v = 46 + 20 * (0 inactive, 1 idle, 2 hovered); the game slices it 20 wide, 4 high
        m.put(GuiPart.BUTTON_DISABLED, new Region(widgets, 0, 46, 200, 20, 256, 256, 20, 4));
        m.put(GuiPart.BUTTON, new Region(widgets, 0, 66, 200, 20, 256, 256, 20, 4));
        m.put(GuiPart.BUTTON_HIGHLIGHTED, new Region(widgets, 0, 86, 200, 20, 256, 256, 20, 4));
        // one slot of the player inventory background, its 1 px frame included
        m.put(GuiPart.SLOT, new Region("minecraft:textures/gui/container/inventory.png", 7, 83, 18, 18, 256, 256, 1));
        m.put(GuiPart.HOTBAR, new Region(widgets, 0, 0, 182, 22, 256, 256, 1));
        // the whole 24 px frame; Gui.renderHotbar draws its top 22 rows only
        m.put(GuiPart.HOTBAR_SELECTION, new Region(widgets, 0, 22, 24, 24, 256, 256, 4));
        // Checkbox: u = 20 when focused, v = 20 when selected
        m.put(GuiPart.CHECKBOX, new Region(checkbox, 0, 0, 20, 20, 64, 64, 0));
        m.put(GuiPart.CHECKBOX_HIGHLIGHTED, new Region(checkbox, 20, 0, 20, 20, 64, 64, 0));
        m.put(GuiPart.CHECKBOX_SELECTED, new Region(checkbox, 0, 20, 20, 20, 64, 64, 0));
        m.put(GuiPart.CHECKBOX_SELECTED_HIGHLIGHTED, new Region(checkbox, 20, 20, 20, 20, 64, 64, 0));
        // AbstractSliderButton: track v = 0 or 20 (focused), handle v = 40 or 60 (hovered)
        m.put(GuiPart.SLIDER, new Region(slider, 0, 0, 200, 20, 256, 256, 20, 4));
        m.put(GuiPart.SLIDER_HIGHLIGHTED, new Region(slider, 0, 20, 200, 20, 256, 256, 20, 4));
        m.put(GuiPart.SLIDER_HANDLE, new Region(slider, 0, 40, 200, 20, 256, 256, 20, 4));
        m.put(GuiPart.SLIDER_HANDLE_HIGHLIGHTED, new Region(slider, 0, 60, 200, 20, 256, 256, 20, 4));
        // TabButton: v = 24 * (0 selected, 1 selected hovered, 2 idle, 3 hovered), sliced 2 px but none at the bottom
        m.put(GuiPart.TAB_SELECTED, new Region(tab, 0, 0, 130, 24, 256, 256, 2, 2, 2, 0));
        m.put(GuiPart.TAB_SELECTED_HIGHLIGHTED, new Region(tab, 0, 24, 130, 24, 256, 256, 2, 2, 2, 0));
        m.put(GuiPart.TAB, new Region(tab, 0, 48, 130, 24, 256, 256, 2, 2, 2, 0));
        m.put(GuiPart.TAB_HIGHLIGHTED, new Region(tab, 0, 72, 130, 24, 256, 256, 2, 2, 2, 0));
        container(m);
        return m;
    }

    /**
     * The panel, section and well every version takes from inventory.png, the container art resource packs
     * restyle first; same layout in the 1.18.2 to 26.1.2 jars. Its right edge stops at 5 px, where the
     * crafting result slot comes within 6 px of the border; 7 px elsewhere keeps a pack's rounded corners.
     * The 15 by 8 fill sits between the player box and the crafting grid, plain in every jar.
     */
    private static void container(Map<GuiPart, Art> m) {
        String inventory = "minecraft:textures/gui/container/inventory.png";
        Region fill = new Region(inventory, 80, 8, 15, 8, 256, 256, 0);
        m.put(GuiPart.PANEL, new Framed(new Region(inventory, 0, 0, 176, 166, 256, 256, 7, 7, 5, 7), fill));
        m.put(GuiPart.SECTION, fill);
        m.put(GuiPart.WELL, new Region(inventory, 7, 83, 18, 18, 256, 256, 3));
    }

    private static Map<GuiPart, Art> legacyWidgets() {
        String widgets = "minecraft:textures/gui/widgets.png";
        Map<GuiPart, Art> m = widgets();
        // AbstractSliderButton: the track is the inactive button (v = 46), focused or not; the handle is the
        // idle (v = 66) or hovered (v = 86) button, its 4 px edges only
        m.put(GuiPart.SLIDER, new Region(widgets, 0, 46, 200, 20, 256, 256, 20, 4));
        m.put(GuiPart.SLIDER_HIGHLIGHTED, new Region(widgets, 0, 46, 200, 20, 256, 256, 20, 4));
        m.put(GuiPart.SLIDER_HANDLE, new Region(widgets, 0, 66, 200, 20, 256, 256, 4, 4));
        m.put(GuiPart.SLIDER_HANDLE_HIGHLIGHTED, new Region(widgets, 0, 86, 200, 20, 256, 256, 4, 4));
        m.remove(GuiPart.TAB_SELECTED);
        m.remove(GuiPart.TAB_SELECTED_HIGHLIGHTED);
        m.remove(GuiPart.TAB);
        m.remove(GuiPart.TAB_HIGHLIGHTED);
        return m;
    }

    private static Map<GuiPart, Art> atlas() {
        Map<GuiPart, Art> m = new EnumMap<>(GuiPart.class);
        m.put(GuiPart.BUTTON, new Sprite("minecraft:widget/button"));
        m.put(GuiPart.BUTTON_HIGHLIGHTED, new Sprite("minecraft:widget/button_highlighted"));
        m.put(GuiPart.BUTTON_DISABLED, new Sprite("minecraft:widget/button_disabled"));
        m.put(GuiPart.SLOT, new Sprite("minecraft:container/slot"));
        m.put(GuiPart.HOTBAR, new Sprite("minecraft:hud/hotbar"));
        m.put(GuiPart.HOTBAR_SELECTION, new Sprite("minecraft:hud/hotbar_selection"));
        m.put(GuiPart.TOOLTIP_BACKGROUND, new Sprite("minecraft:tooltip/background"));
        m.put(GuiPart.TOOLTIP_FRAME, new Sprite("minecraft:tooltip/frame"));
        m.put(GuiPart.CHECKBOX, new Sprite("minecraft:widget/checkbox"));
        m.put(GuiPart.CHECKBOX_HIGHLIGHTED, new Sprite("minecraft:widget/checkbox_highlighted"));
        m.put(GuiPart.CHECKBOX_SELECTED, new Sprite("minecraft:widget/checkbox_selected"));
        m.put(GuiPart.CHECKBOX_SELECTED_HIGHLIGHTED, new Sprite("minecraft:widget/checkbox_selected_highlighted"));
        m.put(GuiPart.SLIDER, new Sprite("minecraft:widget/slider"));
        m.put(GuiPart.SLIDER_HIGHLIGHTED, new Sprite("minecraft:widget/slider_highlighted"));
        m.put(GuiPart.SLIDER_HANDLE, new Sprite("minecraft:widget/slider_handle"));
        m.put(GuiPart.SLIDER_HANDLE_HIGHLIGHTED, new Sprite("minecraft:widget/slider_handle_highlighted"));
        m.put(GuiPart.TAB, new Sprite("minecraft:widget/tab"));
        m.put(GuiPart.TAB_HIGHLIGHTED, new Sprite("minecraft:widget/tab_highlighted"));
        m.put(GuiPart.TAB_SELECTED, new Sprite("minecraft:widget/tab_selected"));
        m.put(GuiPart.TAB_SELECTED_HIGHLIGHTED, new Sprite("minecraft:widget/tab_selected_highlighted"));
        m.put(GuiPart.SCROLLER, new Sprite("minecraft:widget/scroller"));
        m.put(GuiPart.SCROLLER_BACKGROUND, new Sprite("minecraft:widget/scroller_background"));
        m.put(GuiPart.TEXT_FIELD, new Sprite("minecraft:widget/text_field"));
        m.put(GuiPart.TEXT_FIELD_HIGHLIGHTED, new Sprite("minecraft:widget/text_field_highlighted"));
        container(m);
        return m;
    }
}
