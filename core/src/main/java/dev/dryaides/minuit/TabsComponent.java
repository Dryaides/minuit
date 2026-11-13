package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.ImageFamily;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A row of tabs over one page: the shown tab drawn as a chosen option, the others flat until
 * hovered, and a rule under the row. The tabs share the box in equal columns.
 */
public class TabsComponent extends BaseComponent {

    /** The default gap between tabs; a theme changes it through {@link FrameStyle#tabGap}. */
    public static final int GAP = 4;

    private final List<ButtonComponent> tabs = new ArrayList<>();
    private int index;
    /** Milliseconds the page glides in after a switch, eased out. */
    public static final int SLIDE_MS = 170;
    private Glide page = new Glide(Slide.RIGHT, SLIDE_MS);
    /** The gap and box the tab cells were last laid out for; render lays them out again when one changes. */
    /** How the shown tab shows; {@link PressStyle#OVERLAP} by default, {@link PressStyle#SEPARATE} the old ghost tabs. */
    private PressStyle press = PressStyle.OVERLAP;
    /** A bar under the shown tab's label; null follows the family (on for Bedrock, whose tabs carry one). */
    private Boolean underline;
    private boolean separator;
    private int laidGap = GAP, laidX, laidY, laidW, laidH;

    /** @param onSelect the index of the tab clicked, after it is shown */
    public TabsComponent(Box box, List<String> labels, int index, IntConsumer onSelect) {
        super(box);
        Box[] cells = box.columns(labels.size(), GAP);
        for (int i = 0; i < labels.size(); i++) {
            int tab = i;
            this.tabs.add(new ButtonComponent(cells[i], labels.get(i), () -> {
                if (tab != this.index) {
                    this.page = new Glide(tab > this.index ? Slide.RIGHT : Slide.LEFT, SLIDE_MS);
                    this.page.replay();
                }
                select(tab);
                onSelect.accept(tab);
            }).style(ButtonComponent.Style.GHOST));
        }
        select(index);
        this.laidX = this.x;
        this.laidY = this.y;
        this.laidW = this.w;
        this.laidH = this.h;
    }

    /**
     * Lays the tab cells out again when the theme's {@link FrameStyle#tabGap} or this box changed, in
     * place, so the hit boxes clicks test are the cells drawn. Same split as {@link Box#columns}.
     */
    private void layout(int gap) {
        if (gap == this.laidGap && this.x == this.laidX && this.y == this.laidY && this.w == this.laidW
                && this.h == this.laidH) {
            return;
        }
        int count = this.tabs.size();
        int width = (this.w - gap * (count - 1)) / Math.max(1, count);
        for (int i = 0; i < count; i++) {
            ButtonComponent tab = this.tabs.get(i);
            tab.x = this.x + i * (width + gap);
            tab.y = this.y;
            tab.w = i == count - 1 ? this.x + this.w - tab.x : width;
            tab.h = this.h;
        }
        this.laidGap = gap;
        this.laidX = this.x;
        this.laidY = this.y;
        this.laidW = this.w;
        this.laidH = this.h;
    }

    /** Sets how the shown tab shows; {@link PressStyle#RAISED_SELECTED} is an option only, never a default. */
    public TabsComponent style(PressStyle press) {
        this.press = press;
        return this;
    }

    public PressStyle style() {
        return this.press;
    }

    /** Draws a bar under the shown tab's label, or not; null, the default, draws it for the Bedrock family only. */
    public TabsComponent underline(Boolean value) {
        this.underline = value;
        return this;
    }

    /** Draws a line under the whole tab row, off by default; a separator component is the usual way to divide a page. */
    public TabsComponent separator(boolean value) {
        this.separator = value;
        return this;
    }

    private static boolean bedrock(FrameStyle style) {
        FrameStyle shape = style instanceof GlassFrame glass ? glass.base() : style;
        return (shape instanceof ImageFamily images ? images.base() : shape) == FrameStyles.BEDROCK;
    }

    public int count() {
        return this.tabs.size();
    }

    public int index() {
        return this.index;
    }

    /**
     * Greys out tab {@code index}: drawn disabled, skipped by clicks and by the keyboard. The
     * {@code tip} lines show over it, to say why; none shows nothing.
     */
    public TabsComponent disabled(int index, boolean disabled, String... tip) {
        ButtonComponent tab = this.tabs.get(index);
        tab.active = !disabled;
        tab.tooltip(tip);
        return this;
    }

    public boolean isDisabled(int index) {
        return !this.tabs.get(index).active;
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        for (ButtonComponent tab : this.tabs) {
            if (tab.tooltip != null && tab.isOver(mx, my)) {
                return tab.tooltip;
            }
        }
        return super.tooltipAt(mx, my);
    }

    /**
     * The x offset to draw the shown page at, so it glides in from the side of the tab clicked:
     * {@code width} is the page width, the glide 12 % of it. 0 at rest and under {@link #reducedMotion()}.
     */
    public int pageOffset(int width) {
        return Math.round(this.page.offsetX(width * 0.12F));
    }

    public void select(int index) {
        this.index = index;
        for (int i = 0; i < this.tabs.size(); i++) {
            this.tabs.get(i).selected(i == index);
        }
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        FrameStyle style = theme.frame();
        if (this.press != PressStyle.SEPARATE) {
            layout(0);
            joined(d, theme, style, mx, my);
            return;
        }
        int gap = style.tabGap();
        layout(gap);
        if (style.gameWidgets()) {
            Palette p = theme.palette();
            int n = this.tabs.size();
            for (int k = 0; k < n; k++) {
                // Overlapping tabs: the shown one is drawn last, over its neighbours' edges.
                int i = gap >= 0 || this.index < 0 || this.index >= n ? k
                        : k == n - 1 ? this.index : k >= this.index ? k + 1 : k;
                ButtonComponent tab = this.tabs.get(i);
                boolean shown = i == this.index;
                boolean lit = tab.active && (tab.focused || tab.isOver(mx, my));
                style.tab(d, p, tab.x, tab.y, tab.w, tab.h, shown, lit);
                int top = tab.y + style.tabInset(tab.h, shown) + (shown ? 2 : 1);
                int bottom = tab.y + tab.h - (shown ? 2 : 3);
                int fit = Math.min(d.width(tab.label), tab.w - 6);
                d.textIn(tab.label, new Box("", tab.x + (tab.w - fit) / 2, top, tab.w - 6, bottom - top),
                        style.tabText(p, shown, tab.active));
                if (i + 1 < n && gap > 0) {
                    int left = tab.x + tab.w;
                    style.tabRule(d, p, left, tab.y, this.tabs.get(i + 1).x - left, tab.h);
                }
            }
            return;
        }
        for (int i = 0; i < this.tabs.size(); i++) {
            if (gap >= 0 || i != this.index) {
                this.tabs.get(i).draw(d, theme, mx, my, this.delta);
            }
        }
        if (gap < 0 && this.index >= 0 && this.index < this.tabs.size()) {
            this.tabs.get(this.index).draw(d, theme, mx, my, this.delta);
        }
        if (this.separator) {
            d.fill(this.x, this.y + this.h + 2, this.x + this.w, this.y + this.h + 3, theme.palette().surfaceEdge());
        }
    }

    /** The tabs as one joined row in {@link #press}, labels on the faces, and the optional bar under the shown one. */
    private void joined(Draw d, Theme theme, FrameStyle style, int mx, int my) {
        Palette p = theme.palette();
        int n = this.tabs.size();
        int[] xs = new int[n + 1];
        int[] faces = new int[n];
        boolean[] shown = new boolean[n];
        for (int i = 0; i < n; i++) {
            ButtonComponent tab = this.tabs.get(i);
            xs[i] = tab.x;
            shown[i] = tab.active && i == this.index;
            float hover = tab.active && (tab.focused || tab.isOver(mx, my)) ? 1F : 0F;
            faces[i] = !tab.active ? p.disabled() : shown[i] ? style.selectedFace(p, hover)
                    : Shapes.mix(p.raised(), p.raisedHover(), hover);
        }
        xs[n] = this.x + this.w;
        int[] shift = this.press.row(d, p, style, xs, this.y, this.h, faces, shown);
        int faceH = PressStyle.faceHeight(style, this.h);
        boolean bar = this.underline != null ? this.underline : bedrock(style);
        for (int i = 0; i < n; i++) {
            ButtonComponent tab = this.tabs.get(i);
            int ink = !tab.active ? p.disabledText() : shown[i] ? Shapes.textOn(p, faces[i]) : p.raisedText();
            int room = tab.w - 6;
            int fit = Math.min(room, d.width(tab.label));
            int lx = tab.x + (tab.w - fit) / 2;
            d.textShrink(tab.label, lx, this.y + shift[i], room, faceH, 0.75F, ink);
            if (bar && shown[i]) {
                // Bedrock's own tab rule runs under the middle of the label, not its whole width.
                int by = this.y + shift[i] + (faceH + 9) / 2 + 1;
                int u = Math.max(4, fit / 2);
                d.fill(lx + (fit - u) / 2, by, lx + (fit - u) / 2 + u, by + 1, ink);
            } else if (shown[i]) {
                style.selectedMark(d, p, tab.x, this.y + shift[i], tab.w, this.h - shift[i], faces[i]);
            }
        }
        if (this.separator) {
            d.fill(this.x, this.y + this.h + 2, this.x + this.w, this.y + this.h + 3, p.surfaceEdge());
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.active) {
            return false;
        }
        for (ButtonComponent tab : this.tabs) {
            if (tab.mouseClicked(mx, my, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<BaseComponent> stops() {
        return this.active ? this.tabs.stream().filter(tab -> tab.active).map(tab -> (BaseComponent) tab).toList()
                : List.of();
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        this.tabs.forEach(tab -> tab.mouseReleased(mx, my, button));
        return false;
    }
}
