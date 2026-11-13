package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Accent;
import dev.dryaides.minuit.internal.GuiPart;
import dev.dryaides.minuit.internal.Maths;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The frame styles the kit ships: the code a theme file names in its {@code family.json}
 * ({@code "style": "slate"}). Colours live in the theme files; a new shape is a new style here.
 */
public final class FrameStyles {

    private FrameStyles() {
    }

    /**
     * How wide a bar's fill is drawn: at least a round cap (the inner height), never wider than the track,
     * so a narrow bar's first percent does not cross its right frame.
     */
    static int pillWidth(int w, int h, int filled) {
        return Math.max(filled, Math.min(w - 2, h - 2));
    }

    /** The style a theme file names, or null. */
    public static FrameStyle byName(String name) {
        return name == null ? null : Holder.BY_NAME.get(name);
    }

    /** The names a theme file may use. */
    public static Set<String> names() {
        return new TreeSet<>(Holder.BY_NAME.keySet());
    }

    /** Filled after the constants above it, whatever order a caller touches them in. */
    private static final class Holder {
        static final Map<String, FrameStyle> BY_NAME = Map.of(
                "leather", LEATHER, "slate", SLATE, "bedrock", BEDROCK, "soft", SOFT, "bubbly", BUBBLY,
                "vanilla", VANILLA);
    }

    /** A notebook: stitched leather cover, a paper page, cards with a ruled line under the title. */
    public static final FrameStyle LEATHER = new FrameStyle() {
        @Override
        public Insets panelInsets() {
            return new Insets(6, 6, 6, 7);
        }

        @Override
        public int panelShadow() {
            return 3;
        }

        @Override
        public int corner() {
            return 6;
        }

        @Override
        public void panel(Draw d, Palette p, int x, int y, int w, int h) {
            Shapes.rounded(d, x + 2, y + 3, w, h, 6, p.shadow());
            Shapes.rounded(d, x, y, w, h, 6, p.frameDark());
            Shapes.rounded(d, x + 1, y + 1, w - 2, h - 2, 5, p.frame());
            d.fill(x + 6, y + 2, x + w - 6, y + 3, p.frameLight());
            for (int sx = x + 8; sx < x + w - 10; sx += 6) {
                d.fill(sx, y + 3, sx + 3, y + 4, p.trim());
                d.fill(sx, y + h - 4, sx + 3, y + h - 3, p.trim());
            }
            Shapes.rounded(d, x + 5, y + 5, w - 10, h - 10, 3, p.surfaceEdge());
            Shapes.rounded(d, x + 6, y + 6, w - 12, h - 13, 2, p.surface());
        }

        @Override
        public void header(Draw d, Palette p, int x, int y, int w, int h) {
            d.fill(x + 2, y + h - 1, x + w - 2, y + h, p.wellEdge());
        }

        @Override
        public void section(Draw d, Palette p, int x, int y, int w, int h) {
            Shapes.rounded(d, x, y, w, h, 3, p.section());
            d.fill(x + 3, y + 14, x + w - 3, y + 15, p.surfaceEdge());
        }

        @Override
        public void well(Draw d, Palette p, int x, int y, int w, int h, boolean focused) {
            Shapes.rounded(d, x, y, w, h, 3, focused ? p.frame() : p.wellEdge());
            Shapes.rounded(d, x + 1, y + 1, w - 2, h - 2, 2, p.well());
        }
    };

    /** The kit's own: a flat dark card with a thin border, readable over any world. */
    public static final FrameStyle SLATE = new FrameStyle() {
        @Override
        public int panelShadow() {
            return 2;
        }

        @Override
        public int corner() {
            return 3;
        }

        @Override
        public void panel(Draw d, Palette p, int x, int y, int w, int h) {
            Shapes.rounded(d, x + 1, y + 2, w, h, 3, p.shadow());
            Shapes.rounded(d, x, y, w, h, 3, p.frameDark());
            Shapes.rounded(d, x + 1, y + 1, w - 2, h - 2, 2, p.frame());
            d.fill(x + 3, y + 1, x + w - 3, y + 2, p.frameLight());
            Shapes.rounded(d, x + 3, y + 3, w - 6, h - 6, 2, p.surface());
        }

        @Override
        public void header(Draw d, Palette p, int x, int y, int w, int h) {
            d.fill(x + 2, y + h - 1, x + w - 2, y + h, p.surfaceEdge());
        }

        @Override
        public void section(Draw d, Palette p, int x, int y, int w, int h) {
            Shapes.rounded(d, x, y + 1, w, h, 3, 0x33000000);
            Shapes.rounded(d, x, y, w, h, 3, p.section());
        }

        @Override
        public void well(Draw d, Palette p, int x, int y, int w, int h, boolean focused) {
            Shapes.rounded(d, x, y, w, h, 2, focused ? p.accent() : p.wellEdge());
            d.fill(x + 1, y + 1, x + w - 1, y + h - 1, p.well());
        }
    };

    /**
     * Bedrock Edition's Ore UI shapes with minecraft.wiki's buttons and panels: square flat cards with an inner
     * rim on all four sides, buttons in a 1 px outline at 60 % of the face (fixed near-black on a dark chroma),
     * rims lit by the wiki's hard-light rules and a 3 px lip the pressed face sinks into over 100 ms, label
     * with it; two-square switches, segmented slider bars, icon wells with a shaded top and a lit bottom.
     */
    public static final FrameStyle BEDROCK = new Bedrock(true);

    static final class Bedrock implements FrameStyle {
        /** The lip's rows under a button or tab, and how far a pressed face and its label sink into them. */
        private static final int TRAVEL = 2;
        /** Rows of a button without text, and of a knob or thumb: the original relief, a flat face is only for text. */
        private static final int ICON_TRAVEL = 3;
        /** True for a button that carries a label: its lip is thinner so the text renders well. */
        private final boolean text;
        private final Bedrock iconStyle;

        Bedrock(boolean text) {
            this.text = text;
            this.iconStyle = text ? new Bedrock(false) : this;
        }

        @Override
        public FrameStyle iconStyle() {
            return this.iconStyle;
        }
        /** The outline of every control on a dark chroma. */
        private static final int DARK_OUTLINE = 0xFF242424;

        @Override
        public Insets panelInsets() {
            return new Insets(2, 2, 2, 4);
        }

        @Override
        public void shadow(Draw d, Palette p, int x, int y, int w, int h, int color) {
            if (w < 8 || h < 9) {
                d.fill(x, y, x + w, y + h, color);
                return;
            }
            // The panel's chamfered silhouette: 3, 2, 1 columns clear on the first and last three rows.
            for (int r = 0; r < 3; r++) {
                int skip = 3 - r;
                d.fill(x + skip, y + r, x + w - skip, y + r + 1, color);
                d.fill(x + skip, y + h - 1 - r, x + w - skip, y + h - r, color);
            }
            d.fill(x, y + 3, x + w, y + h - 3, color);
        }

        /**
         * Ore UI relief: a 3 px 45-degree chamfer on every corner; rows from the top are the edge, a lit line,
         * the body, a lit line, a 2-row trim ledge that follows the bottom chamfers, the edge.
         */
        @Override
        public void panel(Draw d, Palette p, int x, int y, int w, int h) {
            if (w < 8 || h < 9) {
                d.fill(x, y, x + w, y + h, p.frameDark());
                d.fill(x + 1, y + 1, x + w - 1, y + h - 1, p.surface());
                return;
            }
            int edge = p.frameDark();
            int lit = p.frameLight();
            int body = p.surface();
            int ledge = p.trim();
            row(d, x, y, w, 3, edge, edge, edge, edge);
            row(d, x, y + 1, w, 2, edge, lit, lit, lit);
            row(d, x, y + 2, w, 1, edge, lit, body, body);
            for (int r = 3; r < h - 5; r++) {
                row(d, x, y + r, w, 0, edge, lit, body, body);
            }
            int b = y + h - 5;
            row(d, x, b, w, 0, edge, ledge, lit, body);
            row(d, x, b + 1, w, 0, edge, ledge, ledge, lit);
            row(d, x, b + 2, w, 1, edge, ledge, ledge, ledge);
            row(d, x, b + 3, w, 2, edge, ledge, ledge, ledge);
            row(d, x, b + 4, w, 3, edge, edge, edge, edge);
        }

        /**
         * One mirrored panel row: {@code skip} columns left clear at each end for the chamfer, then
         * {@code c0, c1, c2} inward from there up to the third column, and {@code mid} across the rest.
         */
        private static void row(Draw d, int x, int y, int w, int skip, int c0, int c1, int c2, int mid) {
            int[] cols = {c0, c1, c2};
            for (int i = skip; i < 3; i++) {
                int c = cols[i - skip];
                d.fill(x + i, y, x + i + 1, y + 1, c);
                d.fill(x + w - 1 - i, y, x + w - i, y + 1, c);
            }
            d.fill(x + 3, y, x + w - 3, y + 1, mid);
        }

        /** Trim line over a lit line, inside the panel's edge and lit columns. */
        @Override
        public void header(Draw d, Palette p, int x, int y, int w, int h) {
            d.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, p.trim());
            d.fill(x + 2, y + h - 1, x + w - 2, y + h, p.frameLight());
        }

        @Override
        public void section(Draw d, Palette p, int x, int y, int w, int h) {
            d.fill(x, y, x + w, y + h, p.section());
            d.fill(x, y, x + w, y + 1, p.trim());
            d.fill(x, y + 1, x + w, y + 2, p.frameLight());
        }

        @Override
        public void well(Draw d, Palette p, int x, int y, int w, int h, boolean focused) {
            well(d, p, x, y, w, h, focused, false);
        }

        /**
         * The wiki's icon well: edge, a shaded top and a lit bottom, two rows each from 10 px tall as the
         * reference measures, one below; under the mouse the fill steps 8 % toward black on a light chroma,
         * 7 % toward white on a dark one.
         */
        @Override
        public void well(Draw d, Palette p, int x, int y, int w, int h, boolean focused, boolean lit) {
            boolean dark = Accent.dark(p);
            int fill = lit ? Shapes.mix(p.well(), dark ? 0xFFFFFFFF : 0xFF000000, dark ? 0.07F : 0.08F) : p.well();
            d.fill(x, y, x + w, y + h, focused ? p.text() : p.wellEdge());
            d.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
            if (h >= 5) {
                int rows = h >= 10 ? 2 : 1;
                d.fill(x + 1, y + 1, x + w - 1, y + 1 + rows, Shapes.mix(fill, p.wellEdge(), 0.45F));
                d.fill(x + 1, y + h - 1 - rows, x + w - 1, y + h - 1, Shapes.mix(fill, 0xFFFFFFFF, dark ? 0.12F : 0.25F));
            }
        }

        /**
         * The wiki's .mp-button: outline, top row and left column lit by hard light at 101, right column and the
         * row over the lip at 51, then the lip at 208/255 of the face. Pressed, the face darkens to 0.9, its top
         * comes down the lip's rows and the lip folds away; the bottom stays.
         */
        @Override
        public void raised(Draw d, Palette p, int x, int y, int w, int h, int color, Shapes.Relief relief) {
            int lip = knobLip(h);
            raised(d, p, x, y, w, h, color, relief, relief == Shapes.Relief.PRESSED ? lip : 0, lip);
        }

        /** The lip of a button {@code h} px tall: with text, one row under 16 px and {@link #TRAVEL} above; without, 3. */
        private int lip(int h) {
            return !this.text ? ICON_TRAVEL : h < 16 ? 1 : TRAVEL;
        }

        /** The lip of a knob, a thumb or any other plain raised part: 3 rows, two under 12 px so it keeps a face. */
        private static int knobLip(int h) {
            return h < 12 ? 2 : ICON_TRAVEL;
        }

        /** The button {@code sunk} rows into its lip: the face darkens and the lip folds row by row. */
        private void raised(Draw d, Palette p, int x, int y, int w, int h, int color, Shapes.Relief relief, int sunk, int lip) {
            boolean off = color == p.disabled();
            int k = h - lip >= 6 ? Math.max(0, Math.min(lip, sunk)) : 0;
            boolean pressed = k == lip;
            int top = y + k;
            int face = k > 0 ? scale(color, 1F - 0.1F * k / lip) : color;
            int edge = off ? p.track() : Accent.dark(p) ? DARK_OUTLINE : scale(face, 0.6F);
            d.fill(x, top, x + w, y + h, edge);
            if (w < 4 || y + h - top < 6) {
                d.fill(x + 1, top + 1, x + w - 1, y + h - 1, face);
                return;
            }
            int b = y + h - 1 - (lip - k);
            d.fill(x + 1, top + 1, x + w - 1, b, face);
            if (relief != Shapes.Relief.FLAT) {
                int lit = hardLight(face, 101);
                int soft = hardLight(face, 51);
                rims(d, x, top, w, b, lit, lit, soft, soft);
            }
            if (k < lip) {
                d.fill(x + 1, b, x + w - 1, y + h - 1, scale(face, 208F / 255F));
            }
            if (pressed && relief == Shapes.Relief.PRESSED && (color == p.primary() || color == p.primaryHover()) && w > 12) {
                int u = Math.max(4, w / 5);
                d.fill(x + (w - u) / 2, b - 1, x + (w - u) / 2 + u, b, p.primaryText());
            }
        }

        @Override
        public void button(Draw d, Palette p, int x, int y, int w, int h, ButtonComponent.Role role, int color,
                           Shapes.Relief relief) {
            int lip = lip(h);
            raised(d, p, x, y, w, h, color, relief, relief == Shapes.Relief.PRESSED ? lip : 0, lip);
        }

        @Override
        public void button(Draw d, Palette p, int x, int y, int w, int h, ButtonComponent.Role role, int color,
                           Shapes.Relief relief, int sunk) {
            raised(d, p, x, y, w, h, color, relief, sunk, lip(h));
        }

        @Override
        public int buttonCorner() {
            return 0;
        }

        /** The lip sits inside the box, so a button lines up with a field of the same height. */
        @Override
        public int buttonLip(int h) {
            return h - lip(h) >= 6 ? lip(h) : 0;
        }

        /** Tabs share their 1 px outline, as the wiki's do: each overlaps the next by a column. */
        @Override
        public int tabGap() {
            return -1;
        }

        @Override
        public int pressTravel() {
            return this.text ? TRAVEL : ICON_TRAVEL;
        }

        @Override
        public int pressShiftX() {
            return 0;
        }

        @Override
        public int pressMs() {
            return 100;
        }

        @Override
        public int selectedTravel() {
            return this.text ? TRAVEL : ICON_TRAVEL;
        }

        /** The green tab takes the primary label; the others pick a label that reads on the raised grey. */
        @Override
        public int tabText(Palette p, boolean selected, boolean active) {
            return !active ? p.disabledText() : selected ? p.primaryText() : Shapes.textFor(p, p.raisedText(), p.raised());
        }

        /** A chosen option turns green and sinks, with a white rule under its label. */
        @Override
        public int selectedFace(Palette p, float hover) {
            return hover >= 0.5F ? p.primaryHover() : p.primary();
        }

        @Override
        public Shapes.Relief selectedRelief() {
            return Shapes.Relief.PRESSED;
        }

        /** The empty slider bar: outline, a lit top row over the track grey; the fill runs over it. */
        @Override
        public void track(Draw d, Palette p, int x, int y, int w, int h, int color) {
            boolean off = color == p.disabled();
            int face = off ? p.disabled() : color;
            d.fill(x, y, x + w, y + h, off ? paleEdge(p) : p.frameDark());
            d.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
            if (h >= 6 && !off) {
                int b = y + h - 2;
                rims(d, x, y, w, b, face);
                d.fill(x + 1, b, x + w - 1, y + h - 1, Shapes.mix(face, 0xFF000000, 0.35F));
            }
        }

        /** The groove's own dark outline and track grey read on Ore UI panels: no rim. */
        @Override
        public void trackEdge(Draw d, Palette p, int x, int y, int w, int h, int color) {
        }

        /**
         * The four-sided rim of an Ore UI face between the outline at {@code y} and its bottom band at
         * {@code b}: top row and left column lit most, right column and the row over the band lit less.
         */
        void rims(Draw d, int x, int y, int w, int b, int color) {
            int rim = Shapes.mix(color, 0xFFFFFFFF, 0.12F);
            d.fill(x + 1, y + 1, x + w - 1, y + 2, Shapes.mix(color, 0xFFFFFFFF, 0.40F));
            d.fill(x + 1, y + 2, x + 2, b, Shapes.mix(color, 0xFFFFFFFF, 0.25F));
            d.fill(x + w - 2, y + 2, x + w - 1, b, rim);
            d.fill(x + 2, b - 1, x + w - 2, b, rim);
        }

        void rims(Draw d, int x, int y, int w, int b, int top, int left, int right, int low) {
            d.fill(x + 1, y + 1, x + w - 1, y + 2, top);
            d.fill(x + 1, y + 2, x + 2, b, left);
            d.fill(x + w - 2, y + 2, x + w - 1, b, right);
            d.fill(x + 2, b - 1, x + w - 2, b, low);
        }

        /**
         * The Ore UI slider bar, a row taller on each side of the groove: outline, then one segment per
         * step split by the outline (green up to the knob, grey after), each with a lit top row and left
         * column, its tone and a softer bottom row. Over 16 steps, or segments under 4 px, it stays whole.
         */
        @Override
        public void groove(Draw d, Palette p, int x, int y, int w, int h, int filled, int steps, boolean active) {
            int top = y - 1;
            int tall = h + 2;
            d.fill(x, top, x + w, top + tall, p.frameDark());
            int inner = w - 2;
            boolean notched = steps > 1 && steps <= 16 && inner / steps >= 4;
            int n = notched ? steps : 1;
            int end = x + Math.min(w - 1, Math.max(1, filled));
            for (int i = 0; i < n; i++) {
                int a = x + 1 + inner * i / n;
                int b = x + 1 + inner * (i + 1) / n - (notched && i + 1 < n ? 1 : 0);
                if (end > a) {
                    segment(d, a, top + 1, Math.min(b, end), top + tall - 1, active ? p.accent() : p.disabledText());
                }
                if (b > end) {
                    segment(d, Math.max(a, end), top + 1, b, top + tall - 1, active ? p.track() : p.disabled());
                }
            }
        }

        private void segment(Draw d, int x0, int y0, int x1, int y1, int face) {
            d.fill(x0, y0, x1, y1, face);
            d.fill(x0, y0, x1, y0 + 1, Shapes.mix(face, 0xFFFFFFFF, 0.25F));
            d.fill(x0, y0 + 1, x0 + 1, y1, Shapes.mix(face, 0xFFFFFFFF, 0.25F));
            if (y1 - y0 >= 3) {
                d.fill(x0 + 1, y1 - 1, x1, y1, Shapes.mix(face, 0xFFFFFFFF, 0.10F));
            }
        }

        @Override
        public void bar(Draw d, Palette p, int x, int y, int w, int h, float value, int color) {
            d.fill(x, y, x + w, y + h, p.frameDark());
            d.fill(x + 1, y + 1, x + w - 1, y + h - 1, p.track());
            int filled = Math.round(Maths.clamp(value, 0F, 1F) * (w - 2));
            if (filled > 0) {
                d.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, color);
            }
        }

        /** The slider handle: a grey button square, white under the mouse. */
        @Override
        public void knob(Draw d, Palette p, int x, int y, boolean lit) {
            raised(d, p, x, y, 7, 8, lit ? p.raisedHover() : p.raised(), Shapes.Relief.RAISED);
        }

        @Override
        public void toggleKnob(Draw d, Palette p, int x, int y, int face, boolean active) {
            toggleKnob(d, p, x, y, face, active, false);
        }

        /**
         * The switch's knob: a grey button square standing out of the track by a row above and a
         * column each side, framed in white under the mouse as the Ore UI toggle shows it.
         */
        @Override
        public void toggleKnob(Draw d, Palette p, int x, int y, int face, boolean active, boolean lit) {
            if (lit) {
                if (Shapes.contrast(0xFFFFFFFF, p.surface() | 0xFF000000) < 3) {
                    Shapes.ring(d, x - 3, y - 4, 14, 14, 0, p.frameDark());
                }
                d.fill(x - 2, y - 3, x + 10, y + 9, 0xFFFFFFFF);
            }
            raised(d, p, x - 1, y - 2, 10, 10, active ? p.raised() : p.disabled(),
                    active ? Shapes.Relief.RAISED : Shapes.Relief.FLAT);
            if (!active) {
                Shapes.ring(d, x - 1, y - 2, 10, 10, 0, paleEdge(p));
            }
        }

        /** toggle_state.png, disabled: every edge 0xFF8C8D90 round a 0xFFD0D1D4 face, a third of the way to black. */
        int paleEdge(Palette p) {
            return Shapes.mix(p.disabled(), 0xFF000000, 0.33F);
        }

        /** The vanilla slot: shaded top and left, lit bottom and right. */
        @Override
        public void slot(Draw d, Palette p, int x, int y, boolean hovered, boolean marked) {
            d.fill(x, y, x + 17, y + 17, p.wellEdge());
            d.fill(x + 1, y + 1, x + 18, y + 18, p.frameLight());
            d.fill(x + 1, y + 1, x + 17, y + 17, p.slotFace());
            if (hovered) {
                d.fill(x + 1, y + 1, x + 17, y + 17, Shapes.SLOT_HOVER_BACK);
            }
            if (marked) {
                Shapes.ring(d, x, y, 18, 18, 0, p.accent());
            }
        }

        /** Each channel of {@code c} times {@code k}, alpha kept. */
        private static int scale(int c, float k) {
            int out = c & 0xFF000000;
            for (int sh = 0; sh <= 16; sh += 8) {
                out |= Math.round((c >> sh & 0xFF) * k) << sh;
            }
            return out;
        }

        /** The wiki's hard-light overlay of grey {@code level} at 50 %, per channel: c + .5 * level * (255 - c) / 255. */
        private static int hardLight(int c, int level) {
            int out = c & 0xFF000000;
            for (int sh = 0; sh <= 16; sh += 8) {
                int v = c >> sh & 0xFF;
                out |= Math.min(255, Math.round(v + 0.5F * level * (255 - v) / 255F)) << sh;
            }
            return out;
        }

        /**
         * Bedrock's scroll bar: a 2 px dark rail down the middle, the thumb a full-width raised button over it.
         * Sizes and visibility stay with ScrollBar; this only paints the boxes it is given.
         */
        @Override
        public void scroller(Draw d, Palette p, int x, int y, int h, int thumbY, int thumbH, boolean lit) {
            boolean dark = Accent.dark(p);
            int rail = dark ? scale(FormLook.solid(p.track()), 0.7F) : 0xFF3C3C3C;
            d.fill(x + 2, y, x + 4, y + h, rail);
            // On a dark panel the button face can be the panel grey (Wiki) or smoke (glass): the thumb takes the soft ink.
            int face = dark ? FormLook.solid(lit ? p.text() : p.textSoft()) : lit ? p.raisedHover() : p.raised();
            raised(d, p, x, thumbY, ScrollComponent.BAR, thumbH, face, Shapes.Relief.RAISED);
        }

        /** The thumb fills the whole BAR, so no blank column is left on its right. */
        @Override
        public int scrollerGap() {
            return 0;
        }

        /** A ring {@code thickness} thick that follows the chamfered corners of {@link #shadow}. */
        @Override
        public void panelEdge(Draw d, Palette p, int x, int y, int w, int h, int thickness, int color) {
            if (w < 8 || h < 9) {
                FrameStyle.super.panelEdge(d, p, x, y, w, h, thickness, color);
                return;
            }
            for (int r = 0; r < h; r++) {
                int skip = r < 3 ? 3 - r : r >= h - 3 ? 3 - (h - 1 - r) : 0;
                if (r < thickness || r >= h - thickness) {
                    d.fill(x + skip, y + r, x + w - skip, y + r + 1, color);
                    continue;
                }
                int open = Math.min(skip + thickness, w / 2);
                d.fill(x + skip, y + r, x + open, y + r + 1, color);
                d.fill(x + w - open, y + r, x + w - skip, y + r + 1, color);
            }
        }
    }

    /**
     * The game's own container and button sprites, pixel for pixel: the panel's outline steps two
     * pixels in at each corner, the button wears a 1 px outline that turns white under the mouse.
     */
    public static final FrameStyle VANILLA = new FrameStyle() {
        @Override
        public int corner() {
            return 2;
        }

        /** As inventory.png draws it, top-right and bottom-left corners one pixel deeper than the others. */
        @Override
        public void panel(Draw d, Palette p, int x, int y, int w, int h) {
            if (art(d, p, GuiPart.PANEL, x, y, w, h)) {
                return;
            }
            int k = p.frameDark();
            d.fill(x + 2, y, x + w - 3, y + 1, k);
            d.fill(x + 1, y + 1, x + 2, y + 2, k);
            d.fill(x + w - 3, y + 1, x + w - 2, y + 2, k);
            d.fill(x, y + 2, x + 1, y + h - 3, k);
            d.fill(x + w - 2, y + 2, x + w - 1, y + 3, k);
            d.fill(x + w - 1, y + 3, x + w, y + h - 2, k);
            d.fill(x + 1, y + h - 3, x + 2, y + h - 2, k);
            d.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, k);
            d.fill(x + 2, y + h - 2, x + 3, y + h - 1, k);
            d.fill(x + 3, y + h - 1, x + w - 2, y + h, k);
            d.fill(x + 3, y + 3, x + w - 3, y + h - 3, p.frame());
            d.fill(x + w - 3, y + 2, x + w - 2, y + 3, p.frame());
            d.fill(x + 2, y + h - 3, x + 3, y + h - 2, p.frame());
            d.fill(x + 2, y + 1, x + w - 3, y + 2, p.frameLight());
            d.fill(x + 1, y + 2, x + w - 3, y + 3, p.frameLight());
            d.fill(x + 1, y + 3, x + 3, y + h - 3, p.frameLight());
            d.fill(x + 3, y + 3, x + 4, y + 4, p.frameLight());
            d.fill(x + w - 3, y + 3, x + w - 1, y + h - 3, p.trim());
            d.fill(x + w - 4, y + h - 4, x + w - 3, y + h - 3, p.trim());
            d.fill(x + 3, y + h - 3, x + w - 1, y + h - 2, p.trim());
            d.fill(x + 3, y + h - 2, x + w - 2, y + h - 1, p.trim());
            d.fill(x + 4, y + 4, x + w - 4, y + h - 4, p.surface());
        }

        /** The silhouette {@link #panel} outlines, row by row, as inventory.png steps its four corners. */
        @Override
        public void shadow(Draw d, Palette p, int x, int y, int w, int h, int color) {
            d.fill(x + 2, y, x + w - 3, y + 1, color);
            d.fill(x + 1, y + 1, x + w - 2, y + 2, color);
            d.fill(x, y + 2, x + w - 1, y + 3, color);
            d.fill(x, y + 3, x + w, y + h - 3, color);
            d.fill(x + 1, y + h - 3, x + w, y + h - 2, color);
            d.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, color);
            d.fill(x + 3, y + h - 1, x + w - 2, y + h, color);
        }

        /** The band between {@link #shadow}'s silhouette and the same silhouette {@code thickness} pixels in. */
        @Override
        public void panelEdge(Draw d, Palette p, int x, int y, int w, int h, int thickness, int color) {
            int iw = w - 2 * thickness;
            int ih = h - 2 * thickness;
            for (int row = 0; row < h; row++) {
                int left = silhouetteLeft(row, h);
                int right = silhouetteRight(row, h);
                int inner = row - thickness;
                if (iw <= 0 || ih <= 0 || inner < 0 || inner >= ih) {
                    d.fill(x + left, y + row, x + w - right, y + row + 1, color);
                    continue;
                }
                int openLeft = Math.max(left, thickness + silhouetteLeft(inner, ih));
                int openRight = Math.max(right, thickness + silhouetteRight(inner, ih));
                d.fill(x + left, y + row, x + openLeft, y + row + 1, color);
                d.fill(x + w - openRight, y + row, x + w - right, y + row + 1, color);
            }
        }

        /** How far {@link #shadow}'s row {@code row} of {@code h} starts in from the left. */
        private static int silhouetteLeft(int row, int h) {
            int fromBottom = h - 1 - row;
            return row < 3 ? 2 - row : fromBottom < 3 ? 3 - fromBottom : 0;
        }

        /** How far {@link #shadow}'s row {@code row} of {@code h} ends in from the right. */
        private static int silhouetteRight(int row, int h) {
            int fromBottom = h - 1 - row;
            return row < 3 ? 3 - row : fromBottom < 3 ? 2 - fromBottom : 0;
        }

        @Override
        public int buttonCorner() {
            return 0;
        }

        @Override
        public void header(Draw d, Palette p, int x, int y, int w, int h) {
            d.fill(x + 2, y + h - 1, x + w - 2, y + h, p.surfaceEdge());
        }

        @Override
        public void section(Draw d, Palette p, int x, int y, int w, int h) {
            if (!art(d, p, GuiPart.SECTION, x, y, w, h)) {
                d.fill(x, y, x + w, y + h, p.section());
            }
            d.fill(x, y, x + w, y + 1, p.surfaceEdge());
        }

        /** The inventory.png slot sprite on a slot-sized box; a larger well fills p.well() inside the same bevel. */
        @Override
        public void well(Draw d, Palette p, int x, int y, int w, int h, boolean focused) {
            if (Math.abs(w - 18) <= 2 && Math.abs(h - 18) <= 2 && art(d, p, GuiPart.WELL, x, y, w, h)) {
                if (focused) {
                    Shapes.ring(d, x, y, w, h, 0, p.accent());
                }
                return;
            }
            d.fill(x, y, x + w, y + h, focused ? p.accent() : p.wellEdge());
            d.fill(x + 1, y + 1, x + w, y + h, p.frameLight());
            d.fill(x + 1, y + 1, x + w - 1, y + h - 1, p.well());
            if (focused) {
                Shapes.ring(d, x, y, w, h, 0, p.accent());
            }
        }

        /**
         * button.png: outline, lit top row and left column, two shaded rows at the bottom. As
         * button_highlighted.png, hover turns only the outline white: a {@code color} on the way from a
         * rest face to its hover face keeps the rest face.
         */
        @Override
        public void raised(Draw d, Palette p, int x, int y, int w, int h, int color, Shapes.Relief relief) {
            float hover = Math.max(0F, hover(p, color));
            if (relief == Shapes.Relief.RAISED) {
                GuiPart part = color == p.disabled() ? GuiPart.BUTTON_DISABLED
                        : along(color, p.raised(), p.raisedHover()) < 0F ? null
                        : hover >= 0.5F ? GuiPart.BUTTON_HIGHLIGHTED : GuiPart.BUTTON;
                if (part != null && art(d, p, part, x, y, w, h)) {
                    return;
                }
            }
            outlined(d, x, y, w, h, restFace(p, color),
                    Shapes.mix(p.frameDark(), 0xFFFFFFFF, relief == Shapes.Relief.FLAT ? 0F : hover), relief);
        }

        /** How far {@code color} is from a rest face to its hover face, -1 when it lies on no such way. */
        private float hover(Palette p, int color) {
            float t = along(color, p.raised(), p.raisedHover());
            if (t < 0F) {
                t = along(color, p.primary(), p.primaryHover());
            }
            return t < 0F ? along(color, p.destructive(), p.destructiveHover()) : t;
        }

        private int restFace(Palette p, int color) {
            if (along(color, p.raised(), p.raisedHover()) >= 0F) {
                return p.raised();
            }
            if (along(color, p.primary(), p.primaryHover()) >= 0F) {
                return p.primary();
            }
            return along(color, p.destructive(), p.destructiveHover()) >= 0F ? p.destructive() : color;
        }

        private float along(int color, int from, int to) {
            if (color >>> 24 != from >>> 24 || from >>> 24 != to >>> 24) {
                return -1F;
            }
            int channel = 0;
            int span = 0;
            for (int s = 0; s <= 16; s += 8) {
                int step = (to >> s & 0xFF) - (from >> s & 0xFF);
                if (Math.abs(step) > Math.abs(span)) {
                    span = step;
                    channel = s;
                }
            }
            if (span == 0) {
                return color == from ? 0F : -1F;
            }
            float t = Maths.clamp(((color >> channel & 0xFF) - (from >> channel & 0xFF)) / (float) span, 0F, 1F);
            int on = Shapes.mix(from, to, t);
            for (int s = 0; s <= 16; s += 8) {
                if (Math.abs((on >> s & 0xFF) - (color >> s & 0xFF)) > 2) {
                    return -1F;
                }
            }
            return t;
        }

        @Override
        public void button(Draw d, Palette p, int x, int y, int w, int h, ButtonComponent.Role role, int color,
                           Shapes.Relief relief) {
            if (role == ButtonComponent.Role.HERO) {
                float hover = Math.max(0F, hover(p, color));
                outlined(d, x, y, w, h, restFace(p, color), Shapes.mix(p.accent(), 0xFFFFFFFF, hover), relief);
            } else {
                raised(d, p, x, y, w, h, color, relief);
            }
        }

        private void outlined(Draw d, int x, int y, int w, int h, int color, int outline, Shapes.Relief relief) {
            d.fill(x, y, x + w, y + h, outline);
            d.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
            if (relief == Shapes.Relief.FLAT || w < 4 || h < 5) {
                return;
            }
            int lit = Shapes.mix(color, 0xFFFFFFFF, 0.40F);
            int shade = Shapes.mix(color, 0xFF000000, 0.22F);
            int top = relief == Shapes.Relief.PRESSED ? shade : lit;
            int bottom = relief == Shapes.Relief.PRESSED ? lit : shade;
            d.fill(x + 1, y + 1, x + w - 2, y + 2, top);
            d.fill(x + 1, y + 2, x + 2, y + h - 3, top);
            d.fill(x + 2, y + h - 3, x + w - 1, y + h - 1, bottom);
        }

        /** The selected tab holds the hover face, so its white label keeps 4.5:1 on light. */
        @Override
        public int selectedFace(Palette p, float hover) {
            return p.raisedHover();
        }

        /**
         * text_field.png on a light chroma: black fill, 0xFFA0A0A0 border, white when focused. A dark
         * chroma keeps the sunk well, as approved for the dark variant.
         */
        @Override
        public void field(Draw d, Palette p, int x, int y, int w, int h, boolean focused) {
            if (!light(p)) {
                well(d, p, x, y, w, h, focused);
                return;
            }
            if (d.guiPart(focused ? GuiPart.TEXT_FIELD_HIGHLIGHTED : GuiPart.TEXT_FIELD, x, y, w, h, 1F)) {
                return;
            }
            d.fill(x, y, x + w, y + h, focused ? 0xFFFFFFFF : 0xFFA0A0A0);
            d.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF000000);
        }

        @Override
        public int fieldText(Palette p) {
            return light(p) ? 0xFFE0E0E0 : p.text();
        }

        @Override
        public int fieldHint(Palette p) {
            return light(p) ? 0xFF808080 : p.textFaint();
        }

        private boolean light(Palette p) {
            return Shapes.luminance(p.surface()) > 0.3;
        }

        /** The light variant takes the game's own sprite where the renderer has it; the dark one keeps its palette. */
        private boolean art(Draw d, Palette p, GuiPart part, int x, int y, int w, int h) {
            return light(p) && d.guiPart(part, x, y, w, h, 1F);
        }

        @Override
        public boolean gameWidgets() {
            return true;
        }

        /** Ticked rows of checkbox_selected.png from row 7, column 4: '#' the tick, '+' and '-' its antialias. */
        private static final String[] TICK = {
                "...........##", ".........###", "........##+", "#+.....##-", ".##+..##-", "..##+##+", "...###+",
                "....##-", ".....+"};

        /**
         * The outline of a checkbox, radio or slider well: the frame's dark, as the sprites draw it; in glass the
         * dark well and outline sink into the smoke, so the faint ink draws it (the disabled ink when inactive).
         */
        private int wellOutline(Palette p, boolean active) {
            return !GlassPalette.isGlass(p) ? p.frameDark() : active ? p.textFaint() : p.disabledText();
        }

        /** checkbox.png: a 1 px outline, white under the mouse, round a dark well with a white tick. */
        @Override
        public void checkbox(Draw d, Palette p, int x, int y, int side, boolean on, boolean lit, boolean active) {
            GuiPart part = on ? lit ? GuiPart.CHECKBOX_SELECTED_HIGHLIGHTED : GuiPart.CHECKBOX_SELECTED
                    : lit ? GuiPart.CHECKBOX_HIGHLIGHTED : GuiPart.CHECKBOX;
            if (active && art(d, p, part, x, y, side, side)) {
                return;
            }
            int back = active ? p.track() : p.disabled();
            d.fill(x, y, x + side, y + side, lit ? 0xFFFFFFFF : wellOutline(p, active));
            d.fill(x + 1, y + 1, x + side - 1, y + side - 1, back);
            if (!on) {
                return;
            }
            int ink = active ? p.raisedText() : p.disabledText();
            for (int row = 0; row < TICK.length; row++) {
                String line = TICK[row];
                for (int col = 0; col < line.length(); col++) {
                    char c = line.charAt(col);
                    if (c != '.') {
                        int px0 = x + (4 + col) * side / 20;
                        int py0 = y + (7 + row) * side / 20;
                        int px1 = Math.max(px0 + 1, x + (5 + col) * side / 20);
                        int py1 = Math.max(py0 + 1, y + (8 + row) * side / 20);
                        d.fill(px0, py0, px1, py1, c == '#' ? ink : Shapes.mix(back, ink, c == '+' ? 0.75F : 0.43F));
                    }
                }
            }
        }

        /** The game has no radio: the round mark takes checkbox.png's outline, well and white mark; only the chosen one gets the dark well, a dark disc on the light panel reads as chosen. */
        @Override
        public void radio(Draw d, Palette p, int x, int y, int side, boolean on, boolean lit, boolean active) {
            int cell = Math.max(1, side / FormLook.RADIO_ART);
            FormLook.pixels(d, FormLook.RADIO_INSIDE, x, y, cell, !active ? p.disabled() : on ? p.track() : p.well());
            FormLook.pixels(d, FormLook.RADIO_RING, x, y, cell, lit && active ? 0xFFFFFFFF : wellOutline(p, active));
            if (on) {
                FormLook.pixels(d, FormLook.RADIO_DOT, x + 2 * cell, y + 2 * cell, cell, active ? p.raisedText() : p.disabledText());
            }
        }

        /** slider.png: the checkbox's outline and well, stretched over the box. */
        @Override
        public void sliderBar(Draw d, Palette p, int x, int y, int w, int h, boolean lit, boolean active) {
            if (active && art(d, p, lit ? GuiPart.SLIDER_HIGHLIGHTED : GuiPart.SLIDER, x, y, w, h)) {
                return;
            }
            d.fill(x, y, x + w, y + h, lit ? 0xFFFFFFFF : wellOutline(p, active));
            d.fill(x + 1, y + 1, x + w - 1, y + h - 1, active ? p.track() : p.disabled());
        }

        /** slider_handle.png: a button face 8 wide, its outline white under the mouse. */
        @Override
        public void sliderHandle(Draw d, Palette p, int x, int y, int h, boolean lit) {
            if (art(d, p, lit ? GuiPart.SLIDER_HANDLE_HIGHLIGHTED : GuiPart.SLIDER_HANDLE, x, y, 8, h)) {
                return;
            }
            outlined(d, x, y, 8, h, p.raised(), lit ? 0xFFFFFFFF : p.frameDark(), Shapes.Relief.RAISED);
        }

        /**
         * tab_selected.png: an outline at 0xBF of the frame's dark and a rim at 0x33 white (white under
         * the mouse), hollow and open at the bottom. The others start lower as tab.png does, and wear the
         * button face, since tab.png's 0xDB black only reads as grey over the game's dark menu backdrop.
         */
        @Override
        public void tab(Draw d, Palette p, int x, int y, int w, int h, boolean selected, boolean lit) {
            GuiPart part = selected ? lit ? GuiPart.TAB_SELECTED_HIGHLIGHTED : GuiPart.TAB_SELECTED
                    : lit ? GuiPart.TAB_HIGHLIGHTED : GuiPart.TAB;
            if (art(d, p, part, x, y, w, h)) {
                return;
            }
            int edge = Shapes.alpha(p.frameDark(), 0xBF);
            int faint = Shapes.alpha(0xFFFFFFFF, 0x33);
            int rim = lit ? 0xFFFFFFFF : faint;
            int r = x + w;
            int b = y + h;
            if (selected) {
                d.fill(x, y, r, y + 1, edge);
                d.fill(x, y + 1, x + 1, b - 2, edge);
                d.fill(r - 1, y + 1, r, b - 2, edge);
                d.fill(x + 1, y + 1, r - 1, y + 2, rim);
                d.fill(x + 1, y + 2, x + 2, b - 1, rim);
                d.fill(r - 2, y + 2, r - 1, b - 1, rim);
                d.fill(x, b - 2, x + 1, b - 1, faint);
                d.fill(r - 1, b - 2, r, b - 1, faint);
                d.fill(x, b - 1, x + 2, b, edge);
                d.fill(r - 2, b - 1, r, b, edge);
                return;
            }
            int top = y + tabInset(h, false);
            outlined(d, x, top, w, b - 2 - top, p.raised(), lit ? 0xFFFFFFFF : p.frameDark(), Shapes.Relief.RAISED);
            tabRule(d, p, x, y, w, h);
        }

        /** The game's tabs are 24 px tall. */
        @Override
        public int tabHeight(int base) {
            return 24;
        }

        /**
         * tab.png starts 4 of its 24 rows lower than tab_selected.png. A shorter box gives up that step
         * first, keeping the 9 rows between the rims an 8 px label needs.
         */
        @Override
        public int tabInset(int h, boolean selected) {
            return selected ? 0 : Maths.clamp(h - 13, 0, 4);
        }

        /** The two bottom rows of tab.png: 0x33 white over the outline. */
        @Override
        public void tabRule(Draw d, Palette p, int x, int y, int w, int h) {
            d.fill(x, y + h - 2, x + w, y + h - 1, Shapes.alpha(0xFFFFFFFF, 0x33));
            d.fill(x, y + h - 1, x + w, y + h, Shapes.alpha(p.frameDark(), 0xBF));
        }

        /** The selected tab shows the page's surface, so its label takes the page's text colour. */
        @Override
        public int tabText(Palette p, boolean selected, boolean active) {
            return !active ? p.disabledText() : selected ? p.text() : p.raisedText();
        }

        /**
         * scroller_background.png and scroller.png: a dark trough 6 wide, and a thumb filling it with the
         * game's 0xC0 face, a white rim on its top and left and 0x80 shade on its right and bottom.
         */
        @Override
        public int scrollerGap() {
            return 0;
        }

        @Override
        public void scroller(Draw d, Palette p, int x, int y, int h, int thumbY, int thumbH, boolean lit) {
            if (art(d, p, GuiPart.SCROLLER_BACKGROUND, x, y, 6, h)) {
                d.guiPart(GuiPart.SCROLLER, x, thumbY, 6, thumbH, 1F);
                return;
            }
            d.fill(x, y, x + 6, y + h, p.frameDark());
            int shade = 0xFF808080;
            d.fill(x, thumbY, x + 6, thumbY + thumbH, lit ? 0xFFE0E0E0 : 0xFFC0C0C0);
            d.fill(x, thumbY, x + 6, thumbY + 1, 0xFFFFFFFF);
            d.fill(x, thumbY + 1, x + 1, thumbY + thumbH, 0xFFFFFFFF);
            d.fill(x + 5, thumbY + 1, x + 6, thumbY + thumbH, shade);
            d.fill(x + 1, thumbY + thumbH - 1, x + 6, thumbY + thumbH, shade);
        }

        /** hotbar.png, the HUD bar; off the art, the family's slot wells at the same 20 px pitch. */
        @Override
        public void hotbar(Draw d, Palette p, int x, int y) {
            if (art(d, p, GuiPart.HOTBAR, x, y, 182, 22)) {
                return;
            }
            FrameStyle.super.hotbar(d, p, x, y);
        }

        /** hotbar_selection.png's rings, black then light, mid and dark green-grey, around the 16 px item. */
        @Override
        public void hotbarSelection(Draw d, Palette p, int x, int y) {
            if (art(d, p, GuiPart.HOTBAR_SELECTION, x - 3, y - 3, 24, 23)) {
                return;
            }
            Shapes.ring(d, x - 3, y - 3, 24, 24, 0, 0xFF000000);
            Shapes.ring(d, x - 2, y - 2, 22, 22, 0, 0xFFF7F9F7);
            Shapes.ring(d, x - 1, y - 1, 20, 20, 0, 0xFFA1B29D);
            Shapes.ring(d, x, y, 18, 18, 0, 0xFF5F6D5C);
        }

        /** slot.png, and the game's slot_highlight_back at 0x60 white over the item area. */
        @Override
        public void slot(Draw d, Palette p, int x, int y, boolean hovered, boolean marked) {
            if (!art(d, p, GuiPart.SLOT, x, y, 18, 18)) {
                d.fill(x, y, x + 18, y + 18, p.slotFace());
                d.fill(x, y, x + 17, y + 1, p.wellEdge());
                d.fill(x, y + 1, x + 1, y + 17, p.wellEdge());
                d.fill(x + 1, y + 17, x + 18, y + 18, p.frameLight());
                d.fill(x + 17, y + 1, x + 18, y + 17, p.frameLight());
            }
            if (hovered) {
                d.fill(x + 1, y + 1, x + 17, y + 17, Shapes.SLOT_HOVER_BACK);
            }
            if (marked) {
                Shapes.ring(d, x, y, 18, 18, 0, p.accent());
            }
        }
    };

    /**
     * Neumorphism: every shape is cut from the surface's own colour, raised by a light rim at the
     * top left and a dark rim at the bottom right, sunk by the same rims swapped.
     */
    public static final FrameStyle SOFT = new FrameStyle() {
        @Override
        public Insets panelInsets() {
            return new Insets(2, 2, 2, 2);
        }

        @Override
        public int panelShadow() {
            return 2;
        }

        /** A chosen option takes the primary and sinks, as on Bedrock: a darker plate alone reads as a shade of rest. */
        @Override
        public int selectedFace(Palette p, float hover) {
            return Shapes.mix(p.primary() | 0xFF000000, p.primaryHover() | 0xFF000000, hover);
        }

        @Override
        public Shapes.Relief selectedRelief() {
            return Shapes.Relief.PRESSED;
        }

        @Override
        public int corner() {
            return 5;
        }

        private static void rims(Draw d, Palette p, int x, int y, int w, int h, int r, int face, boolean raised, int t) {
            int lit = lit(p);
            Shapes.rounded(d, x, y, w - t, h - t, r, raised ? lit : p.frameDark());
            Shapes.rounded(d, x + t, y + t, w - t, h - t, r, raised ? p.frameDark() : lit);
            Shapes.rounded(d, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), face);
        }

        /**
         * The lit rim, or the surface edge when the lit rim would vanish into the surface (white on
         * white in a light chroma): then the top and left edges still read as a 1 px line.
         */
        /** WCAG contrast steps of the well's relief: face to shade, face to light, inner row to outer rim. */
        private static final double SHADE_STEP = 1.25;
        private static final double LIT_STEP = 1.15;
        private static final double RIM_STEP = 1.12;

        /** {@code c} mixed toward white (up) or black until it reaches {@code ratio} against {@code c}, or the end colour. */
        private static int step(int c, double ratio, boolean up) {
            int end = up ? 0xFFFFFFFF : 0xFF000000;
            for (int i = 1; i <= 64; i++) {
                int m = Shapes.mix(c, end, i / 64F);
                if (Shapes.contrast(m, c) >= ratio) {
                    return m;
                }
            }
            return end;
        }

        private static int lit(Palette p) {
            return Shapes.contrast(p.frameLight(), p.surface()) < 1.1 ? p.surfaceEdge() : p.frameLight();
        }

        /** A flat face inside the palette's 1 px line, for a chroma that sets {@link Palette#line()}. */
        private static void lined(Draw d, int line, int x, int y, int w, int h, int r, int face) {
            Shapes.rounded(d, x, y, w, h, r, line);
            Shapes.rounded(d, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), face);
        }

        @Override
        public void panel(Draw d, Palette p, int x, int y, int w, int h) {
            Shapes.rounded(d, x + 1, y + 2, w, h, 5, p.shadow());
            if (p.line() != 0) {
                lined(d, p.line(), x, y, w, h, 5, p.surface());
                return;
            }
            if (Accent.dark(p) || w < 12 || h < 12) {
                rims(d, p, x, y, w, h, 5, p.surface(), true, 2);
                return;
            }
            // On a light chroma the lit top and left rims melt into a light page: a 1 px frame in the
            // palette's surface edge keeps the panel's outline, the relief sits inside it.
            Shapes.rounded(d, x, y, w, h, 5, p.surfaceEdge());
            rims(d, p, x + 1, y + 1, w - 2, h - 2, 4, p.surface(), true, 2);
        }

        @Override
        public void header(Draw d, Palette p, int x, int y, int w, int h) {
            if (p.line() != 0) {
                d.fill(x + 3, y + h - 1, x + w - 3, y + h, p.line());
                return;
            }
            d.fill(x + 3, y + h - 1, x + w - 3, y + h, p.frameDark());
            d.fill(x + 3, y + h, x + w - 3, y + h + 1, p.frameLight());
        }

        @Override
        public void section(Draw d, Palette p, int x, int y, int w, int h) {
            if (p.line() != 0) {
                lined(d, p.line(), x, y, w, h, 3, p.section());
                return;
            }
            rims(d, p, x, y, w, h, 3, p.section(), true, 1);
        }

        @Override
        public void well(Draw d, Palette p, int x, int y, int w, int h, boolean focused) {
            // The face, its shade and its light each sit a fixed contrast step apart, derived from p.well(): where
            // the palette ends (a black dark panel, a white light one) the face moves away from the end instead,
            // so the top and left always read darker than the face and the bottom and right lighter.
            if (p.line() != 0) {
                lined(d, focused ? p.accent() : p.line(), x, y, w, h, 3, p.well());
                return;
            }
            int face = p.well() | 0xFF000000;
            int lit = step(face, LIT_STEP, true);
            if (Shapes.contrast(lit, face) < LIT_STEP) {
                face = step(lit, LIT_STEP, false);
            }
            int shade = step(face, SHADE_STEP, false);
            if (Shapes.contrast(shade, face) < SHADE_STEP) {
                face = step(shade, SHADE_STEP, true);
                lit = step(face, LIT_STEP, true);
            }
            int deep = step(shade, RIM_STEP, false);
            int glow = step(lit, RIM_STEP, true);
            Shapes.rounded(d, x, y, w - 1, h - 1, 3, deep);
            Shapes.rounded(d, x + 1, y + 1, w - 1, h - 1, 3, glow);
            Shapes.rounded(d, x + 1, y + 1, w - 2, h - 2, 2, face);
            if (w >= 8 && h >= 8) {
                d.fill(x + 2, y + 1, x + w - 2, y + 2, shade);
                d.fill(x + 1, y + 2, x + 2, y + h - 2, shade);
                d.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, lit);
                d.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, lit);
            }
            if (focused) {
                d.fill(x + 3, y + h - 1, x + w - 3, y + h, p.accent());
            }
        }

        @Override
        public void raised(Draw d, Palette p, int x, int y, int w, int h, int color, Shapes.Relief relief) {
            if (relief == Shapes.Relief.FLAT) {
                Shapes.rounded(d, x, y, w, h, 3, color);
            } else if (p.line() != 0) {
                lined(d, p.line(), x, y, w, h, 3, color);
            } else {
                rims(d, p, x, y, w, h, 3, color, relief != Shapes.Relief.PRESSED, 1);
            }
            // The chosen option's mark, as on Bedrock: a rule in the label's ink under the label.
            if (relief == Shapes.Relief.PRESSED && (color == selectedFace(p, 0F) || color == selectedFace(p, 1F)) && w > 12 && h > 8) {
                int u = Math.max(4, w / 5);
                d.fill(x + (w - u) / 2, y + h - 3, x + (w - u) / 2 + u, y + h - 2, Shapes.textOn(p, color));
            }
        }

        @Override
        public void button(Draw d, Palette p, int x, int y, int w, int h, ButtonComponent.Role role, int color,
                           Shapes.Relief relief) {
            if (role == ButtonComponent.Role.SECONDARY && p.line() != 0) {
                // The chroma's own steps: rest and hover come in as color, pressed takes the hover step.
                lined(d, p.line(), x, y, w, h, 3, relief == Shapes.Relief.PRESSED ? p.raisedHover() : color);
                return;
            }
            if (role == ButtonComponent.Role.SECONDARY) {
                int surface = p.surface();
                int lum = ((surface >> 16 & 0xFF) * 299 + (surface >> 8 & 0xFF) * 587 + (surface & 0xFF) * 114) / 1000;
                int toward = lum >= 128 ? 0xFF000000 : 0xFFFFFFFF;
                int span = Math.abs((p.raisedHover() & 0xFF) - (p.raised() & 0xFF))
                        + Math.abs((p.raisedHover() >> 8 & 0xFF) - (p.raised() >> 8 & 0xFF))
                        + Math.abs((p.raisedHover() >> 16 & 0xFF) - (p.raised() >> 16 & 0xFF));
                int moved = Math.abs((color & 0xFF) - (p.raised() & 0xFF))
                        + Math.abs((color >> 8 & 0xFF) - (p.raised() >> 8 & 0xFF))
                        + Math.abs((color >> 16 & 0xFF) - (p.raised() >> 16 & 0xFF));
                float hover = span == 0 ? 0F : Math.min(1F, moved / (float) span);
                float step = relief == Shapes.Relief.PRESSED ? 0F : 0.06F + 0.05F * hover;
                Shapes.rounded(d, x, y, w, h, 3, Shapes.mix(surface | 0xFF000000, toward, step));
                return;
            }
            if (role != ButtonComponent.Role.HERO) {
                FrameStyle.super.button(d, p, x, y, w, h, role, color, relief);
                return;
            }
            // The hero keeps the neutral plate's relief, its rims cut from its own fill so no outline shows.
            boolean raised = relief != Shapes.Relief.PRESSED;
            int light = Shapes.mix(color, 0xFFFFFFFF, 0.40F);
            int dark = Shapes.mix(color, 0xFF000000, 0.30F);
            Shapes.rounded(d, x, y, w - 1, h - 1, 3, raised ? light : dark);
            Shapes.rounded(d, x + 1, y + 1, w - 1, h - 1, 3, raised ? dark : light);
            Shapes.rounded(d, x + 1, y + 1, w - 2, h - 2, 2, color);
        }

        @Override
        public void bar(Draw d, Palette p, int x, int y, int w, int h, float value, int color) {
            if (p.line() != 0) {
                lined(d, p.line(), x, y, w, h, Math.min(3, h / 2), p.track());
            } else {
                rims(d, p, x, y, w, h, Math.min(3, h / 2), p.track(), false, 1);
            }
            int filled = Math.round(Maths.clamp(value, 0F, 1F) * (w - 2));
            if (filled > 0) {
                Shapes.rounded(d, x + 1, y + 1, pillWidth(w, h, filled), h - 2, Math.min(2, h / 2 - 1), color);
            }
        }

        @Override
        public void knob(Draw d, Palette p, int x, int y, boolean lit) {
            rims(d, p, x, y, 7, 8, 3, lit ? p.primaryHover() : p.primary(), true, 1);
        }

        @Override
        public void track(Draw d, Palette p, int x, int y, int w, int h, int color) {
            if (p.line() != 0) {
                lined(d, p.line(), x, y, w, h, h / 2, color);
                return;
            }
            rims(d, p, x, y, w, h, h / 2, color, false, 1);
        }

        @Override
        public void toggleKnob(Draw d, Palette p, int x, int y, int face, boolean active) {
            if (active) {
                rims(d, p, x, y, 8, 8, 4, p.raised(), true, 1);
            } else {
                Shapes.rounded(d, x, y, 8, 8, 4, face);
            }
        }
    };

    /**
     * Clay: every shape is a soft blob whose relief is traced on its own pixel mask inset by 1 px.
     * The rows of the mask that face up are lit, the rows that face down are shaded, both two rows deep
     * on tall shapes; a sunk shape swaps them.
     */
    public static final FrameStyle BUBBLY = new FrameStyle() {
        @Override
        public Insets panelInsets() {
            return new Insets(3, 3, 3, 4);
        }

        @Override
        public int panelShadow() {
            return 2;
        }

        @Override
        public int corner() {
            return 4;
        }

        private static final float LIT = 0.55F;

        /**
         * The shape's pixel mask, one span per row: row {@code row} of a {@code w} by {@code h} blob of radius {@code r}
         * covers columns {@code inset} to {@code w - inset}. Edges and fills are drawn one span at a time, so a frame
         * costs a few fills per row near the corners instead of one per pixel.
         */
        private static int inset(int h, int r, int row) {
            if (row >= r && row < h - r) {
                return 0;
            }
            double dy = r - (row < r ? row : h - 1 - row) - 0.5;
            return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
        }

        private static int radius(int w, int h, int r) {
            return Math.max(0, Math.min(r, Math.min(w, h) / 2));
        }

        /** The mask pixels whose neighbour {@code depth} rows away in direction {@code dy} is outside, minus the shallower rows. */
        private static void edge(Draw d, int x, int y, int w, int h, int r, int dy, int depth, int color) {
            r = radius(w, h, r);
            for (int row = 0; row < h; row++) {
                int lo = inset(h, r, row);
                int hi = w - lo;
                if (depth > 1) {
                    int near = row + dy * (depth - 1);
                    if (near < 0 || near >= h) {
                        continue;
                    }
                    int in = inset(h, r, near);
                    lo = Math.max(lo, in);
                    hi = Math.min(hi, w - in);
                }
                if (lo >= hi) {
                    continue;
                }
                int far = row + dy * depth;
                int a = far >= 0 && far < h ? inset(h, r, far) : w;
                int b = w - a;
                if (a >= b) {
                    d.fill(x + lo, y + row, x + hi, y + row + 1, color);
                } else {
                    d.fill(x + lo, y + row, x + Math.min(hi, a), y + row + 1, color);
                    d.fill(x + Math.max(lo, b), y + row, x + hi, y + row + 1, color);
                }
            }
        }

        /**
         * A clay blob drawn with layer indices {@code c} to {@code c + 5} as its colours: shadow, face, first lit row,
         * first shaded row, second lit row, second shaded row, in the order they overlap.
         */
        private static void clay(Draw d, int x, int y, int w, int h, int r, boolean shadow, int relief, int c) {
            if (relief > 0 && shadow) {
                Shapes.rounded(d, x + 1, y + 1, w, h, r, c);
            }
            Shapes.rounded(d, x, y, w, h, r, c + 1);
            if (relief == 0 || w < 4 || h < 4) {
                return;
            }
            int mw = w - 2;
            int mh = h - 2;
            int mr = Math.max(0, r - 1);
            int up = relief > 0 ? -1 : 1;
            edge(d, x + 1, y + 1, mw, mh, mr, up, 1, c + 2);
            edge(d, x + 1, y + 1, mw, mh, mr, -up, 1, c + 3);
            if (h >= 10) {
                edge(d, x + 1, y + 1, mw, mh, mr, up, 2, c + 4);
                edge(d, x + 1, y + 1, mw, mh, mr, -up, 2, c + 5);
            }
        }

        private static void clayColors(int[] c, int at, int face, int shadow, int relief, float lit) {
            clayColors(c, at, face, shadow, relief, lit, 0.4F, SUNK_SHADE);
        }

        /** {@code sunkLit} and {@code sunkShade} are the light on the bottom edge and the shade on the top edge of a sunk shape. */
        private static void clayColors(int[] c, int at, int face, int shadow, int relief, float lit, float sunkLit, float sunkShade) {
            c[at] = shadow;
            c[at + 1] = face;
            c[at + 2] = Shapes.mix(face, 0xFFFFFFFF, relief > 0 ? lit : sunkLit);
            c[at + 3] = Shapes.mix(face, 0xFF000000, relief > 0 ? 0.12F : sunkShade);
            c[at + 4] = Shapes.mix(face, 0xFFFFFFFF, (relief > 0 ? lit : sunkLit) / 10);
            c[at + 5] = Shapes.mix(face, 0xFF000000, relief > 0 ? 0.06F : sunkShade / 2);
        }

        /**
         * The shade on the top edge of a sunk shape. A well or a panel surface gets no bottom light: on a dark face
         * it reads as a white line across the box.
         */
        private static final float SUNK_SHADE = 0.14F;

        /** A recessed well for a bar or a track: the bottom light is as faint as a second row, so it reads as an edge, not a line. */
        private static final float TRACK_LIT = 0.03F;
        private static final float TRACK_SHADE = 0.07F;

        /**
         * A fill pill whose relief is traced on its own full mask, layers {@code c} to {@code c + 4}: the face, the top
         * edge lit, the bottom edge shaded, then their second rows; the ends follow the curve.
         */
        private static void pill(Draw d, int x, int y, int w, int h, int r, int c) {
            int mr = radius(w, h, r);
            for (int row = 0; row < h; row++) {
                int in = inset(h, mr, row);
                d.fill(x + in, y + row, x + w - in, y + row + 1, c);
            }
            edge(d, x, y, w, h, r, -1, 1, c + 1);
            edge(d, x, y, w, h, r, 1, 1, c + 2);
            if (h >= 10) {
                edge(d, x, y, w, h, r, -1, 2, c + 3);
                edge(d, x, y, w, h, r, 1, 2, c + 4);
            }
        }

        private static float luminance(int c) {
            return (0.2126F * (c >> 16 & 0xFF) + 0.7152F * (c >> 8 & 0xFF) + 0.0722F * (c & 0xFF)) / 255F;
        }

        // Each shape below goes through FillFold: its geometry folded once per size, its colours set each frame.
        private static final int RAISED = 0;
        private static final int PANEL = 1;
        private static final int WELL = 2;
        private static final int BAR = 3;
        private static final int TRACK = 4;
        private static final int TOGGLE = 5;
        private static final int PLATE = 6;
        private static final int DOT = 7;

        /** extra: the relief plus one. A button, a header, a section, a knob. */
        private static final FillFold.Geometry RAISED_SHAPE = (d, w, h, e) -> clay(d, 0, 0, w, h, 3, true, e - 1, 0);

        /** extra: 1 when the frame casts its shadow. */
        private static final FillFold.Geometry PANEL_SHAPE = (d, w, h, e) -> {
            Shapes.rounded(d, 1, 2, w, h, 4, 0);
            clay(d, 0, 0, w, h, 4, e != 0, 1, 1);
            clay(d, 3, 3, w - 6, h - 7, 3, false, -1, 7);
        };

        private static final FillFold.Geometry WELL_SHAPE = (d, w, h, e) -> {
            Shapes.rounded(d, 0, 0, w, h, 3, 0);
            clay(d, 1, 1, w - 2, h - 2, 2, false, -1, 1);
        };

        /** extra: the fill pill's width, 0 for none. */
        private static final FillFold.Geometry BAR_SHAPE = (d, w, h, e) -> {
            Shapes.rounded(d, 0, 0, w, h, 3, 0);
            clay(d, 1, 1, w - 2, h - 2, 2, false, -1, 1);
            if (e > 0) {
                pill(d, 1, 1, e, h - 2, Math.max(0, Math.min(3, h / 2) - 1), 7);
            }
        };

        private static final FillFold.Geometry TRACK_SHAPE = (d, w, h, e) -> clay(d, 0, 0, w, h, h / 2, false, -1, 0);

        /** extra: the relief, 1 or 0. */
        private static final FillFold.Geometry TOGGLE_SHAPE = (d, w, h, e) -> clay(d, 0, 0, w, h, 4, true, e, 0);

        /** extra: the corner times 4, plus 1 for a fill and 2 for an edge. */
        private static final FillFold.Geometry PLATE_SHAPE = (d, w, h, e) -> {
            int r = e >> 2;
            if ((e & 1) != 0) {
                Shapes.rounded(d, 0, 0, w, h, r, 0);
            }
            if ((e & 2) != 0) {
                Shapes.ring(d, 0, 0, w, h, (e & 1) == 0 && r > 0 ? Math.min(r + 1, Math.min(w, h) / 2) : r, 1);
            }
        };

        /** extra: 1 for a fill, 2 for an edge. */
        private static final FillFold.Geometry DOT_SHAPE = (d, w, h, e) -> {
            if ((e & 1) != 0) {
                Shapes.rounded(d, 0, 0, w, w, w / 2, 0);
            }
            if ((e & 2) != 0) {
                Shapes.ring(d, 0, 0, w, w, w / 2, 1);
            }
        };

        @Override
        public void panel(Draw d, Palette p, int x, int y, int w, int h) {
            int[] c = FillFold.colors();
            c[0] = Shapes.alpha(p.shadow(), (p.shadow() >>> 24) / 2);
            clayColors(c, 1, p.frame(), p.shadow(), 1, LIT);
            clayColors(c, 7, p.surface(), 0, -1, LIT, 0F, SUNK_SHADE);
            FillFold.draw(d, PANEL, PANEL_SHAPE, x, y, w, h, p.shadow() != 0 ? 1 : 0, c, 13);
        }

        @Override
        public void header(Draw d, Palette p, int x, int y, int w, int h) {
            section(d, p, x, y, w, h);
        }

        @Override
        public void section(Draw d, Palette p, int x, int y, int w, int h) {
            int[] c = FillFold.colors();
            clayColors(c, 0, p.section(), Shapes.alpha(p.frameDark(), 0x40), 1, LIT);
            FillFold.draw(d, RAISED, RAISED_SHAPE, x, y, w, h, 2, c, 6);
        }

        @Override
        public void well(Draw d, Palette p, int x, int y, int w, int h, boolean focused) {
            int[] c = FillFold.colors();
            c[0] = focused ? p.accent() : p.wellEdge();
            clayColors(c, 1, p.well(), 0, -1, LIT, 0F, SUNK_SHADE);
            FillFold.draw(d, WELL, WELL_SHAPE, x, y, w, h, 0, c, 7);
        }

        /** A dark face on a dark surface gets a dimmer highlight, so an idle button does not wear a white line. */
        @Override
        public void raised(Draw d, Palette p, int x, int y, int w, int h, int color, Shapes.Relief relief) {
            int k = relief == Shapes.Relief.FLAT ? 0 : relief == Shapes.Relief.PRESSED ? -1 : 1;
            float lit = luminance(p.surface()) < 0.5F ? LIT * Math.min(1F, 0.8F + 0.4F * luminance(color)) : LIT;
            int[] c = FillFold.colors();
            clayColors(c, 0, color, Shapes.alpha(p.frameDark(), 0x60), k, lit);
            FillFold.draw(d, RAISED, RAISED_SHAPE, x, y, w, h, k + 1, c, 6);
        }

        @Override
        public void bar(Draw d, Palette p, int x, int y, int w, int h, float value, int color) {
            int[] c = FillFold.colors();
            c[0] = p.wellEdge();
            clayColors(c, 1, p.well(), 0, -1, LIT, TRACK_LIT, TRACK_SHADE);
            int filled = Math.round(Maths.clamp(value, 0F, 1F) * (w - 2));
            if (filled <= 0) {
                FillFold.draw(d, BAR, BAR_SHAPE, x, y, w, h, 0, c, 7);
                return;
            }
            float lit = (luminance(p.surface()) < 0.5F ? LIT * Math.min(1F, 0.8F + 0.4F * luminance(color)) : LIT) * 0.5F;
            c[7] = color;
            c[8] = Shapes.mix(color, 0xFFFFFFFF, lit);
            c[9] = Shapes.mix(color, 0xFF000000, 0.12F);
            c[10] = Shapes.mix(color, 0xFFFFFFFF, lit / 10);
            c[11] = Shapes.mix(color, 0xFF000000, 0.06F);
            FillFold.draw(d, BAR, BAR_SHAPE, x, y, w, h, pillWidth(w, h, filled), c, 12);
        }

        @Override
        public void knob(Draw d, Palette p, int x, int y, boolean lit) {
            int[] c = FillFold.colors();
            clayColors(c, 0, lit ? p.primaryHover() : p.primary(), Shapes.alpha(p.frameDark(), 0x70), 1, LIT);
            FillFold.draw(d, RAISED, RAISED_SHAPE, x, y, 7, 8, 2, c, 6);
        }

        @Override
        public void track(Draw d, Palette p, int x, int y, int w, int h, int color) {
            int[] c = FillFold.colors();
            clayColors(c, 0, color, 0, -1, LIT, TRACK_LIT, TRACK_SHADE);
            FillFold.draw(d, TRACK, TRACK_SHAPE, x, y, w, h, 0, c, 6);
        }

        @Override
        public void toggleKnob(Draw d, Palette p, int x, int y, int face, boolean active) {
            int[] c = FillFold.colors();
            clayColors(c, 0, face, Shapes.alpha(p.frameDark(), 0x70), active ? 1 : 0, LIT);
            FillFold.draw(d, TOGGLE, TOGGLE_SHAPE, x, y, 8, 8, active ? 1 : 0, c, 6);
        }

        @Override
        public void plate(Draw d, Palette p, int x, int y, int w, int h, int fill, int edge) {
            int r = plateCorner(w, h);
            int parts = (fill != 0 ? 1 : 0) | (edge != 0 ? 2 : 0);
            if (parts == 0 || r < 0 || r > 0x3FF) {
                FrameStyle.super.plate(d, p, x, y, w, h, fill, edge);
                return;
            }
            int[] c = FillFold.colors();
            c[0] = fill;
            c[1] = edge;
            FillFold.draw(d, PLATE, PLATE_SHAPE, x, y, w, h, r << 2 | parts, c, 2);
        }

        @Override
        public void dot(Draw d, Palette p, int x, int y, int side, int fill, int edge) {
            int[] c = FillFold.colors();
            c[0] = fill;
            c[1] = edge;
            FillFold.draw(d, DOT, DOT_SHAPE, x, y, side, side, (fill != 0 ? 1 : 0) | (edge != 0 ? 2 : 0), c, 2);
        }
    };
}
