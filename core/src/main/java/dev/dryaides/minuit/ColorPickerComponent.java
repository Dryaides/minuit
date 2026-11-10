package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.ColorMath;

import dev.dryaides.minuit.internal.Maths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A colour picker: a saturation and value square, a hue bar, an optional alpha bar, the current
 * colour with its hex code in a field the player can type in, and two rows of recent colours. Drawn from the top-left of its box,
 * {@link #WIDTH} wide and {@link #height()} high.
 *
 * <p>The picker holds the colour as hue, saturation and value. The hue changes only when the hue
 * bar moves or the caller sets a colour, so dragging into black or grey keeps the hue the player
 * chose.
 */
public final class ColorPickerComponent extends BaseComponent {

    public static final int WIDTH = 94;
    private static final int SV = 80;
    private static final int BAR = 10;
    private static final int GAP = 4;
    private static final int SWATCH = 12;
    private static final int PER_ROW = (WIDTH + 2) / (SWATCH + 2);

    private static final int[] HUES = {0, 60, 120, 180, 240, 300, 360};

    private final IntConsumer onChange;
    private IntConsumer onCommit = argb -> {
    };
    private boolean alpha;
    private List<Integer> recent = new ArrayList<>();
    private int recentRows = 2;
    private float hue;
    private float sat;
    private float val;
    private int alphaValue = 255;
    private Drag drag = Drag.NONE;
    private final TextFieldComponent hex;

    private enum Drag { NONE, SV, HUE, ALPHA }

    /** @param onChange each change while the player drags */
    public ColorPickerComponent(Box box, int initialArgb, IntConsumer onChange) {
        super(box.x(), box.y(), WIDTH, 0);
        this.onChange = onChange;
        this.hex = new TextFieldComponent(new Box("hex", 0, 0, WIDTH - SWATCH - GAP, SWATCH), "", this::typed)
                .filter(c -> c == '#' || Character.digit(c, 16) >= 0).maxLength(9);
        this.hex.onSubmit(text -> commit());
        setColor(initialArgb);
        this.h = height();
    }

    /** Adds the alpha bar; without it every colour is opaque. */
    public ColorPickerComponent alpha(boolean shown) {
        this.alpha = shown;
        this.h = height();
        showHex();
        return this;
    }

    /** The recent colours, newest first; the picker adds to this list on each commit. */
    public ColorPickerComponent recent(List<Integer> colours) {
        this.recent = colours;
        trimRecent();
        return this;
    }

    /**
     * How many rows of recent colours the picker keeps and shows, 2 by default; the oldest drop out
     * past them, and {@link #height()} counts exactly these rows so the picker never leaves its box.
     */
    public ColorPickerComponent recentRows(int rows) {
        this.recentRows = Math.max(0, rows);
        this.h = height();
        trimRecent();
        return this;
    }

    private int maxRecent() {
        return PER_ROW * this.recentRows;
    }

    private void trimRecent() {
        while (this.recent.size() > maxRecent()) {
            this.recent.remove(this.recent.size() - 1);
        }
    }

    /** On the release of a drag or a click on a recent colour; the colour also joins the recent ones. */
    public ColorPickerComponent onCommit(IntConsumer action) {
        this.onCommit = action;
        return this;
    }

    public int height() {
        return SV + GAP + (this.alpha ? 8 + GAP : 0) + SWATCH
                + (this.recentRows == 0 ? 0 : GAP + this.recentRows * (SWATCH + 2) - 2);
    }

    public int color() {
        return ColorMath.argb(this.hue, this.sat, this.val, this.alpha ? this.alphaValue : 255);
    }

    /** Sets the colour from outside, the one time the hue is read back from RGB. */
    public void setColor(int argb) {
        float[] hsv = ColorMath.hsv(argb);
        if (hsv[1] > 0 && hsv[2] > 0) {
            this.hue = hsv[0];
        }
        this.sat = hsv[1];
        this.val = hsv[2];
        this.alphaValue = argb >>> 24;
        showHex();
    }

    private String hexText() {
        return this.alpha ? "#" + dev.dryaides.minuit.internal.Fmt.hex(color(), 8)
                : "#" + dev.dryaides.minuit.internal.Fmt.hex(color() & 0xFFFFFF, 6);
    }

    private void showHex() {
        if (this.hex != null && !this.hex.focused) {
            this.hex.setText(hexText());
        }
    }

    /** A full code typed in the field (6 digits, or 8 with the alpha bar) sets the colour. */
    private void typed(String text) {
        String digits = text.startsWith("#") ? text.substring(1) : text;
        if (digits.indexOf('#') >= 0 || digits.length() != 6 && !(this.alpha && digits.length() == 8)) {
            return;
        }
        long value = Long.parseLong(digits, 16);
        int argb = digits.length() == 6 ? 0xFF000000 | (int) value : (int) value;
        float[] hsv = ColorMath.hsv(argb);
        if (hsv[1] > 0 && hsv[2] > 0) {
            this.hue = hsv[0];
        }
        this.sat = hsv[1];
        this.val = hsv[2];
        this.alphaValue = argb >>> 24;
        this.onChange.accept(color());
    }

    private int alphaY() {
        return this.y + SV + GAP;
    }

    private int swatchY() {
        return alphaY() + (this.alpha ? 8 + GAP : 0);
    }

    private int recentY() {
        return swatchY() + SWATCH + GAP;
    }

    private Box recentBox(int i) {
        return new Box("recent", this.x + (i % PER_ROW) * (SWATCH + 2), recentY() + (i / PER_ROW) * (SWATCH + 2),
                SWATCH, SWATCH);
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        int hx = this.x + SV + GAP;
        Shapes.well(d, this.x - 1, this.y - 1, SV + 2, SV + 2, p.wellEdge(), p.well());
        Shapes.well(d, hx - 1, this.y - 1, BAR + 2, SV + 2, p.wellEdge(), p.well());
        int pure = ColorMath.argb(this.hue, 1, 1, 255);
        d.gradient(this.x, this.y, this.x + SV, this.y + SV, 0xFFFFFFFF, pure, 0xFF000000, 0xFF000000);
        for (int i = 0; i < HUES.length - 1; i++) {
            int top = this.y + SV * i / 6;
            int bottom = this.y + SV * (i + 1) / 6;
            int from = ColorMath.argb(HUES[i], 1, 1, 255);
            int to = ColorMath.argb(HUES[i + 1], 1, 1, 255);
            d.gradient(hx, top, hx + BAR, bottom, from, from, to, to);
        }
        int sx = this.x + Math.round(this.sat * (SV - 1));
        int sy = this.y + Math.round((1 - this.val) * (SV - 1));
        d.fill(sx - 2, sy - 2, sx + 3, sy + 3, p.shadow());
        d.fill(sx - 1, sy - 1, sx + 2, sy + 2, p.text());
        d.fill(sx, sy, sx + 1, sy + 1, color() | 0xFF000000);
        int hy = this.y + Math.round(this.hue / 360F * (SV - 1));
        d.fill(hx - 2, hy - 1, hx + BAR + 2, hy + 2, p.shadow());
        d.fill(hx - 1, hy, hx + BAR + 1, hy + 1, p.text());
        if (this.alpha) {
            int ay = alphaY();
            Shapes.well(d, this.x - 1, ay - 1, SV + 2, 10, p.wellEdge(), p.well());
            checker(d, this.x, ay, this.x + SV, ay + 8);
            int opaque = color() | 0xFF000000;
            d.gradient(this.x, ay, this.x + SV, ay + 8, opaque & 0xFFFFFF, opaque, opaque & 0xFFFFFF, opaque);
            int ax = this.x + Math.round(this.alphaValue / 255F * (SV - 1));
            d.fill(ax - 1, ay - 2, ax + 2, ay + 10, p.shadow());
            d.fill(ax, ay - 1, ax + 1, ay + 9, p.text());
        }
        int swy = swatchY();
        d.fill(this.x, swy, this.x + SWATCH, swy + SWATCH, p.surfaceEdge());
        int swatch = color();
        if (swatch >>> 24 != 0xFF) {
            checker(d, this.x + 1, swy + 1, this.x + SWATCH - 1, swy + SWATCH - 1);
        }
        d.fill(this.x + 1, swy + 1, this.x + SWATCH - 1, swy + SWATCH - 1, swatch);
        if (!this.focused) {
            this.hex.focused = false;
            showHex();
        }
        this.hex.x = this.x + SWATCH + GAP;
        this.hex.y = swy;
        this.hex.draw(d, theme, mx, my, this.delta);
        for (int i = 0; i < Math.min(this.recent.size(), maxRecent()); i++) {
            Box b = recentBox(i);
            boolean over = b.contains(mx, my);
            d.fill(b.x(), b.y(), b.right(), b.bottom(), over ? p.text() : p.surfaceEdge());
            d.fill(b.x() + 1, b.y() + 1, b.right() - 1, b.bottom() - 1, this.recent.get(i));
        }
    }

    private static void checker(Draw d, int x0, int y0, int x1, int y1) {
        for (int yy = y0; yy < y1; yy += 4) {
            for (int xx = x0; xx < x1; xx += 4) {
                boolean light = ((xx - x0) / 4 + (yy - y0) / 4) % 2 == 0;
                d.fill(xx, yy, Math.min(xx + 4, x1), Math.min(yy + 4, y1), light ? 0xFFCCCCCC : 0xFF888888);
            }
        }
    }

    private void update(double mx, double my) {
        switch (this.drag) {
            case SV -> {
                this.sat = (float) Maths.clamp((mx - this.x) / (SV - 1), 0, 1);
                this.val = 1F - (float) Maths.clamp((my - this.y) / (SV - 1), 0, 1);
            }
            case HUE -> this.hue = (float) Maths.clamp((my - this.y) / (SV - 1), 0, 1) * 360F;
            case ALPHA -> this.alphaValue = (int) Math.round(Maths.clamp((mx - this.x) / (SV - 1), 0, 1) * 255);
            default -> {
                return;
            }
        }
        this.onChange.accept(color());
        showHex();
    }

    private void commit() {
        int c = color();
        this.recent.remove((Integer) c);
        this.recent.add(0, c);
        trimRecent();
        this.onCommit.accept(c);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.active || !this.visible || button != 0) {
            return false;
        }
        this.hex.focused = this.focused && this.hex.isOver(mx, my);
        if (this.hex.focused) {
            return this.hex.mouseClicked(mx, my, button);
        }
        if (new Box("sv", this.x, this.y, SV, SV).contains(mx, my)) {
            this.drag = Drag.SV;
        } else if (new Box("hue", this.x + SV + GAP, this.y, BAR, SV).contains(mx, my)) {
            this.drag = Drag.HUE;
        } else if (this.alpha && new Box("alpha", this.x, alphaY(), SV, 8).contains(mx, my)) {
            this.drag = Drag.ALPHA;
        } else {
            for (int i = 0; i < Math.min(this.recent.size(), maxRecent()); i++) {
                if (recentBox(i).contains(mx, my)) {
                    setColor(this.recent.get(i));
                    this.onChange.accept(color());
                    commit();
                    return true;
                }
            }
            return false;
        }
        update(mx, my);
        return true;
    }

    @Override
    protected boolean takesFocus() {
        return true;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        return this.hex.focused && this.hex.keyPressed(key, shift);
    }

    @Override
    public boolean charTyped(int codepoint) {
        return this.hex.focused && this.hex.charTyped(codepoint);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.hex.focused) {
            return this.hex.mouseDragged(mx, my, button, dx, dy);
        }
        if (this.drag == Drag.NONE) {
            return false;
        }
        update(mx, my);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (this.hex.focused) {
            return this.hex.mouseReleased(mx, my, button);
        }
        if (this.drag == Drag.NONE) {
            return false;
        }
        this.drag = Drag.NONE;
        commit();
        return true;
    }
}
