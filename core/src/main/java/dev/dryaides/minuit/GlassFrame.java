package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.ImageFamily;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A family's shapes in the glass mode: each part is drawn by the family into a recorder, the layers of every pixel
 * are blended into one colour, and the result goes out as rects, rows merged. A translucent face would otherwise
 * stack with the bevel rows, rims and shadows drawn over it into dark seams; flattened, every pixel blends once over
 * what is behind the panel, and the family keeps its silhouette, corners and relief.
 *
 * <p>Plans are kept per part, size, state and palette, as {@link FillFold} keeps its folds. A part that draws text,
 * an image or a clip is drawn by the family as it is.
 *
 * <p>Flattening keeps every family's look, so with {@code -Dminuit.raster=true} every drawn family wears this
 * frame, and a part of many rects is baked once and blitted ({@link RasterCache}).
 */
@Internal
public final class GlassFrame implements FrameStyle {
    /** Plans kept per frame; the least recently used goes first. */
    private static final int PLANS = 512;
    /** Larger parts draw unflattened rather than hold a grid this big. */
    private static final int MAX_AREA = 1 << 20;

    private final FrameStyle base;
    private final Map<Key, int[]> plans = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, int[]> eldest) {
            return size() > PLANS;
        }
    };
    /** Parts flattened since this frame was made, for a bench or a test. */
    int plansBuilt;

    public GlassFrame(FrameStyle base) {
        this.base = base;
    }

    /** The family's own shapes. */
    public FrameStyle base() {
        return this.base;
    }

    private void paint(Draw d, Palette p, int x, int y, Key key, Consumer<Draw> part) {
        int[] plan;
        synchronized (this.plans) {
            plan = this.plans.get(key);
        }
        if (plan == null) {
            plan = flatten(part);
            synchronized (this.plans) {
                this.plans.put(key, plan);
                this.plansBuilt++;
            }
        }
        if (plan.length == 0) {
            d.push(x, y, 1F);
            part.accept(d);
            d.pop();
            return;
        }
        if (RasterCache.draw(d, this, key, plan, x, y)) {
            return;
        }
        for (int i = 1; i < plan.length; i += 5) {
            d.fill(x + plan[i], y + plan[i + 1], x + plan[i + 2], y + plan[i + 3], plan[i + 4]);
        }
    }

    /**
     * The part's fills, the layers of each pixel blended source-over into one colour, as rects {@code x0, y0, x1, y1,
     * argb} after a leading 0; an empty array when the part draws anything but fills, or is too large.
     */
    static int[] flatten(Consumer<Draw> part) {
        Recorder rec = new Recorder();
        part.accept(rec);
        if (rec.impure) {
            return new int[0];
        }
        if (rec.rects.isEmpty()) {
            return new int[] {0};
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int[] r : rec.rects) {
            minX = Math.min(minX, r[0]);
            minY = Math.min(minY, r[1]);
            maxX = Math.max(maxX, r[2]);
            maxY = Math.max(maxY, r[3]);
        }
        int w = maxX - minX;
        int h = maxY - minY;
        if ((long) w * h > MAX_AREA) {
            return new int[0];
        }
        int[] grid = new int[w * h];
        for (int[] r : rec.rects) {
            for (int yy = r[1]; yy < r[3]; yy++) {
                int at = (yy - minY) * w - minX;
                for (int xx = r[0]; xx < r[2]; xx++) {
                    grid[at + xx] = over(r[4], grid[at + xx]);
                }
            }
        }
        List<int[]> done = new ArrayList<>();
        Map<Long, int[]> open = new LinkedHashMap<>();
        for (int row = 0; row < h; row++) {
            Map<Long, int[]> next = new LinkedHashMap<>();
            int col = 0;
            while (col < w) {
                int c = grid[row * w + col];
                int end = col + 1;
                while (end < w && grid[row * w + end] == c) {
                    end++;
                }
                if (c >>> 24 != 0) {
                    long run = (long) col << 48 | (long) end << 32 | c & 0xFFFFFFFFL;
                    int[] rect = open.remove(run);
                    if (rect == null) {
                        rect = new int[] {col + minX, row + minY, end + minX, 0, c};
                    }
                    rect[3] = row + minY + 1;
                    next.put(run, rect);
                }
                col = end;
            }
            done.addAll(open.values());
            open = next;
        }
        done.addAll(open.values());
        int[] plan = new int[1 + 5 * done.size()];
        int i = 1;
        for (int[] r : done) {
            System.arraycopy(r, 0, plan, i, 5);
            i += 5;
        }
        return plan;
    }

    /** {@code top} laid over {@code under}, both straight ARGB, as one straight ARGB colour. */
    static int over(int top, int under) {
        int ta = top >>> 24;
        if (ta == 0xFF || under >>> 24 == 0) {
            return top;
        }
        if (ta == 0) {
            return under;
        }
        float a = ta / 255F;
        float b = (under >>> 24) / 255F * (1F - a);
        float out = a + b;
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            float c = ((top >> shift & 0xFF) * a + (under >> shift & 0xFF) * b) / out;
            rgb |= Math.min(255, Math.round(c)) << shift;
        }
        return Math.round(out * 255F) << 24 | rgb;
    }

    /** A part's state: its name, palette and every argument but the position. */
    private static final class Key {
        private final String part;
        private final Palette palette;
        private final Object[] state;
        private final int hash;

        Key(String part, Palette palette, Object... state) {
            this.part = part;
            this.palette = palette;
            this.state = state;
            this.hash = 31 * (31 * part.hashCode() + System.identityHashCode(palette)) + Arrays.hashCode(state);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && k.palette == this.palette && k.part.equals(this.part)
                    && Arrays.equals(k.state, this.state);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }

    /** Records fills; anything else marks the part as one to draw unflattened. */
    private static final class Recorder extends Draw {
        final List<int[]> rects = new ArrayList<>();
        boolean impure;

        @Override
        protected void rect(int x0, int y0, int x1, int y1, int argb) {
            this.rects.add(new int[] {x0, y0, x1, y1, argb});
        }

        @Override protected void string(String text, int x, int y, int argb) { this.impure = true; }
        @Override public int width(String text) { return 0; }
        @Override public String cut(String text, int maxWidth) { return text; }
        @Override public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH,
                                   float alpha) { this.impure = true; }
        @Override protected void pushPose(float x, float y, float scale) { this.impure = true; }
        @Override protected void popPose() { }
        @Override public void scissor(int x0, int y0, int x1, int y1) { this.impure = true; }
        @Override public void endScissor() { }
        @Override public void tooltip(List<String> lines, int x, int y) { this.impure = true; }
    }

    @Override
    public Insets bleed() {
        return this.base.bleed();
    }

    @Override
    public int corner() {
        return this.base.corner();
    }

    @Override
    public void shadow(Draw d, Palette p, int x, int y, int w, int h, int color) {
        paint(d, p, x, y, new Key("shadow", p, w, h, color), r -> this.base.shadow(r, p, 0, 0, w, h, color));
    }

    @Override
    public void dropShadow(Draw d, Palette p, int x, int y, int w, int h, int depth) {
        paint(d, p, x, y, new Key("dropShadow", p, w, h, depth), r -> this.base.dropShadow(r, p, 0, 0, w, h, depth));
    }

    @Override
    public Insets panelInsets() {
        return this.base.panelInsets();
    }

    @Override
    public int panelShadow() {
        return this.base.panelShadow();
    }

    @Override
    public void panel(Draw d, Palette p, int x, int y, int w, int h) {
        paint(d, p, x, y, new Key("panel", p, w, h), r -> this.base.panel(r, p, 0, 0, w, h));
    }

    @Override
    public void header(Draw d, Palette p, int x, int y, int w, int h) {
        paint(d, p, x, y, new Key("header", p, w, h), r -> this.base.header(r, p, 0, 0, w, h));
    }

    @Override
    public void section(Draw d, Palette p, int x, int y, int w, int h) {
        paint(d, p, x, y, new Key("section", p, w, h), r -> this.base.section(r, p, 0, 0, w, h));
    }

    @Override
    public void well(Draw d, Palette p, int x, int y, int w, int h, boolean focused) {
        paint(d, p, x, y, new Key("well", p, w, h, focused), r -> this.base.well(r, p, 0, 0, w, h, focused));
    }

    @Override
    public void well(Draw d, Palette p, int x, int y, int w, int h, boolean focused, boolean lit) {
        paint(d, p, x, y, new Key("well", p, w, h, focused, lit), r -> this.base.well(r, p, 0, 0, w, h, focused, lit));
    }

    @Override
    public void raised(Draw d, Palette p, int x, int y, int w, int h, int color, boolean highlight) {
        paint(d, p, x, y, new Key("raised", p, w, h, color, highlight),
                r -> this.base.raised(r, p, 0, 0, w, h, color, highlight));
    }

    @Override
    public void raised(Draw d, Palette p, int x, int y, int w, int h, int color, Shapes.Relief relief) {
        paint(d, p, x, y, new Key("raised", p, w, h, color, relief),
                r -> this.base.raised(r, p, 0, 0, w, h, color, relief));
    }

    @Override
    public void button(Draw d, Palette p, int x, int y, int w, int h,
            ButtonComponent.Role role, int color, Shapes.Relief relief) {
        paint(d, p, x, y, new Key("button", p, w, h, role, color, relief),
                r -> this.base.button(r, p, 0, 0, w, h, role, color, relief));
    }

    @Override
    public int selectedFace(Palette p, float hover) {
        return this.base.selectedFace(p, hover);
    }

    @Override
    public void selectedMark(Draw d, Palette p, int x, int y, int w, int h, int color) {
        this.base.selectedMark(d, p, x, y, w, h, color);
    }

    @Override
    public Shapes.Relief selectedRelief() {
        return this.base.selectedRelief();
    }

    @Override
    public int pressTravel() {
        return this.base.pressTravel();
    }

    @Override
    public int buttonLip(int h) {
        return this.base.buttonLip(h);
    }

    @Override
    public int pressMs() {
        return this.base.pressMs();
    }

    @Override
    public void button(Draw d, Palette p, int x, int y, int w, int h,
            ButtonComponent.Role role, int color, Shapes.Relief relief, int sunk) {
        paint(d, p, x, y, new Key("button", p, w, h, role, color, relief, sunk),
                r -> this.base.button(r, p, 0, 0, w, h, role, color, relief, sunk));
    }

    @Override
    public int pressShiftX() {
        return this.base.pressShiftX();
    }

    @Override
    public int selectedTravel() {
        return this.base.selectedTravel();
    }

    @Override
    public void bar(Draw d, Palette p, int x, int y, int w, int h, float value, int color) {
        paint(d, p, x, y, new Key("bar", p, w, h, value, color), r -> this.base.bar(r, p, 0, 0, w, h, value, color));
    }

    @Override
    public void knob(Draw d, Palette p, int x, int y, boolean lit) {
        paint(d, p, x, y, new Key("knob", p, lit), r -> this.base.knob(r, p, 0, 0, lit));
    }

    @Override
    public void track(Draw d, Palette p, int x, int y, int w, int h, int color) {
        paint(d, p, x, y, new Key("track", p, w, h, color), r -> this.base.track(r, p, 0, 0, w, h, color));
    }

    /**
     * The smoke takes the groove's fill and its dark outline melts into it, Bedrock's included: in glass every
     * family's off groove gets the rim, square on Bedrock.
     */
    @Override
    public void trackEdge(Draw d, Palette p, int x, int y, int w, int h, int color) {
        FrameStyle shape = this.base instanceof ImageFamily images ? images.base() : this.base;
        if (shape == FrameStyles.BEDROCK) {
            Shapes.ring(d, x, y, w, h, 0, color);
        } else {
            paint(d, p, x, y, new Key("trackEdge", p, w, h, color), r -> this.base.trackEdge(r, p, 0, 0, w, h, color));
        }
    }

    @Override
    public void toggleKnob(Draw d, Palette p, int x, int y, int face, boolean active) {
        Palette m = GlassPalette.marks(p);
        paint(d, m, x, y, new Key("toggleKnob", m, face, active), r -> this.base.toggleKnob(r, m, 0, 0, face, active));
    }

    @Override
    public void groove(Draw d, Palette p, int x, int y, int w, int h, int filled, int steps, boolean active) {
        paint(d, p, x, y, new Key("groove", p, w, h, filled, steps, active),
                r -> this.base.groove(r, p, 0, 0, w, h, filled, steps, active));
    }

    @Override
    public void toggleKnob(Draw d, Palette p, int x, int y, int face, boolean active, boolean lit) {
        Palette m = GlassPalette.marks(p);
        paint(d, m, x, y, new Key("toggleKnob", m, face, active, lit),
                r -> this.base.toggleKnob(r, m, 0, 0, face, active, lit));
    }

    @Override
    public void slot(Draw d, Palette p, int x, int y, boolean hovered, boolean marked) {
        paint(d, p, x, y, new Key("slot", p, hovered, marked), r -> this.base.slot(r, p, 0, 0, hovered, marked));
    }

    @Override
    public void field(Draw d, Palette p, int x, int y, int w, int h, boolean focused) {
        paint(d, p, x, y, new Key("field", p, w, h, focused), r -> this.base.field(r, p, 0, 0, w, h, focused));
    }

    @Override
    public int fieldText(Palette p) {
        return this.base.fieldText(p);
    }

    @Override
    public int fieldHint(Palette p) {
        return this.base.fieldHint(p);
    }

    @Override
    public void hotbar(Draw d, Palette p, int x, int y) {
        paint(d, p, x, y, new Key("hotbar", p), r -> this.base.hotbar(r, p, 0, 0));
    }

    @Override
    public void hotbarSelection(Draw d, Palette p, int x, int y) {
        paint(d, p, x, y, new Key("hotbarSelection", p), r -> this.base.hotbarSelection(r, p, 0, 0));
    }

    @Override
    public boolean gameWidgets() {
        return this.base.gameWidgets();
    }

    @Override
    public void checkbox(Draw d, Palette p, int x, int y, int side, boolean on, boolean lit, boolean active) {
        paint(d, p, x, y, new Key("checkbox", p, side, on, lit, active),
                r -> this.base.checkbox(r, p, 0, 0, side, on, lit, active));
    }

    @Override
    public void sliderBar(Draw d, Palette p, int x, int y, int w, int h, boolean lit, boolean active) {
        paint(d, p, x, y, new Key("sliderBar", p, w, h, lit, active),
                r -> this.base.sliderBar(r, p, 0, 0, w, h, lit, active));
    }

    @Override
    public void sliderHandle(Draw d, Palette p, int x, int y, int h, boolean lit) {
        paint(d, p, x, y, new Key("sliderHandle", p, h, lit), r -> this.base.sliderHandle(r, p, 0, 0, h, lit));
    }

    @Override
    public void tab(Draw d, Palette p, int x, int y, int w, int h, boolean selected, boolean lit) {
        paint(d, p, x, y, new Key("tab", p, w, h, selected, lit), r -> this.base.tab(r, p, 0, 0, w, h, selected, lit));
    }

    @Override
    public int buttonHeight() {
        return this.base.buttonHeight();
    }

    @Override
    public int tabHeight(int base) {
        return this.base.tabHeight(base);
    }

    @Override
    public int tabInset(int h, boolean selected) {
        return this.base.tabInset(h, selected);
    }

    @Override
    public void tabRule(Draw d, Palette p, int x, int y, int w, int h) {
        paint(d, p, x, y, new Key("tabRule", p, w, h), r -> this.base.tabRule(r, p, 0, 0, w, h));
    }

    @Override
    public int tabGap() {
        return this.base.tabGap();
    }

    @Override
    public int tabText(Palette p, boolean selected, boolean active) {
        return this.base.tabText(p, selected, active);
    }

    @Override
    public int scrollerGap() {
        return this.base.scrollerGap();
    }

    @Override
    public void scroller(Draw d, Palette p, int x, int y, int h, int thumbY, int thumbH, boolean lit) {
        paint(d, p, x, y, new Key("scroller", p, h, thumbY, thumbH, lit),
                r -> this.base.scroller(r, p, 0, 0, h, thumbY, thumbH, lit));
    }

    @Override
    public void plate(Draw d, Palette p, int x, int y, int w, int h, int fill, int edge) {
        paint(d, p, x, y, new Key("plate", p, w, h, fill, edge), r -> this.base.plate(r, p, 0, 0, w, h, fill, edge));
    }

    @Override
    public void ringAround(Draw d, Palette p, int x, int y, int w, int h, int outset, int color) {
        paint(d, p, x, y, new Key("ringAround", p, w, h, outset, color),
                r -> this.base.ringAround(r, p, 0, 0, w, h, outset, color));
    }

    @Override
    public int buttonCorner() {
        return this.base.buttonCorner();
    }

    @Override
    public void panelEdge(Draw d, Palette p, int x, int y, int w, int h, int thickness, int color) {
        paint(d, p, x, y, new Key("panelEdge", p, w, h, thickness, color),
                r -> this.base.panelEdge(r, p, 0, 0, w, h, thickness, color));
    }

    @Override
    public void plateEdge(Draw d, Palette p, int x, int y, int w, int h, int thickness, int color) {
        paint(d, p, x, y, new Key("plateEdge", p, w, h, thickness, color),
                r -> this.base.plateEdge(r, p, 0, 0, w, h, thickness, color));
    }

    @Override
    public int plateCorner(int w, int h) {
        return this.base.plateCorner(w, h);
    }

    @Override
    public void dot(Draw d, Palette p, int x, int y, int side, int fill, int edge) {
        paint(d, p, x, y, new Key("dot", p, side, fill, edge), r -> this.base.dot(r, p, 0, 0, side, fill, edge));
    }

    @Override
    public void row(Draw d, Palette p, int x, int y, int w, int h, int face) {
        paint(d, p, x, y, new Key("row", p, w, h, face), r -> this.base.row(r, p, 0, 0, w, h, face));
    }

    @Override
    public void popup(Draw d, Palette p, int x, int y, int w, int h) {
        paint(d, p, x, y, new Key("popup", p, w, h), r -> this.base.popup(r, p, 0, 0, w, h));
    }

    @Override
    public void checkbox(Draw d, Palette p, int x, int y, int side,
            CheckboxComponent.State state, boolean lit, boolean active) {
        paint(d, p, x, y, new Key("checkbox", p, side, state, lit, active),
                r -> this.base.checkbox(r, p, 0, 0, side, state, lit, active));
    }

    @Override
    public void radio(Draw d, Palette p, int x, int y, int side, boolean on, boolean lit, boolean active) {
        Palette m = GlassPalette.marks(p);
        paint(d, m, x, y, new Key("radio", m, side, on, lit, active),
                r -> this.base.radio(r, m, 0, 0, side, on, lit, active));
    }

    @Override
    public void toast(Draw d, Palette p, int x, int y, int w, int h) {
        paint(d, p, x, y, new Key("toast", p, w, h), r -> this.base.toast(r, p, 0, 0, w, h));
    }
}
