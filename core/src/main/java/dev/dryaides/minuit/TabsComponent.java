package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A row of tabs over one page: the shown tab drawn as a chosen option, the others flat until
 * hovered, and a rule under the row. The tabs share the box in equal columns.
 */
public class TabsComponent extends BaseComponent {

    public static final int GAP = 4;

    private final List<ButtonComponent> tabs = new ArrayList<>();
    private int index;
    /** Milliseconds the page glides in after a switch, eased out. */
    public static final int SLIDE_MS = 170;
    private Glide page = new Glide(Slide.RIGHT, SLIDE_MS);

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
     * {@code width} is the page width, the glide 12 % of it. 0 at rest and under {@link #reducedMotion}.
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
        if (style.gameWidgets()) {
            Palette p = theme.palette();
            for (int i = 0; i < this.tabs.size(); i++) {
                ButtonComponent tab = this.tabs.get(i);
                boolean shown = i == this.index;
                boolean lit = tab.active && (tab.focused || tab.isOver(mx, my));
                style.tab(d, p, tab.x, tab.y, tab.w, tab.h, shown, lit);
                int top = tab.y + style.tabInset(tab.h, shown) + (shown ? 2 : 1);
                int bottom = tab.y + tab.h - (shown ? 2 : 3);
                int fit = Math.min(d.width(tab.label), tab.w - 6);
                d.textIn(tab.label, new Box("", tab.x + (tab.w - fit) / 2, top, tab.w - 6, bottom - top),
                        style.tabText(p, shown, tab.active));
                if (i + 1 < this.tabs.size()) {
                    int gap = tab.x + tab.w;
                    style.tabRule(d, p, gap, tab.y, this.tabs.get(i + 1).x - gap, tab.h);
                }
            }
            return;
        }
        for (ButtonComponent tab : this.tabs) {
            tab.draw(d, theme, mx, my, this.delta);
        }
        d.fill(this.x, this.y + this.h + 2, this.x + this.w, this.y + this.h + 3, theme.palette().surfaceEdge());
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
