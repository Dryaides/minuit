package dev.dryaides.minuit;

import java.util.List;

/**
 * A small label, a count or a dot: "New", "3", "99+". It shows, it does not take input.
 *
 * <pre>{@code
 * add(new BadgeComponent(at(new Box("state", 10, 10, 0, 11)), "Beta").variant(BadgeComponent.Variant.WARN));
 * BadgeComponent mail = add(new BadgeComponent(at(new Box("mail", 40, 10, 0, 11))).count(120)); // 99+
 * add(new BadgeComponent(at(new Box("live", 70, 10, 0, 11)), "Live").dot(true));
 * }</pre>
 *
 * <p>A box of width 0 takes the width of its content, and {@link #alignRight(boolean)} grows it to the left of
 * its box's right edge. In a box that is too narrow the text ends in dots and shows whole as a tooltip. To put a
 * count on another widget's corner, call {@link #drawCount} from that widget's or your panel's drawing.
 */
public class BadgeComponent extends BaseComponent {
    /** The looks. They take the palette's colours, so they follow every theme. */
    public enum Variant { PRIMARY, SECONDARY, OUTLINE, GOOD, WARN, BAD }

    /** The height that holds a line of text with its margin. */
    public static final int HEIGHT = 11;

    private static final int PAD = 4;
    private static final int DOT = 4;
    private static final int DOT_GAP = 3;
    private static final int DEFAULT_MAX = 99;
    /** Below this, the badge has no room for its text: it shows as a plain dot. */
    private static final int MIN_TEXT_H = 9;

    private final int givenW;
    private final int rightEdge;
    private String text;
    private Variant variant = Variant.PRIMARY;
    private boolean dot;
    private boolean bare;
    private boolean alignRight;
    private boolean showZero;
    private int max = DEFAULT_MAX;
    private int count = -1;
    private boolean cut;

    public BadgeComponent(Box box, String text) {
        super(box);
        this.givenW = box.w();
        this.rightEdge = box.x() + box.w();
        this.text = text == null ? "" : text;
    }

    /** A badge for {@link #count(int)} or a lone {@link #dot(boolean)}. */
    public BadgeComponent(Box box) {
        this(box, "");
    }

    public BadgeComponent variant(Variant variant) {
        this.variant = variant == null ? Variant.PRIMARY : variant;
        return this;
    }

    public BadgeComponent text(String text) {
        this.text = text == null ? "" : text;
        this.count = -1;
        return this;
    }

    public String text() {
        return text;
    }

    /** Shows a number in place of the text, capped by {@link #max(int)}. At 0 the badge goes, unless {@link #showZero(boolean)}. */
    public BadgeComponent count(int count) {
        this.count = Math.max(0, count);
        this.text = capped(this.count, max);
        return this;
    }

    public int count() {
        return count;
    }

    /** The highest number shown as it is. 99 by default, past which it reads 99+. */
    public BadgeComponent max(int max) {
        this.max = Math.max(1, max);
        if (count >= 0) {
            text = capped(count, this.max);
        }
        return this;
    }

    public BadgeComponent showZero(boolean showZero) {
        this.showZero = showZero;
        return this;
    }

    /** A dot before the text, or alone with no text. */
    public BadgeComponent dot(boolean dot) {
        this.dot = dot;
        return this;
    }

    /** True to draw only the dot and the text in their colour, with no face behind them: a status beside a name. */
    public BadgeComponent bare(boolean bare) {
        this.bare = bare;
        return this;
    }

    /** With a width of 0: keeps the badge's right edge in place, and the content grows to the left. */
    public BadgeComponent alignRight(boolean alignRight) {
        this.alignRight = alignRight;
        return this;
    }

    public BadgeComponent active(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public BadgeComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    /** What a count reads as. */
    public static String capped(int count, int max) {
        return count > max ? max + "+" : Integer.toString(Math.max(0, count));
    }

    private boolean gone() {
        return (count == 0 && !showZero) || (text.isEmpty() && !dot);
    }

    /** The width the content wants. */
    public int naturalWidth() {
        if (gone()) {
            return 0;
        }
        if (text.isEmpty()) {
            return bare ? DOT : DOT + 2 * Math.max(1, (h - DOT) / 2);
        }
        int content = TextMeasure.width(text) - 1 + (dot ? DOT + DOT_GAP : 0);
        return bare ? content : content + 2 * PAD;
    }

    private void fit() {
        if (givenW <= 0) {
            w = naturalWidth();
            if (alignRight) {
                x = rightEdge - w;
            }
        }
    }

    @Override
    public boolean isOver(double mx, double my) {
        fit();
        return super.isOver(mx, my);
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        fit();
        cut = false;
        if (gone() || w <= 0 || h <= 0) {
            return;
        }
        Palette p = t.palette();
        int face = faceOf(p, variant, active);
        int under = FormLook.surface(p);
        boolean filled = variant != Variant.OUTLINE && !bare;
        int ink = bare || variant == Variant.OUTLINE ? bareInk(p, variant, active, under) : inkOn(p, face, active);
        if (h < MIN_TEXT_H) {
            int side = Math.min(w, h);
            t.frame().plate(d, p, x, y + (h - side) / 2, side, side, filled || bare ? face : ink, 0);
            return;
        }
        if (filled) {
            t.frame().plate(d, p, x, y, w, h, face, 0);
        } else if (!bare) {
            t.frame().plate(d, p, x, y, w, h, 0, Shapes.alpha(ink, 0xA0));
        }
        int pad = bare ? 0 : PAD;
        int left = x + pad;
        if (dot) {
            int dotInk = bare ? bareInk(p, variant, active, under) : ink;
            if (text.isEmpty() && !bare) {
                left = x + (w - DOT) / 2;
            }
            int dotY = y + (h - DOT) / 2;
            t.frame().dot(d, p, left, dotY, DOT, dotInk, 0);
            left += DOT + DOT_GAP;
        }
        if (!text.isEmpty()) {
            int room = x + w - pad - left;
            // The font's last column is the space after the letter: it does not count against the margin.
            cut = TextMeasure.width(text) - 1 > room;
            if (room > 0) {
                d.textFit(text, left, RowLook.textY(y, h) + textNudge(h), cut ? room : room + 1, ink);
            }
        }
    }

    /** The letters have a line under them for descenders: an odd height centres the capitals one pixel lower. */
    private static int textNudge(int height) {
        return height % 2;
    }

    private static int faceOf(Palette p, Variant variant, boolean active) {
        if (!active) {
            return FormLook.disabledFace(p);
        }
        return switch (variant) {
            case PRIMARY -> p.primary();
            case SECONDARY -> FormLook.quietFace(p);
            case OUTLINE -> 0;
            case GOOD -> p.good();
            case WARN -> p.warn();
            case BAD -> p.destructive();
        };
    }

    private static int inkOn(Palette p, int face, boolean active) {
        if (!active) {
            return p.disabledText();
        }
        return Ink.on(p, face);
    }

    private static int bareInk(Palette p, Variant variant, boolean active, int under) {
        if (!active) {
            return p.disabledText();
        }
        return switch (variant) {
            case PRIMARY -> Ink.readable(p, p.markInk(), under, Ink.TEXT);
            case SECONDARY, OUTLINE -> p.text();
            case GOOD -> Ink.readable(p, p.good(), under, Ink.TEXT);
            case WARN -> Ink.readable(p, p.warn(), under, Ink.TEXT);
            case BAD -> Ink.readable(p, p.bad(), under, Ink.TEXT);
        };
    }

    /**
     * Draws a count on the corner of something else, with its right edge at {@code right} and its top at
     * {@code top}. It goes on a new layer, as it lands over text. Nothing shows for 0.
     */
    public static void drawCount(Draw d, Theme t, int count, int right, int top) {
        if (count <= 0) {
            return;
        }
        Palette p = t.palette();
        String label = capped(count, DEFAULT_MAX);
        int width = Math.max(HEIGHT, TextMeasure.width(label) - 1 + 2 * 2);
        d.push(0F, 0F, 1F);
        d.nextLayer();
        t.frame().plate(d, p, right - width - 1, top - 1, width + 2, HEIGHT + 2, FormLook.surface(p), 0);
        t.frame().plate(d, p, right - width, top, width, HEIGHT, p.destructive(), 0);
        int ink = inkOn(p, p.destructive(), true);
        d.text(label, right - width + (width - TextMeasure.width(label) + 2) / 2, top + 2, ink);
        d.pop();
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!isOver(mx, my)) {
            return null;
        }
        if (tooltip != null) {
            return tooltip;
        }
        if (cut) {
            return List.of(text);
        }
        return count > max ? List.of(Integer.toString(count)) : null;
    }
}
