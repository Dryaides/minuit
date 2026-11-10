package dev.dryaides.minuit;

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
                "leather", LEATHER, "slate", SLATE, "bedrock", BEDROCK, "glass", GLASS, "soft", SOFT, "bubbly", BUBBLY,
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
     * Bedrock Edition's Ore UI, as the 1.20+ create and edit world screens draw it: square flat cards
     * with a lit top rule, buttons in a 1 px outline with a lit top row and a dark band at the bottom
     * that the pressed face sinks into, two-square switches, segmented slider bars.
     */
    public static final FrameStyle BEDROCK = new FrameStyle() {
        @Override
        public Insets panelInsets() {
            return new Insets(2, 1, 1, 1);
        }

        @Override
        public void shadow(Draw d, Palette p, int x, int y, int w, int h, int color) {
            d.fill(x, y, x + w, y + h, color);
        }

        @Override
        public void panel(Draw d, Palette p, int x, int y, int w, int h) {
            d.fill(x, y, x + w, y + h, p.frameDark());
            d.fill(x + 1, y + 1, x + w - 1, y + h - 1, p.surface());
            d.fill(x + 1, y + 1, x + w - 1, y + 2, p.frameLight());
        }

        @Override
        public void header(Draw d, Palette p, int x, int y, int w, int h) {
            d.fill(x + 1, y + h - 1, x + w - 1, y + h, p.surfaceEdge());
        }

        @Override
        public void section(Draw d, Palette p, int x, int y, int w, int h) {
            d.fill(x, y, x + w, y + h, p.section());
            d.fill(x, y, x + w, y + 1, p.surfaceEdge());
        }

        @Override
        public void well(Draw d, Palette p, int x, int y, int w, int h, boolean focused) {
            d.fill(x, y, x + w, y + h, focused ? p.text() : p.wellEdge());
            d.fill(x + 1, y + 1, x + w - 1, y + h - 1, p.well());
        }

        /**
         * The Ore UI button: outline, lit top row, the face, a lit row over a dark band two rows deep.
         * Pressed, the face sinks one row and darkens; a disabled face trades the dark outline for the track grey.
         */
        @Override
        public void raised(Draw d, Palette p, int x, int y, int w, int h, int color, Shapes.Relief relief) {
            boolean off = color == p.disabled();
            d.fill(x, y, x + w, y + h, off ? p.track() : p.frameDark());
            if (w < 4 || h < 6) {
                d.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
                return;
            }
            boolean pressed = relief == Shapes.Relief.PRESSED;
            int[] ref = off ? null : nearRef(color);
            int face = pressed && ref == null ? Shapes.mix(color, 0xFF000000, 0.15F) : color;
            int band = ref != null ? pressed ? ref[3] : ref[4] : Shapes.mix(color, 0xFF000000, off ? 0.15F : 0.57F);
            int depth = pressed ? 1 : 2;
            int b = y + h - 1 - depth;
            d.fill(x + 1, y + 1, x + w - 1, b, face);
            if (ref != null && relief != Shapes.Relief.FLAT) {
                rims(d, x, y, w, b, ref[0], ref[1], ref[2], ref[3]);
            } else if (relief != Shapes.Relief.FLAT) {
                rims(d, x, y, w, b, color);
            }
            d.fill(x + 1, b, x + w - 1, y + h - 1, band);
            if (pressed && (color == p.primary() || color == p.primaryHover()) && w > 12) {
                int u = Math.max(4, w / 5);
                d.fill(x + (w - u) / 2, b - 1, x + (w - u) / 2 + u, b, p.primaryText());
            }
        }

        @Override
        public void button(Draw d, Palette p, int x, int y, int w, int h, ButtonComponent.Role role, int color,
                           Shapes.Relief relief) {
            raised(d, p, x, y, w, h + BAND, color, relief);
        }

        @Override
        public int buttonCorner() {
            return 0;
        }

        /** The dark band under an Ore UI button: two rows below the box, so the label centres on the whole box. */
        private static final int BAND = 2;

        @Override
        public Insets bleed() {
            return new Insets(0, 0, 0, BAND);
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
            int face = off ? p.disabled() : on(p, color) ? p.accent() : p.track();
            d.fill(x, y, x + w, y + h, off ? paleEdge(p) : p.frameDark());
            d.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
            if (h >= 6 && !off) {
                int b = y + h - 2;
                rims(d, x, y, w, b, face);
                d.fill(x + 1, b, x + w - 1, y + h - 1, Shapes.mix(face, 0xFF000000, 0.35F));
            }
        }

        /**
         * The four-sided rim of an Ore UI face between the outline at {@code y} and its bottom band at
         * {@code b}: top row and left column lit most, right column and the row over the band lit less.
         */
        private void rims(Draw d, int x, int y, int w, int b, int color) {
            int rim = Shapes.mix(color, 0xFFFFFFFF, 0.12F);
            d.fill(x + 1, y + 1, x + w - 1, y + 2, Shapes.mix(color, 0xFFFFFFFF, 0.40F));
            d.fill(x + 1, y + 2, x + 2, b, Shapes.mix(color, 0xFFFFFFFF, 0.25F));
            d.fill(x + w - 2, y + 2, x + w - 1, b, rim);
            d.fill(x + 2, b - 1, x + w - 2, b, rim);
        }

        /**
         * Rim and band colours measured on the Ore UI Edit World screen (edit_world.png), keyed by the face
         * they surround: top row, left column, right column, the lit row over the band, the band. Faces the
         * screen does not show get them through {@link #nearRef}.
         */
        private static int[] measured(int face) {
            return switch (face | 0xFF000000) {
                case 0xFFD0D1D4 -> new int[]{0xFFEFEFF1, 0xFFEFEFF1, 0xFFE5E4E7, 0xFFE5E4E7, 0xFF59595B};
                case 0xFF3C8527 -> new int[]{0xFF659E52, 0xFF659E52, 0xFF4F943D, 0xFF4E933A, 0xFF1D4F13};
                case 0xFFCA3636 -> new int[]{0xFFD65F5F, 0xFFD65F5F, 0xFFD24A49, 0xFFD04A4A, 0xFFAD1D1E};
                default -> null;
            };
        }

        /**
         * The rims and band of a face the screen does not show (a hover face, a face between rest and hover):
         * those measured around the nearest measured face, each moved by the face's step from it, so a hover
         * face keeps the rest face's rim contrast and a red button keeps a red band. Null for a face far from
         * every measured one, which falls back to the formulas.
         */
        private static int[] nearRef(int face) {
            int best = -1;
            int bestDist = 0;
            for (int key : new int[]{0xFFD0D1D4, 0xFF3C8527, 0xFFCA3636}) {
                int dist = 0;
                for (int sh = 0; sh <= 16; sh += 8) {
                    dist += Math.abs((face >> sh & 0xFF) - (key >> sh & 0xFF));
                }
                if (best == -1 || dist < bestDist) {
                    best = key;
                    bestDist = dist;
                }
            }
            if (bestDist > 90) {
                return null;
            }
            int[] ref = measured(best);
            int[] out = new int[ref.length];
            for (int i = 0; i < ref.length; i++) {
                int c = 0xFF000000;
                for (int sh = 0; sh <= 16; sh += 8) {
                    int v = (ref[i] >> sh & 0xFF) + (face >> sh & 0xFF) - (best >> sh & 0xFF);
                    c |= Math.max(0, Math.min(255, v)) << sh;
                }
                out[i] = c;
            }
            return out;
        }

        private void rims(Draw d, int x, int y, int w, int b, int top, int left, int right, int low) {
            d.fill(x + 1, y + 1, x + w - 1, y + 2, top);
            d.fill(x + 1, y + 2, x + 2, b, left);
            d.fill(x + w - 2, y + 2, x + w - 1, b, right);
            d.fill(x + 2, b - 1, x + w - 2, b, low);
        }

        /** The groove of a switch that is on: {@code color} on the way from the deep accent to the accent. */
        private boolean on(Palette p, int color) {
            for (int i = 0; i <= 20; i++) {
                int m = Shapes.mix(p.accentDeep(), p.accent(), i / 20F);
                boolean near = true;
                for (int sh = 0; sh <= 16; sh += 8) {
                    near &= Math.abs((m >> sh & 0xFF) - (color >> sh & 0xFF)) <= 3;
                }
                if (near) {
                    return true;
                }
            }
            return false;
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
        private int paleEdge(Palette p) {
            return Shapes.mix(p.disabled(), 0xFF000000, 0.33F);
        }

        /** The vanilla slot: shaded top and left, lit bottom and right. */
        @Override
        public void slot(Draw d, Palette p, int x, int y, boolean hovered, boolean marked) {
            d.fill(x, y, x + 17, y + 17, p.wellEdge());
            d.fill(x + 1, y + 1, x + 18, y + 18, p.frameLight());
            d.fill(x + 1, y + 1, x + 17, y + 17, p.well());
            if (hovered) {
                d.fill(x + 1, y + 1, x + 17, y + 17, Shapes.SLOT_HOVER_BACK);
            }
            if (marked) {
                Shapes.ring(d, x, y, 18, 18, 0, p.accent());
            }
        }
    };

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

        /** checkbox.png: a 1 px outline, white under the mouse, round a dark well with a white tick. */
        @Override
        public void checkbox(Draw d, Palette p, int x, int y, int side, boolean on, boolean lit, boolean active) {
            GuiPart part = on ? lit ? GuiPart.CHECKBOX_SELECTED_HIGHLIGHTED : GuiPart.CHECKBOX_SELECTED
                    : lit ? GuiPart.CHECKBOX_HIGHLIGHTED : GuiPart.CHECKBOX;
            if (active && art(d, p, part, x, y, side, side)) {
                return;
            }
            int back = active ? p.track() : p.disabled();
            d.fill(x, y, x + side, y + side, lit ? 0xFFFFFFFF : p.frameDark());
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

        /** The game has no radio: the round mark takes checkbox.png's outline, well and white mark. */
        @Override
        public void radio(Draw d, Palette p, int x, int y, int side, boolean on, boolean lit, boolean active) {
            int cell = Math.max(1, side / FormLook.RADIO_ART);
            FormLook.pixels(d, FormLook.RADIO_INSIDE, x, y, cell, active ? p.track() : p.disabled());
            FormLook.pixels(d, FormLook.RADIO_RING, x, y, cell, lit && active ? 0xFFFFFFFF : p.frameDark());
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
            d.fill(x, y, x + w, y + h, lit ? 0xFFFFFFFF : p.frameDark());
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
         * scroller_background.png and scroller.png: a dark trough, a light thumb shaded on its right and
         * bottom. The thumb also takes a 1 px lit rim on its top and left, so it parts from a panel of its
         * own grey where the game's sprite sits on a dark list.
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
            d.fill(x, thumbY, x + 6, thumbY + thumbH, p.trim());
            d.fill(x, thumbY, x + 5, thumbY + thumbH - 1, p.frameLight());
            d.fill(x + 1, thumbY + 1, x + 5, thumbY + thumbH - 1, lit ? p.frameLight() : p.surfaceEdge());
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
                d.fill(x, y, x + 18, y + 18, p.well());
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
     * A glass pane: one translucent fill per surface with an opaque 1 px outline, so no layer
     * shows through another, and two faint diagonal glints across the panel.
     */
    public static final FrameStyle GLASS = new FrameStyle() {
        @Override
        public Insets panelInsets() {
            return new Insets(2, 1, 1, 1);
        }

        @Override
        public int corner() {
            return 1;
        }

        private static final int PERIOD = 150;

        @Override
        public void panel(Draw d, Palette p, int x, int y, int w, int h) {
            Shapes.rounded(d, x + 1, y + 1, w - 2, h - 2, 1, p.surface());
            for (int row = 2; row < h - 2; row++) {
                glint(d, x + 2, y + row, w - 4, row, 60, 66, 0x1FFFFFFF);
                glint(d, x + 2, y + row, w - 4, row, 74, 77, 0x12FFFFFF);
            }
            outline(d, x, y, w, h, p.frameDark());
            d.fill(x + 2, y + 1, x + w - 2, y + 2, p.frameLight());
        }

        /** The part of the stripe {@code [from, to)} of each period that crosses this row. */
        private void glint(Draw d, int x, int y, int w, int row, int from, int to, int color) {
            for (int start = from - row % PERIOD - PERIOD; start < w; start += PERIOD) {
                int a = Math.max(0, start);
                int b = Math.min(w, start + to - from);
                if (a < b) {
                    d.fill(x + a, y, x + b, y + 1, color);
                }
            }
        }

        @Override
        public void header(Draw d, Palette p, int x, int y, int w, int h) {
            d.fill(x + 2, y + h - 1, x + w - 2, y + h, p.surfaceEdge());
        }

        @Override
        public void section(Draw d, Palette p, int x, int y, int w, int h) {
            d.fill(x + 1, y + 1, x + w - 1, y + h - 1, p.section());
            outline(d, x, y, w, h, p.surfaceEdge());
        }

        @Override
        public void well(Draw d, Palette p, int x, int y, int w, int h, boolean focused) {
            d.fill(x + 1, y + 1, x + w - 1, y + h - 1, p.well());
            outline(d, x, y, w, h, focused ? p.accent() : p.wellEdge());
        }

        @Override
        public void raised(Draw d, Palette p, int x, int y, int w, int h, int color, Shapes.Relief relief) {
            d.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
            outline(d, x, y, w, h, relief == Shapes.Relief.PRESSED ? p.frameDark() : p.wellEdge());
            if (relief == Shapes.Relief.RAISED && w > 4) {
                d.fill(x + 2, y + 1, x + w - 2, y + 2, 0x33FFFFFF);
            }
        }

        @Override
        public int buttonCorner() {
            return 1;
        }

        /** A 1 px line around the box, its corner pixels left out. */
        private void outline(Draw d, int x, int y, int w, int h, int color) {
            if (w < 3 || h < 3) {
                return;
            }
            d.fill(x + 1, y, x + w - 1, y + 1, color);
            d.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
            d.fill(x, y + 1, x + 1, y + h - 1, color);
            d.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
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

        /** Pressed in: a face a little darker than the plate, under rims swapped to sunk. */
        @Override
        public int selectedFace(Palette p, float hover) {
            return Shapes.mix(p.raised() | 0xFF000000, 0xFF000000, 0.09F + 0.04F * hover);
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
        private static int lit(Palette p) {
            return Shapes.contrast(p.frameLight(), p.surface()) < 1.1 ? p.surfaceEdge() : p.frameLight();
        }

        @Override
        public void panel(Draw d, Palette p, int x, int y, int w, int h) {
            Shapes.rounded(d, x + 1, y + 2, w, h, 5, p.shadow());
            rims(d, p, x, y, w, h, 5, p.surface(), true, 2);
        }

        @Override
        public void header(Draw d, Palette p, int x, int y, int w, int h) {
            d.fill(x + 3, y + h - 1, x + w - 3, y + h, p.frameDark());
            d.fill(x + 3, y + h, x + w - 3, y + h + 1, p.frameLight());
        }

        @Override
        public void section(Draw d, Palette p, int x, int y, int w, int h) {
            rims(d, p, x, y, w, h, 3, p.section(), true, 1);
        }

        @Override
        public void well(Draw d, Palette p, int x, int y, int w, int h, boolean focused) {
            rims(d, p, x, y, w, h, 3, p.well(), false, 1);
            if (focused) {
                d.fill(x + 3, y + h - 1, x + w - 3, y + h, p.accent());
            }
        }

        @Override
        public void raised(Draw d, Palette p, int x, int y, int w, int h, int color, Shapes.Relief relief) {
            if (relief == Shapes.Relief.FLAT) {
                Shapes.rounded(d, x, y, w, h, 3, color);
            } else {
                rims(d, p, x, y, w, h, 3, color, relief != Shapes.Relief.PRESSED, 1);
            }
        }

        @Override
        public void button(Draw d, Palette p, int x, int y, int w, int h, ButtonComponent.Role role, int color,
                           Shapes.Relief relief) {
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
            rims(d, p, x, y, w, h, Math.min(3, h / 2), p.track(), false, 1);
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

        private static void clay(Draw d, int x, int y, int w, int h, int r, int face, int shadow, int relief, float lit) {
            clay(d, x, y, w, h, r, face, shadow, relief, lit, 0.4F, SUNK_SHADE);
        }

        /** {@code sunkLit} and {@code sunkShade} are the light on the bottom edge and the shade on the top edge of a sunk shape. */
        private static void clay(Draw d, int x, int y, int w, int h, int r, int face, int shadow, int relief, float lit, float sunkLit, float sunkShade) {
            if (relief > 0 && shadow != 0) {
                Shapes.rounded(d, x + 1, y + 1, w, h, r, shadow);
            }
            Shapes.rounded(d, x, y, w, h, r, face);
            if (relief == 0 || w < 4 || h < 4) {
                return;
            }
            int mw = w - 2;
            int mh = h - 2;
            int mr = Math.max(0, r - 1);
            int up = relief > 0 ? -1 : 1;
            edge(d, x + 1, y + 1, mw, mh, mr, up, 1, Shapes.mix(face, 0xFFFFFFFF, relief > 0 ? lit : sunkLit));
            edge(d, x + 1, y + 1, mw, mh, mr, -up, 1, Shapes.mix(face, 0xFF000000, relief > 0 ? 0.12F : sunkShade));
            if (h >= 10) {
                edge(d, x + 1, y + 1, mw, mh, mr, up, 2, Shapes.mix(face, 0xFFFFFFFF, (relief > 0 ? lit : sunkLit) / 10));
                edge(d, x + 1, y + 1, mw, mh, mr, -up, 2, Shapes.mix(face, 0xFF000000, relief > 0 ? 0.06F : sunkShade / 2));
            }
        }

        /**
         * The shade on the top edge of a sunk shape. A well or a panel surface gets no bottom light: on a dark face
         * it reads as a white line across the box.
         */
        private static final float SUNK_SHADE = 0.14F;

        /** A recessed well for a bar or a track: the bottom light is as faint as a second row, so it reads as an edge, not a line. */
        private static final float TRACK_LIT = 0.03F;
        private static final float TRACK_SHADE = 0.07F;

        /** A fill pill whose relief is traced on its own full mask: the top edge lit, the bottom edge shaded, the ends follow the curve. */
        private static void pill(Draw d, int x, int y, int w, int h, int r, int face, float lit) {
            int mr = radius(w, h, r);
            for (int row = 0; row < h; row++) {
                int in = inset(h, mr, row);
                d.fill(x + in, y + row, x + w - in, y + row + 1, face);
            }
            edge(d, x, y, w, h, r, -1, 1, Shapes.mix(face, 0xFFFFFFFF, lit));
            edge(d, x, y, w, h, r, 1, 1, Shapes.mix(face, 0xFF000000, 0.12F));
            if (h >= 10) {
                edge(d, x, y, w, h, r, -1, 2, Shapes.mix(face, 0xFFFFFFFF, lit / 10));
                edge(d, x, y, w, h, r, 1, 2, Shapes.mix(face, 0xFF000000, 0.06F));
            }
        }

        private static float luminance(int c) {
            return (0.2126F * (c >> 16 & 0xFF) + 0.7152F * (c >> 8 & 0xFF) + 0.0722F * (c & 0xFF)) / 255F;
        }

        @Override
        public void panel(Draw d, Palette p, int x, int y, int w, int h) {
            Shapes.rounded(d, x + 1, y + 2, w, h, 4, Shapes.alpha(p.shadow(), (p.shadow() >>> 24) / 2));
            clay(d, x, y, w, h, 4, p.frame(), p.shadow(), 1, LIT);
            clay(d, x + 3, y + 3, w - 6, h - 7, 3, p.surface(), 0, -1, LIT, 0F, SUNK_SHADE);
        }

        @Override
        public void header(Draw d, Palette p, int x, int y, int w, int h) {
            clay(d, x, y, w, h, 3, p.section(), Shapes.alpha(p.frameDark(), 0x40), 1, LIT);
        }

        @Override
        public void section(Draw d, Palette p, int x, int y, int w, int h) {
            clay(d, x, y, w, h, 3, p.section(), Shapes.alpha(p.frameDark(), 0x40), 1, LIT);
        }

        @Override
        public void well(Draw d, Palette p, int x, int y, int w, int h, boolean focused) {
            Shapes.rounded(d, x, y, w, h, 3, focused ? p.accent() : p.wellEdge());
            clay(d, x + 1, y + 1, w - 2, h - 2, 2, p.well(), 0, -1, LIT, 0F, SUNK_SHADE);
        }

        /** A dark face on a dark surface gets a dimmer highlight, so an idle button does not wear a white line. */
        @Override
        public void raised(Draw d, Palette p, int x, int y, int w, int h, int color, Shapes.Relief relief) {
            int k = relief == Shapes.Relief.FLAT ? 0 : relief == Shapes.Relief.PRESSED ? -1 : 1;
            float lit = luminance(p.surface()) < 0.5F ? LIT * Math.min(1F, 0.8F + 0.4F * luminance(color)) : LIT;
            clay(d, x, y, w, h, 3, color, Shapes.alpha(p.frameDark(), 0x60), k, lit);
        }

        @Override
        public void bar(Draw d, Palette p, int x, int y, int w, int h, float value, int color) {
            int r = Math.min(3, h / 2);
            Shapes.rounded(d, x, y, w, h, 3, p.wellEdge());
            clay(d, x + 1, y + 1, w - 2, h - 2, 2, p.well(), 0, -1, LIT, TRACK_LIT, TRACK_SHADE);
            int filled = Math.round(Maths.clamp(value, 0F, 1F) * (w - 2));
            if (filled > 0) {
                float lit = luminance(p.surface()) < 0.5F ? LIT * Math.min(1F, 0.8F + 0.4F * luminance(color)) : LIT;
                pill(d, x + 1, y + 1, pillWidth(w, h, filled), h - 2, Math.max(0, r - 1), color, lit * 0.5F);
            }
        }

        @Override
        public void knob(Draw d, Palette p, int x, int y, boolean lit) {
            clay(d, x, y, 7, 8, 3, lit ? p.primaryHover() : p.primary(), Shapes.alpha(p.frameDark(), 0x70), 1, LIT);
        }

        @Override
        public void track(Draw d, Palette p, int x, int y, int w, int h, int color) {
            clay(d, x, y, w, h, h / 2, color, 0, -1, LIT, TRACK_LIT, TRACK_SHADE);
        }

        @Override
        public void toggleKnob(Draw d, Palette p, int x, int y, int face, boolean active) {
            clay(d, x, y, 8, 8, 4, face, Shapes.alpha(p.frameDark(), 0x70), active ? 1 : 0, LIT);
        }
    };
}
