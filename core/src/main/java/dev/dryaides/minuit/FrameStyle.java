package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Maths;
import dev.dryaides.minuit.internal.OfficialPalettes;

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

    /** {@code box} grown by {@code style}'s {@link #bleedOf bleed}: the whole silhouette of a button the family draws there. */
    static Box withBleed(FrameStyle style, Box box) {
        Insets b = bleedOf(style);
        return new Box(box.name(), box.x() - b.left(), box.y() - b.top(), box.w() + b.left() + b.right(),
                box.h() + b.top() + b.bottom());
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

    /** The {@link #shadow} of a box floating {@code depth} pixels over what lies under it, at the {@link #popup}'s strength. */
    default void dropShadow(Draw d, Palette p, int x, int y, int w, int h, int depth) {
        if (depth > 0) {
            shadow(d, p, x, y + depth, w, h, Shapes.alpha(p.shadow(), 0x60));
        }
    }

    /**
     * Where {@link #panel} content starts on each side: the frame's width. A popup's rows start one pixel further
     * in, so a ring around a row stays off the frame.
     */
    default Insets panelInsets() {
        return new Insets(3, 3, 3, 3);
    }

    /** How many pixels {@link #panel} and {@link #popup} draw below the box: an arrow under a panel starts past them. */
    default int panelShadow() {
        return 0;
    }

    /** The whole panel: frame, then the surface inside it. */
    void panel(Draw d, Palette p, int x, int y, int w, int h);

    /** The title band at the top of the panel. */
    void header(Draw d, Palette p, int x, int y, int w, int h);

    /** A card on the surface. */
    void section(Draw d, Palette p, int x, int y, int w, int h);

    /** A sunken area: a list, a field, a meter. */
    void well(Draw d, Palette p, int x, int y, int w, int h, boolean focused);

    /** A well that answers the mouse, {@code lit} under it; the default draws it the same lit or not. */
    default void well(Draw d, Palette p, int x, int y, int w, int h, boolean focused, boolean lit) {
        well(d, p, x, y, w, h, focused);
    }

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

    /**
     * The mark a toggled-on option button carries over its face, drawn after the button at its box;
     * tabs and segments never get it. None by default.
     */
    default void selectedMark(Draw d, Palette p, int x, int y, int w, int h, int color) {
    }

    /** The bevel of a toggled-on choice. */
    default Shapes.Relief selectedRelief() {
        return Shapes.Relief.RAISED;
    }

    /**
     * The dark square a {@link FramedIcon} sits in, {@code size} square at {@code x, y}, as Bedrock frames the icons
     * of its settings list: a 1 px outline in the well's edge, a face darker than the frame, lit top left. Rounded by
     * the family's {@link #corner}, at most 2 px.
     */
    default void iconFrame(Draw d, Palette p, int x, int y, int size) {
        int r = Math.min(2, corner());
        int face = Shapes.mix(p.frameDark() | 0xFF000000, 0xFF000000, 0.45F);
        Shapes.rounded(d, x, y, size, size, r, Shapes.mix(p.wellEdge() | 0xFF000000, 0xFF000000, 0.5F));
        Shapes.rounded(d, x + 1, y + 1, size - 2, size - 2, Math.max(0, r - 1), face);
        Shapes.bevel(d, x + 1, y + 1, size - 2, size - 2, Math.max(0, r - 1), face, Shapes.Relief.RAISED);
    }

    /** The colour of a glyph inside {@link #iconFrame}: light, tinted by the accent, to read on the dark face. */
    default int iconFrameInk(Palette p) {
        return Shapes.mix(p.accent() | 0xFF000000, 0xFFFFFFFF, 0.6F);
    }

    /** The style for a button that carries no text (an icon, a swatch); a family whose lip depends on text returns its own. */
    default FrameStyle iconStyle() {
        return this;
    }

    /** How many pixels a button's label moves down while the button is held; it follows the face {@link #raised} sinks. */
    default int pressTravel() {
        return 1;
    }

    /**
     * Milliseconds a held button takes to sink its {@link #pressTravel}, and to rise back; 0, the default,
     * draws the press at once through {@link #button}'s relief.
     */
    default int pressMs() {
        return 0;
    }

    /**
     * A button {@code sunk} whole pixels into its press, 0 up to {@link #pressTravel}, for a family with a
     * {@link #pressMs}; the default draws it pressed for any depth.
     */
    default void button(Draw d, Palette p, int x, int y, int w, int h, ButtonComponent.Role role, int color,
                        Shapes.Relief relief, int sunk) {
        button(d, p, x, y, w, h, role, color, sunk > 0 ? Shapes.Relief.PRESSED : relief);
    }

    /**
     * The rows at the foot of a button {@code h} px tall that its face does not cover at rest (a lip the face
     * sinks into); the label centres on the face above them. 0, the default, centres it on the whole box.
     */
    default int buttonLip(int h) {
        return 0;
    }

    /** How many pixels a held button's label moves right. */
    default int pressShiftX() {
        return 1;
    }

    /** How many pixels the label of a toggled-on choice sits lower, for a family whose {@link #selectedRelief} sinks the face. */
    default int selectedTravel() {
        return 0;
    }

    /** A bar from 0 to 1. */
    default void bar(Draw d, Palette p, int x, int y, int w, int h, float value, int color) {
        int r = Math.min(3, h / 2);
        Shapes.rounded(d, x, y, w, h, r, p.track());
        int filled = Math.round(Maths.clamp(value, 0F, 1F) * (w - 2));
        if (filled > 0) {
            int drawn = FrameStyles.pillWidth(w, h, filled);
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

    /**
     * The 1 px rim of an off toggle's groove, round {@link #track}'s shape. The groove grey sits a step off the
     * panel in most palettes, too close to read as a switch; the rim takes a role made to be seen on the panel.
     * A family whose groove already carries its own outline draws nothing.
     */
    default void trackEdge(Draw d, Palette p, int x, int y, int w, int h, int color) {
        Shapes.band(d, x, y, w, h, h / 2, 1, color);
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
        well(d, p.slot() == 0 ? p : slotWell(p), x, y, 18, 18, false);
        if (hovered) {
            d.fill(x + 1, y + 1, x + 17, y + 17, Shapes.SLOT_HOVER_BACK);
        }
        if (marked) {
            Shapes.ring(d, x, y, 18, 18, 0, p.accent());
        }
    }

    /** {@code p} with its well turned into the slot face, for a family whose slot is its plain well. */
    private static Palette slotWell(Palette p) {
        return OfficialPalettes.derive(p, new Palette(p.frame(), p.frameDark(), p.frameLight(), p.trim(), p.surface(),
                p.surfaceEdge(), p.section(), p.slot(), p.wellEdge(), p.text(), p.textSoft(), p.textFaint(), p.raised(),
                p.raisedHover(), p.raisedText(), p.primary(), p.primaryHover(), p.primaryText(), p.accent(),
                p.accentDeep(), p.good(), p.warn(), p.bad(), p.destructive(), p.destructiveHover(),
                p.destructiveText(), p.track(), p.disabled(), p.disabledText(), p.shadow(), p.link(), p.backdrop(), p.line(),
                p.mark(), p.slot()));
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

    /**
     * The game's HUD hotbar bar, 182 by 22 at x, y: nine 20 px cells, an item 3 px in from each cell's corner.
     * The default is a bar of the family's slot wells, 18 px each, 1 px in from the 20 px cells.
     */
    default void hotbar(Draw d, Palette p, int x, int y) {
        d.fill(x, y, x + 182, y + 22, p.frameDark());
        for (int i = 0; i < 9; i++) {
            slot(d, p, x + 2 + i * 20, y + 2, false, false);
        }
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

    /**
     * The height of a button a component sizes itself (a {@link RadialComponent}'s page cards), not one a caller
     * laid out: the game's 20 by default.
     */
    default int buttonHeight() {
        return 20;
    }

    /** The height of a {@link TabsComponent} row the family asks for; {@code base} is what a caller would use without it. */
    default int tabHeight(int base) {
        return base;
    }

    /** How far below its box's top a {@link #tab} of height {@code h} starts. */
    default int tabInset(int h, boolean selected) {
        return 0;
    }

    /** The rule under a row of {@link #tab}s, in a gap between two of them; the default draws none. */
    default void tabRule(Draw d, Palette p, int x, int y, int w, int h) {
    }

    /**
     * The columns between two tabs of a {@link TabsComponent} row; negative overlaps them, the shown
     * tab drawn over its neighbours.
     */
    default int tabGap() {
        return TabsComponent.GAP;
    }

    /** The label colour of a {@link #tab}. */
    default int tabText(Palette p, boolean selected, boolean active) {
        return active ? p.raisedText() : p.disabledText();
    }

    /** The blank columns {@link #scroller} leaves on the right of its box: its track's outer edge lies that far in. */
    default int scrollerGap() {
        return 2;
    }

    /** A scroll bar BAR wide at x, its track from y over h, its thumb from {@code thumbY} over {@code thumbH}. */
    default void scroller(Draw d, Palette p, int x, int y, int h, int thumbY, int thumbH, boolean lit) {
        int track = FormLook.solid(p.track());
        // The faint ink is made to be seen on the panel; the raised face sat a step off the track and vanished.
        int thumb = FormLook.solid(lit ? p.textSoft() : p.textFaint());
        Shapes.rounded(d, x + 1, y, 3, h, 1, track);
        Shapes.rounded(d, x + 1, thumbY, 3, thumbH, 1, thumb);
    }

    /**
     * A flat box in the family's shape: a badge, a chip at rest, a day cell, a band, a selection ring. A 0
     * {@code fill} or {@code edge} skips it. Flat by contract: a family never bevels it, so nested parts never
     * stack bevels.
     */
    default void plate(Draw d, Palette p, int x, int y, int w, int h, int fill, int edge) {
        int r = plateCorner(w, h);
        if (fill != 0) {
            Shapes.rounded(d, x, y, w, h, r, fill);
        }
        if (edge != 0) {
            // A bare 1 px ring with a 3 px corner loses one pixel per corner and reads square: it takes one more.
            Shapes.ring(d, x, y, w, h, fill == 0 && r > 0 ? Math.min(r + 1, Math.min(w, h) / 2) : r, edge);
        }
    }

    /** A line {@code outset} pixels thick around the {@link #plate} at x, y, w, h, following its corners with no gap. */
    default void ringAround(Draw d, Palette p, int x, int y, int w, int h, int outset, int color) {
        Shapes.band(d, x - outset, y - outset, w + 2 * outset, h + 2 * outset, plateCorner(w, h) + outset, outset, color);
    }

    /** The corner radius {@link #button} draws: a focus ring around a button is this corner grown by its distance. */
    default int buttonCorner() {
        return 3;
    }

    /**
     * A line {@code thickness} pixels thick inside the {@link #panel}'s own outline at x, y, w, h, following its
     * corners: a chosen card's ring, so no bevel pixel of the panel shows past it.
     */
    default void panelEdge(Draw d, Palette p, int x, int y, int w, int h, int thickness, int color) {
        Shapes.band(d, x, y, w, h, corner(), thickness, color);
    }

    /** The edge of a {@link #plate}, {@code thickness} pixels thick inside the box. */
    default void plateEdge(Draw d, Palette p, int x, int y, int w, int h, int thickness, int color) {
        Shapes.band(d, x, y, w, h, plateCorner(w, h), thickness, color);
    }

    /** The corner of a {@link #plate} this size: the panel's corner, kept under a third of the short side. */
    default int plateCorner(int w, int h) {
        return Math.min(corner(), Math.min(w, h) / 3);
    }

    /** A round mark, {@code side} by {@code side}: a status dot, a step marker, a carousel dot, a timeline point. */
    default void dot(Draw d, Palette p, int x, int y, int side, int fill, int edge) {
        if (fill != 0) {
            Shapes.rounded(d, x, y, side, side, side / 2, fill);
        }
        if (edge != 0) {
            Shapes.ring(d, x, y, side, side, side / 2, edge);
        }
    }

    /** A list row's highlight in {@code face}, a clean fill without the plate's relief; a 0 face draws nothing. */
    default void row(Draw d, Palette p, int x, int y, int w, int h, int face) {
        if (face != 0) {
            Shapes.rounded(d, x, y, w, h, Math.min(corner(), Math.min(w, h) / 2), face);
        }
    }

    /**
     * A card over other widgets: its shadow, a solid backing under a see-through surface so the widgets
     * beneath do not show through its words, then the {@link #panel}.
     */
    default void popup(Draw d, Palette p, int x, int y, int w, int h) {
        dropShadow(d, p, x, y, w, h, 2);
        int surface = p.surface();
        int c = Math.max(1, corner());
        if (surface >>> 24 != 0xFF && w > 2 * c && h > 2 * c) {
            int solid = surface | 0xFF000000;
            d.fill(x + c, y + 1, x + w - c, y + h - 1, solid);
            d.fill(x + 1, y + c, x + c, y + h - c, solid);
            d.fill(x + w - c, y + c, x + w - 1, y + h - c, solid);
        }
        panel(d, p, x, y, w, h);
    }

    /**
     * A checkbox that can also be mixed. Where {@link #gameWidgets} it is the boolean {@link #checkbox} with a
     * dash for mixed; otherwise a ring at rest and a {@link #plate} in the primary colour, ticked or dashed.
     */
    default void checkbox(Draw d, Palette p, int x, int y, int side, CheckboxComponent.State state, boolean lit,
                          boolean active) {
        if (gameWidgets()) {
            checkbox(d, p, x, y, side, state == CheckboxComponent.State.ON, lit, active);
            if (state == CheckboxComponent.State.MIXED) {
                FormLook.dash(d, x, y, side, active ? p.accent() : p.disabledText());
            }
        } else if (state == CheckboxComponent.State.OFF) {
            if (active) {
                checkbox(d, p, x, y, side, false, lit, true);
                if (!lit) {
                    plate(d, p, x, y, side, side, 0, FormLook.edge(p));
                }
            } else {
                plate(d, p, x, y, side, side, 0, FormLook.disabledFace(p));
            }
        } else {
            int face = !active ? FormLook.disabledFace(p) : lit ? p.primaryHover() : p.primary();
            int ink = active ? p.primaryText() : p.disabledText();
            plate(d, p, x, y, side, side, face, lit && active ? FormLook.hoverEdge(p) : 0);
            if (state == CheckboxComponent.State.ON) {
                FormLook.tick(d, x, y, side, ink);
            } else {
                FormLook.dash(d, x, y, side, ink);
            }
        }
    }

    /** A radio button, {@code side} by {@code side}, chosen when {@code on}; the default is pixel art in whole cells. */
    default void radio(Draw d, Palette p, int x, int y, int side, boolean on, boolean lit, boolean active) {
        int cell = Math.max(1, side / FormLook.RADIO_ART);
        if (on) {
            int face = !active ? FormLook.disabledFace(p) : lit ? p.primaryHover() : p.primary();
            FormLook.pixels(d, FormLook.RADIO_INSIDE, x, y, cell, face);
            FormLook.pixels(d, FormLook.RADIO_RING, x, y, cell, lit && active ? FormLook.hoverEdge(p) : face);
            FormLook.pixels(d, FormLook.RADIO_DOT, x + 2 * cell, y + 2 * cell, cell, active ? p.primaryText() : p.disabledText());
        } else if (active) {
            FormLook.pixels(d, FormLook.RADIO_INSIDE, x, y, cell, p.track());
            FormLook.pixels(d, FormLook.RADIO_RING, x, y, cell, lit ? FormLook.hoverEdge(p) : FormLook.edge(p));
        } else {
            FormLook.pixels(d, FormLook.RADIO_RING, x, y, cell, FormLook.disabledFace(p));
        }
    }

    /** A toast's card; the default is the panel. */
    default void toast(Draw d, Palette p, int x, int y, int w, int h) {
        panel(d, p, x, y, w, h);
    }
}
