package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Accent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A column of square tool tabs standing against one side of a panel, like the theme tab: sort, lock,
 * copy a layout, open settings. The mod fills it with its own actions; each tab shows one icon and a
 * tooltip. A one-shot tab runs its action on a click; a toggle tab flips its own on state. A tab that is
 * on, or that the mod marks {@link #selected}, wears the
 * family's selected face. Tab, Shift+Tab and the arrows walk the tabs; Space or Enter presses one.
 */
@Incubating
public class SideRailComponent extends BaseComponent {

    public enum Edge {
        LEFT,
        RIGHT
    }

    /** How the tabs stack and show a press; every style draws with the family's own button. */
    public enum Style {
        /** Tabs overlap so each covers the lip of the one above; a selected tab sinks behind its lower neighbour. */
        OVERLAP,
        /** Tabs overlap, all pushed flat; only a selected tab stands raised on its lip. */
        RAISED_SELECTED,
        /** The lip runs along the outer side; a selected tab drops it and slides toward the panel. */
        SIDEWAYS,
        /** Square tabs side by side, a selected tab wearing the family's selected face. */
        SEPARATE
    }

    /** A tab's side, across and along the edge. */
    public static final int SIZE = 16;
    /** Room a host leaves beside the rail; tabs no longer stick out, so every tab keeps one size. */
    public static final int POP = 2;
    /** Margin around a mod's own icon art inside its tab. */
    private static final int ART_PAD = 2;
    /** From the panel's top to the first tab. */
    static final int INSET = 6;

    private final Edge edge;
    private final List<Tab> tabs = new ArrayList<>();
    private Box panel;
    private Style style = Style.OVERLAP;
    /** The family last drawn with: its lip and press travel set the tab geometry. */
    private FrameStyle frame;

    /** A rail against {@code edge} of {@code panel}; it grows downward as tabs are added. */
    public SideRailComponent(Box panel, Edge edge) {
        super(new Box("side rail", 0, 0, SIZE + POP, 0));
        this.edge = edge;
        attach(panel);
    }

    /** Moves the rail to {@code panel}'s edge, after the panel moved or changed size. */
    public SideRailComponent attach(Box panel) {
        this.panel = panel;
        int width = width();
        this.x = this.edge == Edge.LEFT ? panel.x() - width : panel.x() + panel.w();
        this.y = panel.y() + INSET;
        this.w = width;
        this.h = this.tabs.isEmpty() ? 0 : (this.tabs.size() - 1) * pitch() + tabHeight();
        for (Tab tab : this.tabs) {
            tab.place();
        }
        return this;
    }

    /** Sets how the tabs stack and show a press; {@link Style#OVERLAP} by default; RAISED_SELECTED is its inverse, an option only. */
    public SideRailComponent style(Style style) {
        this.style = style;
        return attach(this.panel);
    }

    public Style style() {
        return this.style;
    }

    /** Sets the rail's press style shared with the toggle group and tabs. */
    public SideRailComponent style(PressStyle press) {
        return style(switch (press) {
            case OVERLAP -> Style.OVERLAP;
            case RAISED_SELECTED -> Style.RAISED_SELECTED;
            case SEPARATE -> Style.SEPARATE;
        });
    }

    private boolean overlaps() {
        return this.style == Style.OVERLAP || this.style == Style.RAISED_SELECTED;
    }

    /** The lip rows an overlapping tab stands on; 0 for a family whose buttons have none. */
    int lip() {
        if (this.frame == null || !overlaps()) {
            return 0;
        }
        int side = side();
        return this.frame.buttonLip(side + 2 * this.frame.buttonLip(side));
    }

    /** Columns a sideways tab's lip takes, and how far a pushed one slides. */
    int sideTravel() {
        return Math.max(2, this.frame == null ? 1 : Math.max(this.frame.pressTravel(), this.frame.buttonLip(side())));
    }

    /**
     * A drawn tab's height. An overlapping tab adds two lips to its square: one its lower neighbour covers,
     * one it sinks by, so a sunk tab under a raised neighbour still shows its whole icon.
     */
    int tabHeight() {
        return side() + 2 * lip();
    }

    /** From one tab's top to the next: overlapping tabs cover the lip and bottom edge of the one above. */
    int pitch() {
        int lip = lip();
        return lip > 0 ? tabHeight() - 1 - lip : this.style == Style.SIDEWAYS ? side() - 1 : side();
    }

    /** The rail's width beside the panel: a sideways rail adds the lip columns. */
    int width() {
        return this.style == Style.SIDEWAYS ? side() + sideTravel() : side();
    }

    /** True when {@code tab} draws pushed: the selected one in OVERLAP and SIDEWAYS, the others in RAISED_SELECTED. */
    private boolean pushed(Tab tab) {
        return this.style == Style.RAISED_SELECTED ? PressStyle.RAISED_SELECTED.pushed(tab.on) : tab.on;
    }

    /** A tab that runs {@code run} on each press. */
    public SideRailComponent action(Icon icon, String tooltip, Runnable run) {
        return add(new Tab(icon, null, tooltip, false, false, on -> run.run()));
    }

    /** A one-shot tab with the mod's own art instead of a kit icon. */
    public SideRailComponent action(IconArt art, String tooltip, Runnable run) {
        return add(new Tab(null, art, tooltip, false, false, on -> run.run()));
    }

    /** A tab that flips between on and off; {@code onChange} hears the new state after each press. */
    public SideRailComponent toggle(Icon icon, String tooltip, boolean on, Consumer<Boolean> onChange) {
        return add(new Tab(icon, null, tooltip, true, on, onChange));
    }

    public SideRailComponent toggle(IconArt art, String tooltip, boolean on, Consumer<Boolean> onChange) {
        return add(new Tab(null, art, tooltip, true, on, onChange));
    }

    /** One square side for every tab: {@link #SIZE}, or more when a mod's art needs room. */
    int side() {
        int side = SIZE;
        for (Tab tab : this.tabs) {
            if (tab.art != null) {
                side = Math.max(side, Math.max(tab.art.width(), tab.art.height()) + 2 * ART_PAD);
            }
        }
        return side;
    }

    private SideRailComponent add(Tab tab) {
        this.tabs.add(tab);
        return attach(this.panel);
    }

    public int count() {
        return this.tabs.size();
    }

    public Edge edge() {
        return this.edge;
    }

    /** True when tab {@code index} is on (a toggle) or marked selected. */
    public boolean isSelected(int index) {
        return this.tabs.get(index).on;
    }

    /** Marks tab {@code index} selected or not without telling its listener: the page a one-shot tab opened, a toggle set from outside. */
    public SideRailComponent selected(int index, boolean on) {
        this.tabs.get(index).on = on;
        attach(this.panel);
        return this;
    }

    /** Swaps tab {@code index}'s icon and tooltip, for a tab whose meaning follows its state (lock and unlock). */
    public SideRailComponent look(int index, Icon icon, String tooltip) {
        Tab tab = this.tabs.get(index);
        tab.icon = icon;
        tab.art = null;
        tab.tooltip(tooltip);
        return this;
    }

    /** As {@link #look(int, Icon, String)} with the mod's own art. */
    public SideRailComponent look(int index, IconArt art, String tooltip) {
        Tab tab = this.tabs.get(index);
        tab.icon = null;
        tab.art = art;
        tab.tooltip(tooltip);
        return this;
    }

    /** Greys out tab {@code index}: drawn disabled, skipped by clicks and the keyboard; {@code tip} says why. */
    public SideRailComponent disabled(int index, boolean disabled, String... tip) {
        Tab tab = this.tabs.get(index);
        tab.active = !disabled;
        if (tip.length > 0) {
            tab.tooltip(tip);
        }
        return this;
    }

    /** What a click or Space on tab {@code index} does. */
    public void press(int index) {
        Tab tab = this.tabs.get(index);
        if (!this.active || !tab.active) {
            return;
        }
        if (tab.toggles) {
            tab.on = !tab.on;
            attach(this.panel);
        }
        tab.onPress.accept(tab.on);
    }

    @Override
    public boolean isOver(double mx, double my) {
        for (Tab tab : this.tabs) {
            if (tab.isOver(mx, my)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        for (Tab tab : this.tabs) {
            if (tab.tooltip != null && tab.isOver(mx, my)) {
                return tab.tooltip;
            }
        }
        return null;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        if (this.frame != theme.frame()) {
            this.frame = theme.frame();
            attach(this.panel);
        }
        // Top to bottom: a tab covers the lip of the one above, and a sunk tab goes under its lower neighbour.
        // Sideways, pushed tabs go first so their neighbours keep the shared outline rows.
        boolean sideways = this.style == Style.SIDEWAYS;
        for (Tab tab : this.tabs) {
            if (!sideways || pushed(tab)) {
                tab.draw(d, theme, mx, my, this.delta);
            }
        }
        if (sideways) {
            for (Tab tab : this.tabs) {
                if (!pushed(tab)) {
                    tab.draw(d, theme, mx, my, this.delta);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.active || button != 0) {
            return false;
        }
        for (int i = 0; i < this.tabs.size(); i++) {
            Tab tab = this.tabs.get(i);
            if (tab.active && tab.isOver(mx, my)) {
                click();
                press(i);
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

    private final class Tab extends BaseComponent {
        Icon icon;
        IconArt art;
        final boolean toggles;
        final Consumer<Boolean> onPress;
        boolean on;

        Tab(Icon icon, IconArt art, String tooltip, boolean toggles, boolean on, Consumer<Boolean> onPress) {
            super(new Box("rail tab", 0, 0, SIZE, SIZE));
            this.icon = icon;
            this.art = art;
            this.toggles = toggles;
            this.on = on;
            this.onPress = onPress;
            tooltip(tooltip);
        }

        int index() {
            return SideRailComponent.this.tabs.indexOf(this);
        }

        /** The tab's hit box: the strip of the rail it shows, its neighbours' overlaps left out. */
        void place() {
            SideRailComponent rail = SideRailComponent.this;
            int i = index();
            this.w = rail.width();
            this.h = i == rail.tabs.size() - 1 ? rail.tabHeight() : rail.pitch();
            this.y = rail.y + i * rail.pitch();
            this.x = rail.x;
        }

        @Override
        protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
            Palette p = theme.palette();
            FrameStyle style = theme.frame();
            boolean usable = this.active && SideRailComponent.this.active;
            int face;
            int ink;
            Shapes.Relief relief = Shapes.Relief.RAISED;
            if (!usable) {
                face = Shapes.luminance(p.disabled()) < Shapes.luminance(p.raised()) && !Accent.dark(p)
                        ? p.raised() : p.disabled();
                ink = p.disabledText();
            } else if (this.on) {
                face = style.selectedFace(p, this.hoverAnim);
                ink = Shapes.textOn(p, face);
                relief = style.selectedRelief();
            } else {
                face = Shapes.mix(p.raised(), p.raisedHover(), this.hoverAnim);
                ink = p.raisedText();
            }
            IconArt shown = this.art != null ? this.art : theme.icon(this.icon);
            SideRailComponent rail = SideRailComponent.this;
            if (rail.style == Style.SEPARATE) {
                style.button(d, p, this.x, this.y, this.w, this.h, null, face, relief);
                icon(d, shown, ink, this.x, this.y, this.x + this.w, this.y + this.h, 0, 0);
            } else if (rail.style == Style.SIDEWAYS) {
                sideways(d, p, style, face, shown, ink, usable && rail.pushed(this));
            } else {
                overlapping(d, p, style, face, shown, ink, usable && rail.pushed(this));
            }
        }

        /** {@code art} centred in the box, moved by dx, dy and cut to the box. */
        private void icon(Draw d, IconArt art, int ink, int x0, int y0, int x1, int y1, int dx, int dy) {
            d.scissor(x0, y0, x1, y1);
            d.icon(art, x0 + (x1 - x0 - art.width()) / 2 + dx, y0 + (y1 - y0 - art.height()) / 2 + dy, ink);
            d.endScissor();
        }

        private void overlapping(Draw d, Palette p, FrameStyle style, int face, IconArt shown, int ink, boolean pushed) {
            SideRailComponent rail = SideRailComponent.this;
            int lip = rail.lip();
            int h = rail.tabHeight();
            int y = this.y;
            int sunk = pushed ? PressStyle.sink(style, lip) : 0;
            PressStyle.segment(d, p, style, this.x, y, this.w, h, face, sunk);
            // The icon centres on the face a raised lower neighbour leaves in sight (it covers lip + 1 rows).
            icon(d, shown, ink, this.x + 1, y + 1 + (pushed ? lip : 0), this.x + this.w - 1, y + h - 1 - lip, 0,
                    pushed && lip == 0 ? 1 : 0);
        }

        /**
         * A tab whose lip runs along the outer side. It is {@link #sideTravel} wider than the rail shows: that
         * part tucks under the panel's edge. A pushed tab drops its lip and slides that far toward the panel,
         * leaving the strip outside it bare.
         */
        private void sideways(Draw d, Palette p, FrameStyle style, int face, IconArt shown, int ink, boolean pushed) {
            SideRailComponent rail = SideRailComponent.this;
            boolean left = rail.edge == Edge.LEFT;
            int t = rail.sideTravel();
            int h = rail.side();
            int y = this.y;
            int vx0 = this.x;
            int vx1 = this.x + this.w;
            int tw = this.w + t;
            int x0 = left ? vx0 : vx0 - t;
            int fx = pushed ? (left ? x0 + t : x0 - t) : x0;
            d.scissor(vx0, y, vx1, y + h);
            lipless(d, p, style, fx, y, tw, h, face);
            if (!pushed) {
                int lx = left ? x0 + 1 : x0 + tw - 1 - t;
                d.fill(lx, y + 1, lx + t, y + h - 1, Shapes.mix(face, 0xFF000000, 0.22F));
            }
            d.endScissor();
            int faceX0 = left ? x0 + 1 + t : vx0;
            int faceX1 = left ? vx1 : x0 + tw - 1 - t;
            int shift = pushed ? (left ? 1 : -1) * Math.min(2, t - 1) : 0;
            icon(d, shown, ink, faceX0, y + 1, faceX1, y + h - 1, shift, 0);
        }

        /**
         * The family's button with no lip at its foot: drawn a lip taller and cut above the lip, then the
         * bottom edge row taken from a button of the plain height.
         */
        private void lipless(Draw d, Palette p, FrameStyle style, int x, int y, int w, int h, int face) {
            int lip = style.buttonLip(h + style.buttonLip(h));
            if (lip == 0) {
                style.button(d, p, x, y, w, h, null, face, Shapes.Relief.RAISED, 0);
                return;
            }
            d.scissor(x, y, x + w, y + h - 1);
            style.button(d, p, x, y, w, h + lip, null, face, Shapes.Relief.RAISED, 0);
            d.endScissor();
            d.scissor(x, y + h - 1, x + w, y + h);
            style.button(d, p, x, y, w, h, null, face, Shapes.Relief.RAISED, 0);
            d.endScissor();
        }

        @Override
        protected boolean navigable() {
            return true;
        }

        @Override
        protected boolean activate() {
            if (!this.active) {
                return false;
            }
            click();
            press(index());
            return true;
        }
    }
}
