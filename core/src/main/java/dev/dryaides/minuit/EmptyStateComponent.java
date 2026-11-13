package dev.dryaides.minuit;

import java.util.List;

/**
 * What a list, a search or a page shows when it has nothing yet: an optional icon, a title, a short text and an
 * optional action button, centred in the box in the theme's inks.
 *
 * <pre>{@code
 * add(new EmptyStateComponent(at(new Box("none", 10, 30, 200, 100)), words.get("mymod.waypoints.none"))
 *         .icon(Icon.FOLDER)
 *         .text(words.get("mymod.waypoints.none.hint"))
 *         .action(words.get("mymod.waypoints.add"), this::addWaypoint));
 * }</pre>
 *
 * <p>A built-in glyph is drawn at twice its size; any other icon (an item, a 16 px sprite) at its own size. The
 * text wraps to the box; a box too short drops the text first, then the icon, and keeps the title and the button.
 * The button takes the clicks and the keyboard as any {@link ButtonComponent}.
 */
public class EmptyStateComponent extends BaseComponent {

    private static final int GAP = 6;
    private static final int BUTTON_H = 16;
    private static final int BUTTON_PAD = 12;
    private static final int SIDE = 6;
    /** Glyphs up to this size are built-in ones and drawn at twice their size. */
    private static final int GLYPH_MAX = 12;

    private String title;
    private String text = "";
    private Icon icon;
    private IconArt iconArt;
    private ButtonComponent button;

    /** @param title the line that says what is empty; may be null or empty */
    public EmptyStateComponent(Box box, String title) {
        super(box);
        this.title = title == null ? "" : title;
    }

    public EmptyStateComponent title(String title) {
        this.title = title == null ? "" : title;
        return this;
    }

    /** The short text under the title: what to do about it. Null or empty shows none. */
    public EmptyStateComponent text(String text) {
        this.text = text == null ? "" : text;
        return this;
    }

    /** A built-in glyph in the theme's look; null shows none. */
    public EmptyStateComponent icon(Icon icon) {
        this.icon = icon;
        this.iconArt = null;
        return this;
    }

    /** Any icon: an item, a sprite, a glyph of the mod's own; null shows none. */
    public EmptyStateComponent icon(IconArt art) {
        this.iconArt = art;
        this.icon = null;
        return this;
    }

    /** A button under the text that runs {@code action}; a null label removes it. */
    public EmptyStateComponent action(String label, Runnable action) {
        this.button = label == null ? null : new ButtonComponent(new Box("action", this.x, this.y, 0, BUTTON_H), label, action);
        return this;
    }

    /** The action button, to set its role or tooltip; null without one. */
    public ButtonComponent button() {
        return this.button;
    }

    private IconArt art(Theme theme) {
        return this.iconArt != null ? this.iconArt : this.icon != null ? theme.icon(this.icon) : null;
    }

    private static int scaleOf(IconArt art) {
        return art.width() <= GLYPH_MAX && art.height() <= GLYPH_MAX ? 2 : 1;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        int under = p.surface();
        int ink = this.active ? Ink.on(p, under) : p.disabledText();
        int soft = this.active ? Ink.soft(p, under) : p.disabledText();
        int room = Math.max(0, this.w - 2 * SIDE);

        IconArt art = art(theme);
        int scale = art == null ? 1 : scaleOf(art);
        int iconH = art == null ? 0 : art.height() * scale + GAP;
        int titleH = this.title.isEmpty() ? 0 : SurfaceLook.FONT_H;
        List<String> lines = this.text.isEmpty() || room <= 0 ? List.of() : TextMeasure.wrapAll(this.text, room);
        int textH = lines.isEmpty() ? 0 : GAP / 2 + lines.size() * SurfaceLook.LINE;
        int buttonH = this.button == null ? 0 : GAP + BUTTON_H;
        int need = titleH + buttonH;
        if (need + textH > this.h) {
            textH = 0;
            lines = List.of();
        }
        if (need + textH + iconH > this.h) {
            iconH = 0;
            art = null;
        }

        int cx = this.x + this.w / 2;
        int top = this.y + Math.max(0, (this.h - (iconH + titleH + textH + buttonH)) / 2);
        if (art != null) {
            int aw = art.width() * scale;
            d.push(cx - aw / 2, top, scale);
            art.draw(d, 0, 0, soft, this.active ? 1F : 0.55F);
            d.pop();
            top += iconH;
        }
        if (titleH > 0) {
            String shown = d.ellipsize(this.title, room);
            d.centered(shown, cx, top, ink);
            top += titleH;
        }
        if (textH > 0) {
            top += GAP / 2;
            for (String line : lines) {
                d.centered(line, cx, top + 1, soft);
                top += SurfaceLook.LINE;
            }
        }
        if (this.button != null) {
            top += GAP;
            int bw = Math.min(room, TextMeasure.width(this.button.label) + 2 * BUTTON_PAD);
            this.button.x = cx - bw / 2;
            this.button.y = top;
            this.button.w = bw;
            this.button.h = BUTTON_H;
            this.button.active = this.active;
            this.button.draw(d, theme, mx, my, this.delta);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return this.button != null && this.button.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return this.button != null && this.button.mouseReleased(mx, my, button);
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (this.button != null && this.button.visible && this.button.isOver(mx, my)) {
            return this.button.tooltipAt(mx, my);
        }
        return super.tooltipAt(mx, my);
    }

    @Override
    public List<BaseComponent> stops() {
        return this.button == null ? List.of() : this.button.stops();
    }
}
