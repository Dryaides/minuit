package dev.dryaides.minuit;

/**
 * A stat bar on the HUD: a small panel with an optional icon, a label on the left and a value text on the right,
 * and the theme's bar under them, filled from 0 to 1 in a colour of the palette. The fill eases to a new value
 * over about a tenth of a second, at once under reduced motion.
 *
 * <pre>{@code
 * HudBar mana = HudLayer.main().add(new HudBar("mymod", "mana", HudAnchor.BOTTOM_CENTER, 81)
 *         .label("Mana").tone(HudBar.Tone.ACCENT).icon("minecraft:lapis_lazuli"));
 * mana.value(current / (float) max).text(current + "/" + max);
 * }</pre>
 */
@Incubating
public class HudBar extends HudPart {

    /** Which palette colour fills the bar. */
    public enum Tone {
        GOOD, WARN, BAD, ACCENT, PRIMARY;

        /** This tone's colour in {@code p}. */
        public int of(Palette p) {
            return switch (this) {
                case GOOD -> p.good();
                case WARN -> p.warn();
                case BAD -> p.bad();
                case ACCENT -> p.accent();
                case PRIMARY -> p.primary();
            };
        }
    }

    private static final int PAD = 4;
    private static final int BAR_H = 5;
    private static final int ICON = 16;
    private static final long EASE_MS = 120;

    private String label;
    private String text;
    private String itemId;
    private IconArt icon;
    private Tone tone = Tone.GOOD;
    private int color;
    private FillColor byValue;
    private float target;
    private float fill;
    private long lastMs = Long.MIN_VALUE;
    private boolean measure = true;
    private String labelShown;
    private int textWidth;

    /** @param width the whole part, panel included, in GUI pixels */
    public HudBar(String modId, String id, HudAnchor anchor, int width) {
        super(modId, id, anchor, width, PAD + BAR_H + PAD);
    }

    public HudBar label(String words) {
        this.label = words;
        resize();
        return this;
    }

    /** A short value on the right of the label row, such as {@code "17/20"}; null for none. */
    public HudBar text(String words) {
        if (words == null ? this.text != null : !words.equals(this.text)) {
            this.text = words;
            resize();
        }
        return this;
    }

    /** An item's icon on the left, 16 by 16. */
    public HudBar icon(String item) {
        this.itemId = item;
        this.icon = null;
        resize();
        return this;
    }

    public HudBar icon(IconArt art) {
        this.icon = art;
        this.itemId = null;
        resize();
        return this;
    }

    public HudBar tone(Tone fillTone) {
        this.tone = fillTone;
        this.color = 0;
        this.byValue = null;
        return this;
    }

    /** A fixed fill colour instead of the palette's. */
    public HudBar color(int argb) {
        this.color = argb;
        this.byValue = null;
        return this;
    }

    /** A fill colour that follows the value, such as {@link FillColor#zones}; null goes back to the tone. */
    public HudBar fillColor(FillColor byValue) {
        this.byValue = byValue;
        this.color = 0;
        return this;
    }

    /** 0 to 1. */
    public HudBar value(float v) {
        this.target = Math.max(0F, Math.min(1F, v));
        return this;
    }

    public float value() {
        return this.target;
    }

    private void resize() {
        this.measure = true;
        boolean words = this.label != null || this.text != null;
        int inner = words ? 8 + 2 + BAR_H : BAR_H;
        if (this.itemId != null || this.icon != null) {
            inner = Math.max(inner, this.icon != null ? this.icon.height() : ICON);
        }
        size(this.w, PAD + inner + PAD);
    }

    private int iconWidth() {
        if (this.icon != null) {
            return this.icon.width() + 3;
        }
        return this.itemId != null ? ICON + 3 : 0;
    }

    @Override
    protected void update(Draw d, float partial) {
        if (fontChanged()) {
            this.measure = true;
        }
        if (this.measure) {
            this.measure = false;
            this.textWidth = this.text == null ? 0 : d.width(this.text);
            int room = this.w - 2 * PAD - iconWidth() - this.textWidth - (this.textWidth > 0 ? 4 : 0);
            this.labelShown = this.label == null ? null : d.ellipsize(this.label, room);
        }
        long now = BaseComponent.clock().getAsLong();
        if (this.lastMs == Long.MIN_VALUE || BaseComponent.reducedMotion()) {
            this.fill = this.target;
        } else {
            float step = Math.min(1F, (now - this.lastMs) / (float) EASE_MS);
            this.fill += (this.target - this.fill) * step;
            if (Math.abs(this.target - this.fill) < 0.002F) {
                this.fill = this.target;
            }
        }
        this.lastMs = now;
    }

    private int fillColor(Palette p) {
        if (this.byValue != null) {
            return this.byValue.color(this.target, p);
        }
        if (this.color != 0) {
            return this.color;
        }
        return this.tone.of(p);
    }

    @Override
    protected void draw(Draw d, Theme theme, int x, int y, float partial) {
        Palette p = theme.palette();
        FrameStyle frame = theme.frame();
        frame.panel(d, p, x, y, this.w, this.h);
        int left = x + PAD;
        int inner = this.h - 2 * PAD;
        if (this.icon != null) {
            d.icon(this.icon, left, y + PAD + (inner - this.icon.height()) / 2, p.text());
        } else if (this.itemId != null) {
            d.item(this.itemId, left, y + PAD + (inner - ICON) / 2);
        }
        left += iconWidth();
        int right = x + this.w - PAD;
        int barTop = y + this.h - PAD - BAR_H;
        if (this.label != null || this.text != null) {
            int textTop = barTop - 2 - 8;
            if (this.text != null) {
                d.text(this.text, right - this.textWidth, textTop, p.textSoft());
            }
            if (this.labelShown != null) {
                d.text(this.labelShown, left, textTop, p.text());
            }
        }
        frame.bar(d, p, left, barTop, right - left, BAR_H, this.fill, fillColor(p));
    }
}
