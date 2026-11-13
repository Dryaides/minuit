package dev.dryaides.minuit.internal.showcase;

import dev.dryaides.minuit.AsyncImage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.ColorPickerComponent;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.DropdownComponent;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.EmptyStateComponent;
import dev.dryaides.minuit.MultiStopToggleComponent;
import dev.dryaides.minuit.GridComponent;
import dev.dryaides.minuit.Icon;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.KeyPress;
import dev.dryaides.minuit.ListComponent;
import dev.dryaides.minuit.MeterComponent;
import dev.dryaides.minuit.OverlayLayer;
import dev.dryaides.minuit.Palette;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.PlayerHeadComponent;
import dev.dryaides.minuit.ProgressComponent;
import dev.dryaides.minuit.RadialComponent;
import dev.dryaides.minuit.ReelComponent;
import dev.dryaides.minuit.ScrollComponent;
import dev.dryaides.minuit.SectionComponent;
import dev.dryaides.minuit.Shapes;
import dev.dryaides.minuit.SliderComponent;
import dev.dryaides.minuit.SpinnerComponent;
import dev.dryaides.minuit.MotionStyle;
import dev.dryaides.minuit.StepperComponent;
import dev.dryaides.minuit.TabsComponent;
import dev.dryaides.minuit.ToggleGroupComponent;
import dev.dryaides.minuit.TextFieldComponent;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.ToggleComponent;
import dev.dryaides.minuit.WaveComponent;
import dev.dryaides.minuit.WindowComponent;
import dev.dryaides.minuit.Words;

import java.util.ArrayList;
import java.util.List;

/**
 * The scenes the showcase site shows, one per component and variant. Pure Java on purpose: the
 * offline bench (SiteExport) draws them into the static renders, the browser build (:web) runs
 * the same objects live and the game's demo screen ({@link ShowcasePanel}) hosts them, so a preview
 * box, its fallback picture and the game cannot drift apart. Scenes set no game-wide state: the
 * site and the bench plug in their own item lookup ({@link McScenes#LOOKUP}) and gallery.
 */
public final class SiteScenes {

    /** The radial demos' quick menu: each name says what its icon shows. */
    private static final java.util.Map<String, Icon> QUICK = java.util.Map.ofEntries(
            java.util.Map.entry("Delete", Icon.TRASH), java.util.Map.entry("Lock", Icon.LOCK),
            java.util.Map.entry("Back", Icon.BACK), java.util.Map.entry("Warn", Icon.WARN),
            java.util.Map.entry("Stop", Icon.STOP), java.util.Map.entry("Reload", Icon.REFRESH),
            java.util.Map.entry("Play", Icon.PLAY), java.util.Map.entry("Folder", Icon.FOLDER),
            java.util.Map.entry("Download", Icon.DOWNLOAD), java.util.Map.entry("Web", Icon.GLOBE),
            java.util.Map.entry("Colours", Icon.PALETTE), java.util.Map.entry("Report", Icon.BUG),
            java.util.Map.entry("Light", Icon.SUN), java.util.Map.entry("Dark", Icon.MOON));

    public static final int W = 427;
    public static final int H = 240;
    public static final int PAD = 6;
    public static final Words WORDS = (key, args) -> key;
    /**
     * The scenes of {@link #GALLERY}, which replay the component bench's demos: the site and the bench plug them in,
     * the mod has none (null answers).
     */
    public static volatile java.util.function.BiFunction<String, Theme, Stage> gallery = (id, t) -> null;

    /**
     * The picture of a map in {@link #MAPS}: one face of the game's title panorama, which every version ships. The
     * site plugs in its own map shots.
     */
    public static volatile java.util.function.Function<String, AsyncImage> thumbs = s -> AsyncImage.of(
            "minecraft:textures/gui/title/background/panorama_" + Math.floorMod(SiteScenes.MAPS.indexOf(s), 6) + ".png", 256, 256);

    public static final List<String> MAPS = List.of("Baroque Palace", "Dome of Islands", "Polaroid", "Circus", "Galleon", "Gluttonary", "Trainstation", "Spaceship");

    /** The 1.1.0 components: one state each of the component bench's demos (componentbench/bench/demos). */
    public static final List<String> GALLERY = List.of("accordion/closed", "accordion/open", "alert/variants", "avatar/initials", "avatar/sizes",
            "avatargroup/fit", "avatargroup/more", "badge/variants", "badge/counts", "breadcrumb/fits",
            "breadcrumb/folded", "calendar/month", "calendar/range", "card/full", "carousel/slides",
            "chart/line", "chart/bar", "checkbox/states", "combobox/items", "commandpalette/open",
            "commandpalette/search", "contextmenu/menu", "datepicker/filled", "dialog/confirm", "donutchart/donut",
            "donutchart/pie", "drawer/right", "formfield/error", "formsummary/list", "gauge/zones",
            "gauge/half", "heatmap/calendar", "heatmap/grid", "hovercard/open", "kanban/board",
            "lightbox/grid", "lightbox/open", "map/paper", "menubar/open", "modelpreview/card",
            "numberfield/field", "otpinput/typing", "pagination/middle", "picture/cover", "popover/below",
            "radiogroup/choices", "rangeslider/range", "reorderlist/rows", "scene3d/house", "sidebar/nav",
            "sidebar/rail", "skeleton/kinds", "sparkline/inline", "splitpane/split", "steps/midway",
            "table/rows", "taginput/chips", "textarea/text", "timeline/events", "tree/rows",
            "treetable/open", "wheel/items");

    /** Every scene, in the order the site lists them: {@code component/variant}. */
    public static final List<String> ALL = List.of("button/roles", "button/tooltip", "toggle/states", "slider/values",
            "dropdown/open", "textfield/states", "tabs/rows", "toggle-group/rows", "list/rows", "progress/states",
            "meter/levels", "panel/sections", "scroll/rows", "grid/cards", "section/open-shut", "window/tools",
            "radial/wheel", "reel/cards", "colorpicker/alpha", "spinner/ring");

    /** Every scene: the ones above, then the slots and the Minecraft components of {@link McScenes}. */
    public static final List<String> EVERY = java.util.stream.Stream.concat(java.util.stream.Stream.concat(
            java.util.stream.Stream.concat(ALL.stream(), java.util.stream.Stream.of("slots/display", "slots/hud")), McScenes.ALL.stream()),
            GALLERY.stream()).toList();

    private static int windows;

    private SiteScenes() {
    }

    /**
     * What one scene draws on a canvas of {@code w} by {@code h} GUI pixels: panels (which route
     * input and focus themselves) and bare components, moved by {@code dx, dy}. {@code mx, my} is
     * where the static render puts the mouse, in canvas pixels.
     */
    public static final class Stage {
        public final String id;
        public final int w;
        public final int h;
        public final int mx;
        public final int my;
        /** Frames the static render draws before saving, so delays and blinks settle. */
        public final int frames;
        final int dx;
        final int dy;
        final Theme theme;
        final List<PanelComponent> panels = new ArrayList<>();
        final List<BaseComponent> parts = new ArrayList<>();
        /** The popups of the stage's panels and parts while the stage is the root (a site canvas), in canvas pixels. */
        private final OverlayLayer layer = new OverlayLayer();
        private BaseComponent focus;

        public Stage(String id, Theme theme, int w, int h, int dx, int dy, int mx, int my, int frames) {
            this.id = id;
            this.theme = theme;
            this.w = w;
            this.h = h;
            this.dx = dx;
            this.dy = dy;
            this.mx = mx;
            this.my = my;
            this.frames = frames;
        }

        public Stage panel(PanelComponent panel) {
            this.panels.add(panel);
            panel.overlayArea(new Box("canvas", -this.dx, -this.dy, this.w, this.h));
            return this;
        }

        public Stage part(BaseComponent part) {
            this.parts.add(part);
            return this;
        }

        /** One frame with the mouse at {@code mx, my} in canvas pixels. */
        public void draw(Draw d, int mx, int my) {
            drawBody(d, mx, my);
            List<String> popupTip = this.layer.tooltipAt(mx, my);
            if (popupTip != null) {
                if (!popupTip.isEmpty()) {
                    d.tooltip(popupTip, mx, this.layer.tooltipY(mx, my));
                }
                return;
            }
            int x = mx - this.dx;
            int y = my - this.dy;
            d.push(this.dx, this.dy, 1F);
            for (PanelComponent p : this.panels) {
                p.drawTooltip(d, x, y);
            }
            for (BaseComponent c : this.parts) {
                List<String> tip = c.isOver(x, y) ? c.tooltipAt(x, y) : null;
                if (tip != null && !tip.isEmpty()) {
                    d.tooltip(tip, x, y);
                }
            }
            d.pop();
        }

        /** The frame without tooltips, for a host that draws the tip itself (the game's demo screen scales the stage). */
        public void drawBody(Draw d, int mx, int my) {
            boolean root = this.layer.begin(d, true);
            int x = mx - this.dx;
            int y = my - this.dy;
            d.push(this.dx, this.dy, 1F);
            for (PanelComponent p : this.panels) {
                p.draw(d, x, y, 1F);
            }
            for (BaseComponent c : this.parts) {
                c.draw(d, this.theme, x, y, 1F);
            }
            d.pop();
            if (root) {
                this.layer.flush(d, mx, my);
            }
        }

        /** Hands the stage's widgets to the inspector, the canvas drawn at {@code left, top} and {@code scale}. */
        void inspect(dev.dryaides.minuit.internal.Inspector.Walk walk, float left, float top, float scale) {
            float ox = left + this.dx * scale;
            float oy = top + this.dy * scale;
            for (PanelComponent p : this.panels) {
                walk.widgets(p.widgets(), ox, oy, scale);
            }
            walk.widgets(this.parts, ox, oy, scale);
        }

        /** The tooltip at {@code mx, my} in canvas pixels, or null. */
        public List<String> tooltipAt(double mx, double my) {
            List<String> popupTip = this.layer.tooltipAt(mx, my);
            if (popupTip != null) {
                return popupTip;
            }
            double x = mx - this.dx;
            double y = my - this.dy;
            for (PanelComponent p : this.panels) {
                List<String> tip = p.tooltipAt(x, y);
                if (tip != null && !tip.isEmpty()) {
                    return tip;
                }
            }
            for (BaseComponent c : this.parts) {
                List<String> tip = c.isOver(x, y) ? c.tooltipAt(x, y) : null;
                if (tip != null && !tip.isEmpty()) {
                    return tip;
                }
            }
            return null;
        }

        /** True while a field of the stage waits for a raw key (a key binding field listening). */
        public boolean capturing() {
            for (PanelComponent p : this.panels) {
                if (p.capturing()) {
                    return true;
                }
            }
            return false;
        }

        /** {@code s} with the mouse resting outside, so no item tooltip shows before the visitor points at one. */
        public Stage away() {
            Stage out = new Stage(this.id, this.theme, this.w, this.h, this.dx, this.dy, -100, -100, this.frames);
            out.panels.addAll(this.panels);
            out.parts.addAll(this.parts);
            return out;
        }

        /** A game tick, 20 a second. */
        public void tick() {
            this.panels.forEach(PanelComponent::tickAll);
            this.parts.forEach(BaseComponent::tick);
        }

        public boolean click(double mx, double my, int button) {
            if (this.layer.mouseClicked(mx, my, button)) {
                return true;
            }
            double x = mx - this.dx;
            double y = my - this.dy;
            if (this.focus != null) {
                this.focus.focused = false;
            }
            this.focus = null;
            for (int i = this.parts.size() - 1; i >= 0; i--) {
                BaseComponent c = this.parts.get(i);
                if (c.mouseClicked(x, y, button)) {
                    this.focus = c;
                    c.focused = true;
                    return true;
                }
            }
            for (PanelComponent p : this.panels) {
                if (p.mouseClicked(x, y, button)) {
                    return true;
                }
            }
            return false;
        }

        /** A key or button for the widget that is capturing one (a keybind field). */
        public boolean rawKey(KeyPress k) {
            for (PanelComponent p : this.panels) {
                if (p.rawKey(k)) {
                    return true;
                }
            }
            return false;
        }

        public void release(double mx, double my, int button) {
            this.layer.mouseReleased(mx, my, button);
            this.parts.forEach(c -> c.mouseReleased(mx - this.dx, my - this.dy, button));
            this.panels.forEach(p -> p.mouseReleased(mx - this.dx, my - this.dy, button));
        }

        public void drag(double mx, double my, int button, double ddx, double ddy) {
            if (this.layer.mouseDragged(mx, my, button, ddx, ddy)) {
                return;
            }
            this.parts.forEach(c -> c.mouseDragged(mx - this.dx, my - this.dy, button, ddx, ddy));
            this.panels.forEach(p -> p.mouseDragged(mx - this.dx, my - this.dy, button, ddx, ddy));
        }

        public boolean scroll(double mx, double my, double amount) {
            if (this.layer.mouseScrolled(mx, my, amount)) {
                return true;
            }
            double x = mx - this.dx;
            double y = my - this.dy;
            for (BaseComponent c : this.parts) {
                if (c.isOver(x, y) && c.mouseScrolled(x, y, amount)) {
                    return true;
                }
            }
            for (PanelComponent p : this.panels) {
                if (p.mouseScrolled(x, y, amount)) {
                    return true;
                }
            }
            return false;
        }

        /** What the visitor set on each widget, by its place in the scene, to carry over a rebuild. */
        public java.util.Map<String, String> values() {
            java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
            List<BaseComponent> all = widgets();
            for (int i = 0; i < all.size(); i++) {
                String v = value(all.get(i));
                if (v != null) {
                    out.put(Integer.toString(i), v);
                }
            }
            return out;
        }

        public void restore(java.util.Map<String, String> values) {
            List<BaseComponent> all = widgets();
            for (int i = 0; i < all.size(); i++) {
                String v = values.get(Integer.toString(i));
                if (v != null) {
                    try {
                        set(all.get(i), v);
                    } catch (RuntimeException ignored) {
                        // a scene changed since the value was kept
                    }
                }
            }
        }

        private List<BaseComponent> widgets() {
            List<BaseComponent> all = new ArrayList<>();
            for (PanelComponent p : this.panels) {
                p.widgets().forEach(w -> collect(w, all));
            }
            this.parts.forEach(w -> collect(w, all));
            return all;
        }

        private static void collect(BaseComponent w, List<BaseComponent> all) {
            all.add(w);
            if (w instanceof SectionComponent s) {
                s.children().forEach(c -> collect(c, all));
            }
        }

        private static String value(BaseComponent w) {
            if (w instanceof ToggleComponent t) {
                return Boolean.toString(t.value);
            } else if (w instanceof SliderComponent s) {
                return Integer.toString(s.value);
            } else if (w instanceof StepperComponent s) {
                return Integer.toString(s.value);
            } else if (w instanceof DropdownComponent d) {
                return d.label;
            } else if (w instanceof TextFieldComponent f) {
                return f.text();
            } else if (w instanceof ToggleGroupComponent g) {
                return g.on().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            } else if (w instanceof TabsComponent t) {
                return Integer.toString(t.index());
            } else if (w instanceof ColorPickerComponent c) {
                return Integer.toString(c.color());
            }
            return null;
        }

        private static void set(BaseComponent w, String v) {
            if (w instanceof ToggleComponent t) {
                t.value = Boolean.parseBoolean(v);
            } else if (w instanceof SliderComponent s) {
                s.value = Integer.parseInt(v);
            } else if (w instanceof StepperComponent s) {
                s.value = Integer.parseInt(v);
            } else if (w instanceof DropdownComponent d) {
                choose(d, v);
            } else if (w instanceof TextFieldComponent f) {
                f.setText(v);
            } else if (w instanceof ToggleGroupComponent g) {
                java.util.Set<Integer> on = new java.util.TreeSet<>();
                for (String n : v.split(",")) {
                    if (!n.isEmpty()) {
                        on.add(Integer.parseInt(n));
                    }
                }
                g.set(on);
            } else if (w instanceof TabsComponent t) {
                t.select(Integer.parseInt(v));
            } else if (w instanceof ColorPickerComponent c) {
                c.setColor(Integer.parseInt(v));
            }
        }

        public boolean key(InputKey key, boolean shift) {
            if (this.layer.takesKeys()) {
                return this.layer.keyPressed(key, shift);
            }
            if (this.focus != null && this.focus.keyPressed(key, shift)) {
                return true;
            }
            for (PanelComponent p : this.panels) {
                if (p.keyPressed(key, shift)) {
                    return true;
                }
            }
            for (BaseComponent c : this.parts) {
                if (c.keyPressed(key, shift)) {
                    return true;
                }
            }
            return key == InputKey.ESCAPE && this.layer.closeTop();
        }

        /**
         * Tab or Shift+Tab: the panel steps to its next stop and the key is used; on the last stop (first, for
         * Shift+Tab) or with no stop it is left unused, so the browser moves focus out of the canvas.
         * The panel itself wraps around, which would trap the keyboard.
         */
        public boolean tab(boolean shift) {
            if (this.layer.takesKeys()) {
                return this.layer.keyPressed(InputKey.TAB, shift);
            }
            for (PanelComponent p : this.panels) {
                List<BaseComponent> stops = p.stops();
                if (stops.isEmpty()) {
                    continue;
                }
                int at = stops.indexOf(p.cursor());
                if (at >= 0 && (shift ? at == 0 : at == stops.size() - 1)) {
                    return false;
                }
                return p.keyPressed(InputKey.TAB, shift);
            }
            return false;
        }

        /** The canvas gained focus from Tab: the panel's cursor goes to its first stop, or its last for Shift+Tab. */
        public void enter(boolean fromEnd) {
            for (PanelComponent p : this.panels) {
                List<BaseComponent> stops = p.stops();
                if (!stops.isEmpty()) {
                    p.cursorTo(stops.get(fromEnd ? stops.size() - 1 : 0));
                    return;
                }
            }
        }

        public boolean typed(int codepoint) {
            if (this.layer.takesKeys()) {
                return this.layer.charTyped(codepoint);
            }
            if (this.focus != null && this.focus.charTyped(codepoint)) {
                return true;
            }
            for (PanelComponent p : this.panels) {
                if (p.charTyped(codepoint)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** A framed panel of the given size at the canvas padding, holding what a scene adds. */
    public static class Card extends PanelComponent {
        final int cw;
        final int ch;

        public Card(Theme theme, int w, int h) {
            super(theme, WORDS, PAD, PAD);
            this.cw = w;
            this.ch = h;
        }

        @Override
        public int width() {
            return this.cw;
        }

        @Override
        public int height() {
            return this.ch;
        }

        public Box place(int x, int y, int w, int h) {
            return at(new Box("cell", x, y, w, h));
        }

        public <T extends BaseComponent> T put(T widget) {
            return add(widget);
        }

        public void show(BaseComponent popup) {
            this.overlay = popup;
        }

        @Override
        protected void drawBackground(Draw d, int mx, int my) {
            this.theme.frame().panel(d, this.theme.palette(), this.left, this.top, this.cw, this.ch);
        }
    }

    /** A panel framed only when it has a title, as the window bench draws one. */
    public static class Plain extends PanelComponent {
        final int pw;
        final int ph;
        final String title;

        public Plain(Theme theme, int left, int top, int w, int h, String title) {
            super(theme, WORDS, left, top);
            this.pw = w;
            this.ph = h;
            this.title = title;
        }

        @Override
        public int width() {
            return this.pw;
        }

        @Override
        public int height() {
            return this.ph;
        }

        @Override
        protected void drawBackground(Draw d, int mx, int my) {
            if (this.title != null) {
                this.theme.frame().panel(d, this.theme.palette(), this.left, this.top, this.pw, this.ph);
                d.text(this.title, this.left + 8, this.top + 8, this.theme.palette().text());
            }
        }
    }

    private static Stage card(String id, Theme t, Card c) {
        return new Stage(id, t, c.cw + 2 * PAD, c.ch + 2 * PAD, 0, 0, -100, -100, 6).panel(c);
    }

    /** A 300 by 180 scene, its canvas cut to it; {@code mxRel < 0} leaves the mouse out. */
    private static Stage bench(String id, Theme t, PanelComponent scene, int mxRel, int myRel) {
        int left = (W - 300) / 2 - PAD;
        int top = (H - 180) / 2 - PAD;
        int h = scene instanceof Scene s ? s.h : 180;
        return new Stage(id, t, 300 + 2 * PAD, h + 2 * PAD, -left, -top, mxRel < 0 ? -100 - left : PAD + mxRel,
                myRel < 0 ? -100 - top : PAD + myRel, 6).panel(scene);
    }



    /** The scene {@code id} ({@code component/variant}) in {@code t}, new and at rest; null if unknown. */
    public static Stage stage(String id, Theme t) {
        switch (id) {
            case "button/roles": return bench(id, t, rolesScene(t), 60, 34);
            case "slots/display": return bench(id, t, SlotScenes.slotsScene(t), -1, -1);
            case "slots/hud": return bench(id, t, SlotScenes.hudScene(t), -1, -1);
            case "button/tooltip": return bench(id, t, tooltipScene(t), 126, 42);
            case "toggle/states": {
                Card c = new Card(t, 180, 62);
                c.put(new ToggleComponent(c.place(10, 10, 160, 12), "Notifications", true, v -> { }));
                c.put(new ToggleComponent(c.place(10, 28, 160, 12), "Show coordinates", false, v -> { }));
                ToggleComponent off = c.put(new ToggleComponent(c.place(10, 44, 160, 12), "Auto-save", true, v -> { }));
                off.active = false;
                return card(id, t, c);
            }
            case "multistop/states": {
                Card c = new Card(t, 180, 72);
                c.put(new MultiStopToggleComponent(c.place(10, 6, 160, 15), "Mode", 3, 0, v -> { })
                        .icons(Icon.SUN, null, Icon.MOON));
                c.put(new MultiStopToggleComponent(c.place(10, 23, 160, 15), "Auto-save", 3, 1, v -> { })
                        .icons(Icon.CLOSE, Icon.REFRESH, Icon.CHECK));
                c.put(new MultiStopToggleComponent(c.place(10, 40, 160, 12), "Render distance", 5, 2, v -> { }));
                MultiStopToggleComponent off = c.put(new MultiStopToggleComponent(c.place(10, 55, 160, 12), "Sync", 2, 1, v -> { }));
                off.active = false;
                return card(id, t, c);
            }
            case "slider/values": {
                Card c = new Card(t, 180, 50);
                c.put(new SliderComponent(c.place(10, 10, 160, 12), 0, 100, 25, v -> { }));
                c.put(new SliderComponent(c.place(10, 28, 160, 12), 0, 8, 6, v -> { }));
                return card(id, t, c);
            }
            case "dropdown/open": {
                // The card grows to the open list in this family, plus room for the family's band under the box.
                Card c = new Card(t, 180, 10 + 14 + 1 + 4 + DropdownComponent.listHeight(t.frame(), DROPDOWN_LINES) + 8);
                DropdownComponent dd = dropdown(c, 180);
                dd.open();
                return card(id, t, c);
            }
            case "textfield/states": return bench(id, t, fieldScene(t), -1, -1);
            case "tabs/rows": return bench(id, t, tabsScene(t), 40, 34);
            case "toggle-group/rows": {
                Card c = new Card(t, 200, 50);
                c.put(new ToggleGroupComponent(c.place(10, 8, 120, 16), List.of("Left", "Center", "Right"),
                        ToggleGroupComponent.Mode.SINGLE, java.util.Set.of(1), on -> { }).required(true));
                c.put(new ToggleGroupComponent(c.place(10, 28, 180, 16), List.of("Bold", "Italic", "Underline"),
                        ToggleGroupComponent.Mode.MULTIPLE, java.util.Set.of(0, 1), on -> { }).disabled(2, true));
                return card(id, t, c);
            }
            case "list/rows": {
                Card c = new Card(t, 180, 100);
                ListComponent<String> list = c.put(new ListComponent<String>(c.place(8, 8, 164, 84), 14, SiteScenes::row, s -> { }));
                list.setItems(List.of("Alex", "Steve", "Zuri", "Makena", "Kai", "Noor", "Sunny", "Ari"));
                list.selected = "Zuri";
                return card(id, t, c);
            }
            case "progress/states": {
                Card c = new Card(t, 180, 52);
                ProgressComponent a = c.put(new ProgressComponent(c.place(10, 10, 160, 8)));
                a.fraction = 0.3F;
                ProgressComponent b = c.put(new ProgressComponent(c.place(10, 22, 160, 8)));
                b.fraction = 0.8F;
                c.put(new ProgressComponent(c.place(10, 34, 160, 8)));
                return card(id, t, c);
            }
            case "empty/states": {
                Card c = new Card(t, 260, 100);
                c.put(new EmptyStateComponent(c.place(6, 6, 124, 88), "No waypoints")
                        .icon(Icon.FOLDER)
                        .text("Mark a place to come back to it.")
                        .action("Add waypoint", () -> { }));
                c.put(new EmptyStateComponent(c.place(130, 6, 124, 88), "No results")
                        .icon(ItemView.of("minecraft:compass"))
                        .text("Try another word."));
                return card(id, t, c);
            }
            case "spinner/ring": {
                Card c = new Card(t, 90, 40);
                c.put(new SpinnerComponent(c.place(10, 8, 24, 24)));
                c.put(new SpinnerComponent(c.place(46, 12, 16, 16)));
                SpinnerComponent still = c.put(new SpinnerComponent(c.place(70, 14, 12, 12)));
                still.motionStyle = MotionStyle.NONE;
                return card(id, t, c);
            }
            case "meter/levels": {
                // A voice-like level driven by the clock: syllables inside a burst, then a pause, crossing the gate.
                MeterComponent[] meter = new MeterComponent[1];
                WaveComponent[] wave = new WaveComponent[1];
                Card c = new Card(t, 180, 40) {
                    @Override
                    protected void drawBackground(Draw d, int mx, int my) {
                        long now = BaseComponent.clock().getAsLong();
                        double rms = voiceRms(now);
                        meter[0].level(rms, VOICE_GATE_RMS, now);
                        wave[0].level(MeterComponent.position(rms));
                        super.drawBackground(d, mx, my);
                    }
                };
                meter[0] = c.put(new MeterComponent(c.place(10, 10, 160, 8)));
                meter[0].live = true;
                wave[0] = c.put(new WaveComponent(c.place(10, 22, 160, 10)));
                wave[0].live = true;
                return card(id, t, c);
            }
            case "panel/sections": {
                Card c = new Card(t, 180, 80) {
                    @Override
                    protected void drawBackground(Draw d, int mx, int my) {
                        super.drawBackground(d, mx, my);
                        Palette p = this.theme.palette();
                        d.text("Panel", this.left + 10, this.top + 10, p.text());
                        this.theme.frame().section(d, p, this.left + 10, this.top + 24, 160, 46);
                        d.text("A section card", this.left + 16, this.top + 30, p.textSoft());
                        d.text("Faint hint text", this.left + 16, this.top + 42, p.textFaint());
                    }
                };
                return card(id, t, c);
            }
            case "scroll/rows": return bench(id, t, scrollScene(t), 120, 70);
            case "grid/cards": return bench(id, t, gridScene(t), 140, 50);
            case "section/open-shut": return bench(id, t, sectionScene(t), -1, -1);
            case "window/tools": {
                PanelComponent content = new Plain(t, 0, 0, 150, 70, null) {
                    {
                        add(new SliderComponent(new Box("s", 6, 8, 130, 14), 0, 100, 40, v -> { }));
                        add(new ToggleComponent(new Box("t", 6, 30, 130, 14), "Snap to grid", true, v -> { }));
                    }
                };
                WindowComponent win = new WindowComponent("minuit_demo:window/" + windows++, content, new Box("w", PAD, PAD, 0, 0))
                        .title("Builder tools");
                return new Stage(id, t, 172, 110, 0, 0, -100, -100, 1).part(win);
            }
            case "radial/wheel": {
                RadialComponent<String> wheel = RadialComponent.<String>builder(new Box("screen", 0, 0, 330, 230), 70)
                        .slot(40, 20)
                        .page("Actions", List.of("Delete", "Lock", "Back", "Warn", "Stop", "Reload", "Play", "Folder"))
                        .page("Links", List.of("Download", "Web", "Report"))
                        .page("Look", List.of("Colours", "Light", "Dark"))
                        .label(s -> s)
                        .icon(s -> QUICK.get(s).glyph())
                        .centerLabel(() -> "Cancel")
                        .dim(false)
                        .build();
                // Radius 70 leaves the page chips 12 px off the stage edge and 8 px off the wheel.
                // The page header sits above the screen box, so the stage starts 12 px higher than the wheel.
                return new Stage(id, t, 330, 242, 0, 12, 165 + 40, 115 + 40 + 12, 1).part(wheel);
            }
            case "reel/cards": {
                // A click on the reel or on Spin starts a spin; the live demo repeats it once it has landed.
                ReelComponent<String> reel = new ReelComponent<>(new Box("reel", 6, 6, 288, 80), MAPS, s -> s) {
                    @Override
                    public boolean mouseClicked(double mx, double my, int button) {
                        if (button != 0 || !isOver(mx, my)) {
                            return false;
                        }
                        if (!spinning()) {
                            spin(new java.util.Random());
                        }
                        return true;
                    }
                }.image(s -> thumbs.apply(s));
                ButtonComponent spin = new ButtonComponent(new Box("spin", 118, 92, 64, 16), "Spin",
                        () -> {
                            if (!reel.spinning()) {
                                reel.spin(new java.util.Random());
                            }
                        }).role(ButtonComponent.Role.PRIMARY);
                return new Stage(id, t, 300, 114, 0, 0, -100, -100, 1).part(reel).part(spin);
            }
            case "colorpicker/alpha": {
                Plain p = new Plain(t, PAD, PAD, 140, 170, "Colour");
                ColorPickerComponent picker = new ColorPickerComponent(new Box("picker", PAD + 20, PAD + 24, 0, 0), 0xC06699CC, c -> { })
                        .alpha(true).recent(new ArrayList<>(List.of(0xFFE04040, 0xFF40A0E0, 0xFF60C060, 0xFFFFD040, 0xFF8040C0)));
                return new Stage(id, t, 152, 182, 0, 0, 0, 0, 1).panel(p).part(picker);
            }
            default: {
                if (GALLERY.contains(id)) {
                    return gallery.apply(id, t);
                }
                Stage h = home(id, t);
                return h != null ? h : McScenes.stage(id, t);
            }
        }
    }

    // --- the home page mosaic: everyday screens, one card each --------------------------------

    /** The mosaic cards of the home page; drawn live, and once in the default look as the fallback picture. */
    public static final List<String> HOME = List.of("home/settings", "home/notifications", "home/confirm", "home/create",
            "home/team", "home/tabs", "home/stepper", "home/view", "home/progress", "home/vote", "home/color",
            "home/radial", "home/window");

    public static final int HW = 160;

    /**
     * Faces for the team card: the default skins of {@link #PLAYERS}. The site points them at its own textures
     * ({@link AsyncImage#ready}) to swap a face when the visitor renames a player.
     */
    public static final List<AsyncImage> HEADS = List.of(skin("Alex"), skin("Noor"), skin("Kai"));

    /** Names on the player card, one per face; the page swaps them as it swaps the faces. */
    public static final String[] PLAYERS = {"Alex", "Noor", "Kai"};

    /** A mosaic card: the panel frame, a title, and lines of soft text under it. */
    public static class Titled extends Card {
        final String title;
        final List<String> lines;

        public Titled(Theme theme, int h, String title, String... lines) {
            super(theme, HW, h);
            this.title = title;
            this.lines = List.of(lines);
        }

        @Override
        protected void drawBackground(Draw d, int mx, int my) {
            super.drawBackground(d, mx, my);
            Palette p = this.theme.palette();
            d.text(this.title, this.left + 10, this.top + 10, p.text());
            for (int i = 0; i < this.lines.size(); i++) {
                d.text(this.lines.get(i), this.left + 10, this.top + 24 + i * 11, p.textSoft());
            }
        }
    }

    /** A bar that fills while the card is awake and starts over, for a loading row. */
    static final class Loading extends ProgressComponent {
        Loading(Box box, float from) {
            super(box);
            this.fraction = from;
        }

        @Override
        public void tick() {
            super.tick();
            this.fraction = this.fraction >= 1F ? 0F : Math.min(1F, this.fraction + 0.02F);
        }
    }

    static DropdownComponent pick(Card c, int x, int y, int w, String label, String header, String... options) {
        DropdownComponent dd = c.put(new DropdownComponent(c.place(x, y, w, 14), label, PAD + x, w, c::show));
        List<DropdownComponent.Line> lines = new ArrayList<>();
        lines.add(new DropdownComponent.Header(header));
        for (String o : options) {
            lines.add(new DropdownComponent.Entry(o, "", "", "", o.equals(label) ? DropdownComponent.State.SELECTED
                    : DropdownComponent.State.SELECTABLE, -1F, List.of(), () -> choose(dd, o)));
        }
        dd.lines = lines;
        return dd;
    }

    /** Shows {@code option} as the dropdown's pick and marks it selected in its list. */
    static void choose(DropdownComponent dd, String option) {
        dd.label = option;
        List<DropdownComponent.Line> lines = new ArrayList<>();
        for (DropdownComponent.Line l : dd.lines) {
            if (l instanceof DropdownComponent.Entry e && (e.state() == DropdownComponent.State.SELECTED
                    || e.state() == DropdownComponent.State.SELECTABLE)) {
                l = new DropdownComponent.Entry(e.name(), e.detail(), e.size(), e.ram(), e.name().equals(option)
                        ? DropdownComponent.State.SELECTED : DropdownComponent.State.SELECTABLE, e.fraction(), e.tooltip(), e.action());
            }
            lines.add(l);
        }
        dd.lines = lines;
    }

    /** The home page mosaic's scenes only ({@code home/...}); null for any other id. */
    public static Stage home(String id, Theme t) {
        switch (id) {
            case "home/settings": {
                Titled c = new Titled(t, 128, "Settings");
                c.put(new ToggleComponent(c.place(10, 26, 140, 12), "Auto-save", true, v -> { }));
                c.put(new ToggleComponent(c.place(10, 42, 140, 12), "Show hints", false, v -> { }));
                c.put(new SliderComponent(c.place(10, 60, 140, 12), 0, 100, 70, v -> { }));
                pick(c, 10, 80, 140, "Normal", "Difficulty", "Peaceful", "Easy", "Normal", "Hard");
                c.put(new ButtonComponent(c.place(10, 104, 64, 16), "Done", () -> { }).role(ButtonComponent.Role.PRIMARY));
                return card(id, t, c);
            }
            case "home/notifications": {
                Titled c = new Titled(t, 104, "Notifications", "Choose what pings you.");
                c.put(new ToggleComponent(c.place(10, 40, 140, 12), "Messages", true, v -> { }));
                c.put(new ToggleComponent(c.place(10, 56, 140, 12), "Mentions", true, v -> { }));
                c.put(new ToggleComponent(c.place(10, 72, 140, 12), "Updates", false, v -> { }));
                ToggleComponent off = c.put(new ToggleComponent(c.place(10, 88, 140, 12), "Sounds", false, v -> { }));
                off.active = false;
                return card(id, t, c);
            }
            case "home/confirm": {
                Titled c = new Titled(t, 76, "Delete this world?", "This cannot be undone.");
                c.put(new ButtonComponent(c.place(10, 50, 66, 16), "Cancel", () -> { }));
                c.put(new ButtonComponent(c.place(84, 50, 66, 16), "Delete", () -> { }).role(ButtonComponent.Role.DESTRUCTIVE));
                return card(id, t, c);
            }
            case "home/create": {
                Titled c = new Titled(t, 106, "New item");
                c.put(new TextFieldComponent(c.place(10, 26, 140, 16), "", s -> { }).placeholder("Name"));
                c.put(new TextFieldComponent(c.place(10, 48, 140, 16), "", s -> { }).placeholder("Description"));
                c.put(new ButtonComponent(c.place(10, 78, 64, 16), "Cancel", () -> { }));
                c.put(new ButtonComponent(c.place(86, 78, 64, 16), "Save", () -> { }).role(ButtonComponent.Role.PRIMARY));
                return card(id, t, c);
            }
            case "home/team": {
                String[] names = PLAYERS;
                Titled c = new Titled(t, 100, "Online players") {
                    @Override
                    protected void drawBackground(Draw d, int mx, int my) {
                        super.drawBackground(d, mx, my);
                        for (int i = 0; i < names.length; i++) {
                            int y = this.top + 26 + i * 22;
                            PlayerHeadComponent.draw(d, this.theme.palette(), HEADS.get(i), this.left + 10, y, 14);
                            d.text(d.cut(names[i], 50), this.left + 30, y + 3, this.theme.palette().text());
                        }
                    }
                };
                String[] roles = {"Playing", "Away", "Building"};
                for (int i = 0; i < roles.length; i++) {
                    pick(c, 84, 26 + i * 22, 66, roles[i], "Status", roles);
                }
                return card(id, t, c);
            }
            case "home/tabs": {
                Titled c = new Titled(t, 84, "Options");
                c.put(new TabsComponent(c.place(10, 24, 140, 14), List.of("General", "Display", "Controls"), 0, i -> { }));
                c.put(new ToggleComponent(c.place(10, 46, 140, 12), "Subtitles", true, v -> { }));
                c.put(new ToggleComponent(c.place(10, 62, 140, 12), "Fast mode", false, v -> { }));
                return card(id, t, c);
            }
            case "home/stepper": {
                Titled c = new Titled(t, 76, "Quantity", "How many to craft.");
                c.put(new StepperComponent(c.place(10, 44, 80, 16), 1, 64, 16, v -> { }));
                c.put(new ButtonComponent(c.place(98, 44, 52, 16), "Craft", () -> { }).role(ButtonComponent.Role.PRIMARY));
                return card(id, t, c);
            }
            case "home/view": {
                Titled c = new Titled(t, 80, "View");
                c.put(new ToggleGroupComponent(c.place(10, 24, 140, 16), List.of("List", "Grid", "Map"),
                        ToggleGroupComponent.Mode.SINGLE, java.util.Set.of(1), on -> { }).required(true));
                c.put(new ToggleGroupComponent(c.place(10, 50, 140, 16), List.of("Name", "Date", "Size"),
                        ToggleGroupComponent.Mode.SINGLE, java.util.Set.of(0), on -> { }).required(true));
                return card(id, t, c);
            }
            case "home/progress": {
                Titled c = new Titled(t, 92, "Downloading", "world.zip", "", "", "textures.zip");
                c.put(new Loading(c.place(10, 38, 140, 8), 0.45F));
                c.put(new Loading(c.place(10, 71, 140, 8), 0.8F));
                return card(id, t, c);
            }
            case "home/vote": {
                Titled c = new Titled(t, 120, "Vote for the next map");
                ReelComponent<String> reel = new ReelComponent<>(new Box("reel", PAD + 4, PAD + 22, HW - 8, 70), MAPS, s -> s)
                        .card(72, 58, 4).image(s -> thumbs.apply(s));
                c.put(new ButtonComponent(c.place(48, 98, 64, 16), "Spin", () -> reel.spin(new java.util.Random()))
                        .role(ButtonComponent.Role.PRIMARY));
                return card(id, t, c).part(reel);
            }
            case "home/color": {
                Titled c = new Titled(t, 146, "Team colour");
                ColorPickerComponent picker = new ColorPickerComponent(new Box("picker", PAD + 30, PAD + 24, 0, 0), 0xFF40A0E0, v -> { })
                        .recent(new ArrayList<>(List.of(0xFFE04040, 0xFF60C060, 0xFFFFD040, 0xFF8040C0))).recentRows(1);
                return card(id, t, c).part(picker);
            }
            case "home/radial": {
                int w = HW + 2 * PAD;
                RadialComponent<String> wheel = RadialComponent.<String>builder(new Box("screen", 0, 0, w, 160), 54)
                        .slot(34, 18)
                        .page("Quick menu", List.of("Delete", "Lock", "Back", "Warn", "Reload", "Stop"))
                        .label(s -> s)
                        .icon(s -> QUICK.get(s).glyph())
                        .centerLabel(() -> "Close")
                        .dim(false)
                        .build();
                return new Stage(id, t, w, 160, 0, 0, w / 2 + 30, 50, 1).part(wheel);
            }
            case "home/window": {
                // As wide as the other home cards (window frame 4 px a side), so the mosaic column lines up.
                PanelComponent content = new Plain(t, 0, 0, HW - 8, 64, null) {
                    {
                        add(new ToggleGroupComponent(new Box("tools", 6, 6, HW - 20, 16), List.of("Pen", "Fill", "Pick"),
                                ToggleGroupComponent.Mode.SINGLE, java.util.Set.of(0), on -> { }).required(true));
                        add(new SliderComponent(new Box("size", 6, 28, HW - 20, 12), 1, 16, 4, v -> { }));
                        add(new ToggleComponent(new Box("grid", 6, 46, HW - 20, 12), "Grid", true, v -> { }));
                    }
                };
                WindowComponent win = new WindowComponent("minuit_demo:window/" + windows++, content, new Box("w", PAD, PAD + 4, 0, 0))
                        .title("Tools");
                return new Stage(id, t, HW + 2 * PAD, 112, 0, 0, -100, -100, 1).part(win);
            }
            default: return null;
        }
    }

    static DropdownComponent dropdown(Card c, int width) {
        DropdownComponent dd = c.put(new DropdownComponent(c.place(10, 10, 160, 14), "Normal", PAD + 10, width - 20, c::show));
        dd.lines = DROPDOWN_LINES;
        return dd;
    }

    static final List<DropdownComponent.Line> DROPDOWN_LINES = List.of(new DropdownComponent.Header("Difficulty"),
            entry("Peaceful", DropdownComponent.State.SELECTABLE),
            entry("Easy", DropdownComponent.State.SELECTABLE),
            entry("Normal", DropdownComponent.State.SELECTED),
            entry("Hard", DropdownComponent.State.DISABLED));

    static void row(Draw d, Theme theme, String name, Box row, boolean hovered, boolean selected) {
        Palette p = theme.palette();
        if (selected) {
            // The same plate the list draws for a selected row, in the family's corner mask.
            Shapes.rounded(d, row.x() - 2, row.y() + 1, row.w() + 4, row.h() - 2, Math.max(2, theme.frame().corner()), p.primary());
        }
        d.textFit(name, row.x() + 4, row.y() + 3, row.w() - 8, selected ? Shapes.textFor(p, p.primaryText(), p.primary()) : p.text());
    }

    // --- the bench scenes, shared with WidgetBench --------------------------------------------

    /** A plain framed panel 300 by 180 in the middle of the canvas, holding what a scene adds. */
    public static final class Scene extends PanelComponent {
        final String title;
        /** The scene's height; a scene whose widgets end high cuts it down to them. */
        public int h = 180;

        public Scene(Theme theme, String title) {
            super(theme, WORDS, (W - 300) / 2, (H - 180) / 2);
            this.title = title;
        }

        @Override
        public int width() {
            return 300;
        }

        @Override
        public int height() {
            return this.h;
        }

        public Box place(int x, int y, int w, int h) {
            return at(new Box("cell", x, y, w, h));
        }

        public <T extends BaseComponent> T put(T widget) {
            return add(widget);
        }

        public void show(BaseComponent popup) {
            this.overlay = popup;
        }

        public void back(Runnable back) {
            addBack(back);
        }

        @Override
        public List<Box> textBoxes() {
            List<Box> boxes = new ArrayList<>(super.textBoxes());
            boxes.add(place(12, 10, 276, 10));
            return boxes;
        }

        @Override
        protected void drawBackground(Draw d, int mx, int my) {
            Palette p = this.theme.palette();
            this.theme.frame().panel(d, p, this.left, this.top, 300, this.h);
            d.textIn(this.title, place(12, 10, 276, 10), p.text());
        }
    }

    public static Scene tabsScene(Theme theme) {
        Scene s = new Scene(theme, "Tabs");
        s.put(new TabsComponent(s.place(12, 28, 276, 14), List.of("General", "Display", "Controls"), 1, i -> { }));
        s.put(new TabsComponent(s.place(12, 60, 180, 14), List.of("Singleplayer", "Multiplayer"), 0, i -> { }));
        s.h = 86;
        return s;
    }


    public static Scene fieldScene(Theme theme) {
        Scene s = new Scene(theme, "Text field");
        s.put(new TextFieldComponent(s.place(12, 28, 276, 16), "", t -> { }).placeholder("Search..."));
        TextFieldComponent typed = s.put(new TextFieldComponent(s.place(12, 52, 276, 16), "", t -> { }));
        s.mouseClicked(typed.x + 5, typed.y + 5, 0);
        type(s, "Hello world");
        s.keyPressed(InputKey.LEFT, true);
        s.keyPressed(InputKey.LEFT, true);
        s.keyPressed(InputKey.LEFT, true);
        s.keyPressed(InputKey.LEFT, true);
        s.keyPressed(InputKey.LEFT, true);
        s.put(new TextFieldComponent(s.place(12, 76, 140, 16),
                "A sentence much longer than the field it is typed in", t -> { }));
        s.put(new TextFieldComponent(s.place(12, 100, 140, 16), "Not focused, kept", t -> { }));
        s.h = 124;
        return s;
    }

    public static Scene scrollScene(Theme theme) {
        Scene s = new Scene(theme, "Scroll");
        ScrollComponent scroll = s.put(new ScrollComponent(s.place(12, 28, 276, 140)));
        for (int i = 0; i < 12; i++) {
            Box row = s.place(16, 32 + i * 18, 250, 14);
            if (i % 2 == 0) {
                scroll.add(new ButtonComponent(row, "Button " + (i + 1), () -> { }));
            } else {
                scroll.add(new ToggleComponent(row, "Toggle " + (i + 1), i % 4 == 1, v -> { }));
            }
        }
        scroll.setScroll(38);
        return s;
    }

    public static Scene tooltipScene(Theme theme) {
        Scene s = new Scene(theme, "Tooltip");
        s.put(new ButtonComponent(s.place(12, 28, 120, 16), "Hover me", () -> { })
                .tooltip("Render distance", "Chunks drawn around you. Higher values cost more frames."));
        s.put(new ButtonComponent(s.place(140, 28, 120, 16), "No tip", () -> { }));
        s.h = 96;
        return s;
    }

    public static Scene rolesScene(Theme theme) {
        Scene s = new Scene(theme, "Button roles");
        ButtonComponent.Role[] roles = ButtonComponent.Role.values();
        String[] names = {"Hero", "Primary", "Secondary", "Destructive"};
        for (int i = 0; i < roles.length; i++) {
            s.put(new ButtonComponent(s.place(12, 28 + i * 24, 130, 18), names[i], () -> { }).role(roles[i]));
            s.put(new ButtonComponent(s.place(150, 28 + i * 24, 130, 18), names[i] + " off", () -> { }).role(roles[i]).active(false));
        }
        s.put(new ButtonComponent(s.place(12, 130, 130, 18), "Plain", () -> { }));
        s.put(new ButtonComponent(s.place(150, 130, 130, 18), "Selected", () -> { }).role(ButtonComponent.Role.SECONDARY).selected(true));
        return s;
    }

    public static Scene sectionScene(Theme theme) {
        Scene s = new Scene(theme, "Sections");
        SectionComponent open = s.put(new SectionComponent(s.place(12, 28, 276, 0), "Display", true));
        open.add(new ToggleComponent(s.place(20, 46, 250, 14), "Show coordinates", true, v -> { }));
        open.add(new ButtonComponent(s.place(20, 64, 120, 16), "Reset camera", () -> { }));
        int below = 28 + open.h + 4;
        SectionComponent shut = s.put(new SectionComponent(s.place(12, below, 276, 0), "Advanced", false));
        shut.add(new ButtonComponent(s.place(20, below + SectionComponent.HEADER + 2, 120, 16), "Reset all", () -> { })
                .role(ButtonComponent.Role.DESTRUCTIVE));
        // Room for the shut section's button once the visitor opens it.
        s.h = below + SectionComponent.HEADER + 2 + 16 + 10;
        return s;
    }

    /** The gate of the levels demo, on the 16 bit RMS scale. */
    static final double VOICE_GATE_RMS = 900;

    /** A synthetic voice on the 16 bit RMS scale: 2.6 s of syllables, then a pause, every 4.2 s. */
    static double voiceRms(long ms) {
        double ph = Math.floorMod(ms, 4200L);
        if (ph >= 2600) {
            return 40;
        }
        double syllable = 0.35 + 0.65 * Math.abs(Math.sin(ph * Math.PI / 330));
        double burst = Math.sqrt(Math.sin(ph * Math.PI / 2600));
        return 400 + 6000 * syllable * burst;
    }

    public static Scene gridScene(Theme theme) {
        Scene s = new Scene(theme, "Grid");
        List<String> names = List.of("Alex", "Steve", "Zuri", "Makena", "Kai", "Noor", "Sunny", "Ari", "Efe");
        ScrollComponent scroll = s.put(new ScrollComponent(s.place(12, 28, 276, 140)));
        GridComponent<String> grid = scroll.add(new GridComponent<>(s.place(16, 32, 250, 0), 4, 36, 8, 8, names, SiteScenes::card,
                n -> { }));
        grid.selected("Zuri");
        return s;
    }

    /** A card of the bench grid: a plate, the name, a selection ring in the accent. */
    public static void card(Draw d, Theme theme, String name, Box cell, boolean hovered, boolean selected) {
        Palette p = theme.palette();
        if (selected) {
            int corner = theme.frame().corner();
            Shapes.rounded(d, cell.x() - 2, cell.y() - 2, cell.w() + 4, cell.h() + 4, corner == 0 ? 0 : corner + 2, p.accent());
        }
        theme.frame().section(d, p, cell.x(), cell.y(), cell.w(), cell.h());
        if (hovered) {
            d.fill(cell.x() + 3, cell.y() + 3, cell.x() + 17, cell.y() + 17, p.raisedHover());
        }
        dev.dryaides.minuit.PlayerHeadComponent.draw(d, p, skin(name), cell.x() + 4, cell.y() + 4, 12);
        d.textFit(name, cell.x() + 4, cell.y() + 22, cell.w() - 8, p.text());
    }

    /** The game's default skin of {@code player}, one of its nine default names. */
    public static AsyncImage skin(String player) {
        return AsyncImage.of("minecraft:textures/entity/player/wide/" + player.toLowerCase(java.util.Locale.ROOT) + ".png", 64, 64);
    }

    public static DropdownComponent.Entry entry(String name, DropdownComponent.State state) {
        return new DropdownComponent.Entry(name, "", "", "", state, -1F, List.of(), () -> { });
    }

    public static void type(PanelComponent panel, String text) {
        text.codePoints().forEach(panel::charTyped);
    }}
