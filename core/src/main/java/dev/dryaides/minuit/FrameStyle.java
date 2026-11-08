package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Maths;

/**
 * How a theme draws its surfaces. The palette says which colours; this says which shapes: a
 * stitched leather cover, a bevelled bedrock panel, a flat slate card.
 */
public interface FrameStyle {

    /** The most a family may draw outside a widget's box on one side. */
    int BLEED_SIDE = 3;

    /** The most a family may draw outside a widget's box on two opposite sides together. */
    int BLEED_AXIS = 4;

    /**
     * What {@link #button} draws outside the box it is given; the box stays the hit and label area,
     * the same in every family, so a layout never changes with the theme. Layouts leave
     * {@link #BLEED_AXIS} between neighbours. Read it through {@link #bleedOf}, which keeps it in the caps.
     */
    default Insets bleed() {
        return Insets.NONE;
    }

    /** {@code style}'s {@link #bleed}, each side cut to {@link #BLEED_SIDE} and each axis to {@link #BLEED_AXIS}. */
    static Insets bleedOf(FrameStyle style) {
        Insets b = style.bleed();
        int top = Maths.clamp(b.top(), 0, BLEED_SIDE);
        int left = Maths.clamp(b.left(), 0, BLEED_SIDE);
        int bottom = Maths.clamp(b.bottom(), 0, Math.min(BLEED_SIDE, BLEED_AXIS - top));
        int right = Maths.clamp(b.right(), 0, Math.min(BLEED_SIDE, BLEED_AXIS - left));
        return new Insets(top, left, right, bottom);
    }

    /** The corner radius of {@link #panel}, so a ring drawn around a panel follows its corners. */
    default int corner() {
        return 0;
    }

    /** The drop shadow of a {@link #panel} at x, y, w, h: the panel's own outline, filled with {@code color}. */
    default void shadow(Draw d, Palette p, int x, int y, int w, int h, int color) {
        Shapes.rounded(d, x, y, w, h, corner(), color);
    }

    /** The whole panel: frame, then the surface inside it. */
    void panel(Draw d, Palette p, int x, int y, int w, int h);

    /** The title band at the top of the panel. */
    void header(Draw d, Palette p, int x, int y, int w, int h);

    /** A card on the surface. */
    void section(Draw d, Palette p, int x, int y, int w, int h);

    /** A sunken area: a list, a field, a meter. */
    void well(Draw d, Palette p, int x, int y, int w, int h, boolean focused);

    /** A button or a chip; {@code highlight} false draws it without its bevel. */
    default void raised(Draw d, Palette p, int x, int y, int w, int h, int color, boolean highlight) {
        raised(d, p, x, y, w, h, color, highlight ? Shapes.Relief.RAISED : Shapes.Relief.FLAT);
    }

    /** A button or a chip, its bevel derived from {@code color} so every theme gets one. */
    default void raised(Draw d, Palette p, int x, int y, int w, int h, int color, Shapes.Relief relief) {
        Shapes.pill(d, x, y, w, h, color, p.shadow(), relief);
    }

    /**
     * A button wearing a role: the hero in a ring of the accent colour, a secondary one in an outline
     * and no bevel at rest, the others as {@link #raised}. A null role is a plain button.
     */
    default void button(Draw d, Palette p, int x, int y, int w, int h, ButtonComponent.Role role, int color,
                        Shapes.Relief relief) {
        if (role == ButtonComponent.Role.HERO) {
            Shapes.rounded(d, x, y, w, h, 3, p.accent());
            raised(d, p, x + 1, y + 1, w - 2, h - 2, color, relief);
        } else if (role == ButtonComponent.Role.SECONDARY) {
            Shapes.rounded(d, x, y, w, h, 3, p.wellEdge());
            raised(d, p, x + 1, y + 1, w - 2, h - 2, color,
                    relief == Shapes.Relief.PRESSED ? relief : Shapes.Relief.FLAT);
        } else {
            raised(d, p, x, y, w, h, color, relief);
        }
    }

    /** The face of a toggled-on choice; the default holds the hover face, a family may sink it instead. */
    default int selectedFace(Palette p, float hover) {
        return Shapes.selectedFace(p, hover);
    }

    /** The bevel of a toggled-on choice. */
    default Shapes.Relief selectedRelief() {
        return Shapes.Relief.RAISED;
    }

    /** A bar from 0 to 1. */
    default void bar(Draw d, Palette p, int x, int y, int w, int h, float value, int color) {
        int r = Math.min(3, h / 2);
        Shapes.rounded(d, x, y, w, h, r, p.track());
        int filled = Math.round(Maths.clamp(value, 0F, 1F) * (w - 2));
        if (filled > 0) {
            int drawn = Math.max(filled, Math.min(w - 2, h - 2));
            Shapes.rounded(d, x + 1, y + 1, drawn, h - 2, Math.max(0, r - 1), color);
            if (h >= 5) {
                d.fill(x + 2, y + 1, x + drawn - 1, y + 2, 0x40FFFFFF);
            }
        }
    }

    /** The knob of a slider, 7 by 8. */
    default void knob(Draw d, Palette p, int x, int y, boolean lit) {
        Shapes.rounded(d, x, y, 7, 8, 2, p.frameDark());
        int face = lit ? p.primaryHover() : p.primary();
        Shapes.rounded(d, x + 1, y + 1, 5, 6, 2, face);
        Shapes.bevel(d, x + 1, y + 1, 5, 6, 2, face, Shapes.Relief.RAISED);
    }

    /** The groove of a toggle or a slider, filled with {@code color}. */
    default void track(Draw d, Palette p, int x, int y, int w, int h, int color) {
        Shapes.rounded(d, x, y, w, h, h / 2, color);
    }

    /** The knob of a toggle, 8 by 8; {@code active} false draws it flat. */
    default void toggleKnob(Draw d, Palette p, int x, int y, int face, boolean active) {
        Shapes.rounded(d, x, y, 8, 8, 4, face);
        Shapes.bevel(d, x, y, 8, 8, 4, face, active ? Shapes.Relief.RAISED : Shapes.Relief.FLAT);
    }

    /**
     * A slider's groove, {@code h} high, its first {@code filled} pixels in the accent. {@code steps}
     * is the number of steps the value can take; the default draws one continuous groove.
     */
    default void groove(Draw d, Palette p, int x, int y, int w, int h, int filled, int steps, boolean active) {
        track(d, p, x, y, w, h, p.wellEdge());
        Shapes.rounded(d, x, y, filled, h, 2, active ? p.accent() : p.disabledText());
    }

    /** The knob of a toggle, {@code lit} under the mouse or the keyboard; the default ignores it. */
    default void toggleKnob(Draw d, Palette p, int x, int y, int face, boolean active, boolean lit) {
        toggleKnob(d, p, x, y, face, active);
    }

    /**
     * One inventory cell, 18 by 18: a well with a 1 px inset around the 16 by 16 item. Hovered lays the
     * hover face at half strength on the item area; {@code marked} rings the cell in the accent.
     */
    default void slot(Draw d, Palette p, int x, int y, boolean hovered, boolean marked) {
        well(d, p, x, y, 18, 18, false);
        if (hovered) {
            d.fill(x + 1, y + 1, x + 17, y + 17, Shapes.SLOT_HOVER_BACK);
        }
        if (marked) {
            Shapes.ring(d, x, y, 18, 18, 0, p.accent());
        }
    }

    /**
     * A text or key field's box: the {@link #well}, its face darkened on a light palette so the
     * light {@link #fieldText} reads on it, as vanilla's black field with light text does.
     */
    default void field(Draw d, Palette p, int x, int y, int w, int h, boolean focused) {
        well(d, FieldInk.palette(p), x, y, w, h, focused);
    }

    /** The text colour inside a {@link #field}: always light, a text a mod draws with the game's shadow stays sharp. */
    default int fieldText(Palette p) {
        return FieldInk.text(p);
    }

    /** The placeholder colour inside a {@link #field}. */
    default int fieldHint(Palette p) {
        return FieldInk.hint(p);
    }

    /** The frame around the held hotbar cell, 18 by 18 at x, y; the default is a double ring in the text colour. */
    default void hotbarSelection(Draw d, Palette p, int x, int y) {
        Shapes.ring(d, x - 1, y - 1, 20, 20, 0, p.text());
        Shapes.ring(d, x, y, 18, 18, 0, p.text());
    }

    /**
     * True when the family draws a toggle as a {@link #checkbox}, a slider as a {@link #sliderBar} the
     * height of its box, tabs and toggle groups as {@link #tab}s; false keeps the switch, groove and plates.
     */
    default boolean gameWidgets() {
        return false;
    }

    /** The side of a {@link #checkbox} in a row {@code rowHeight} tall: the game's 20, less in a shorter row, so stacked rows never touch. */
    static int checkboxSide(int rowHeight) {
        return Maths.clamp(rowHeight - 4, 9, 20);
    }

    /** A checkbox, {@code side} by {@code side}, ticked when {@code on}; {@code lit} under the mouse or the keyboard. */
    default void checkbox(Draw d, Palette p, int x, int y, int side, boolean on, boolean lit, boolean active) {
        Shapes.rounded(d, x, y, side, side, 2, lit ? p.text() : p.wellEdge());
        Shapes.rounded(d, x + 1, y + 1, side - 2, side - 2, 1, active ? p.track() : p.disabled());
        if (on) {
            int in = Math.round(side * 0.3F);
            d.fill(x + in, y + in, x + side - in, y + side - in, active ? p.accent() : p.disabledText());
        }
    }

    /** A slider's bar over its whole box, the value written inside it by the slider. */
    default void sliderBar(Draw d, Palette p, int x, int y, int w, int h, boolean lit, boolean active) {
        well(d, p, x, y, w, h, lit);
    }

    /** A slider's handle, 8 wide over the bar's height. */
    default void sliderHandle(Draw d, Palette p, int x, int y, int h, boolean lit) {
        raised(d, p, x, y, 8, h, lit ? p.raisedHover() : p.raised(), Shapes.Relief.RAISED);
    }

    /**
     * One tab in its box. The selected one fills the box and leaves its bottom open onto the page under
     * it; the others start lower and stand on the rule {@link #tabRule} draws between them.
     */
    default void tab(Draw d, Palette p, int x, int y, int w, int h, boolean selected, boolean lit) {
        raised(d, p, x, y, w, h, selected ? selectedFace(p, lit ? 1F : 0F) : lit ? p.raisedHover() : p.raised(),
                selected ? selectedRelief() : Shapes.Relief.RAISED);
    }

    /** How far below its box's top a {@link #tab} of height {@code h} starts. */
    default int tabInset(int h, boolean selected) {
        return 0;
    }

    /** The rule under a row of {@link #tab}s, in a gap between two of them; the default draws none. */
    default void tabRule(Draw d, Palette p, int x, int y, int w, int h) {
    }

    /** The label colour of a {@link #tab}. */
    default int tabText(Palette p, boolean selected, boolean active) {
        return active ? p.raisedText() : p.disabledText();
    }

    /** A scroll bar BAR wide at x, its track from y over h, its thumb from {@code thumbY} over {@code thumbH}. */
    default void scroller(Draw d, Palette p, int x, int y, int h, int thumbY, int thumbH, boolean lit) {
        Shapes.rounded(d, x + 1, y, 3, h, 1, p.track());
        Shapes.rounded(d, x + 1, thumbY, 3, thumbH, 1, lit ? p.raisedHover() : p.raised());
    }

    /** A toast's card; the default is the panel. */
    default void toast(Draw d, Palette p, int x, int y, int w, int h) {
        panel(d, p, x, y, w, h);
    }
}
