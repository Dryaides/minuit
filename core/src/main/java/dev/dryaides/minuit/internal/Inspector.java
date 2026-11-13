package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.Palette;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.ScrollComponent;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.internal.port.KeyCodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * A dev inspector over every minuit panel, on with {@code -Dminuit.inspect=true}: the widget under the pointer
 * gets a card with its class, box, the palette roles it drew with this frame and its keyboard state (stop,
 * cursor, focus); F8 toggles outlines of every widget box. The card and outlines go through the panel's
 * {@link dev.dryaides.minuit.OverlayLayer}, so they sit over popups and outside every scissor, and take no input.
 * {@code -Dminuit.inspect.key=<code>} picks another key code for the toggle. Not API.
 */
public final class Inspector {
    public static final boolean ENABLED = Boolean.getBoolean("minuit.inspect");
    private static final int KEY = Integer.getInteger("minuit.inspect.key", -1);
    private static final int GLFW_F8 = 297;
    private static final int LWJGL2_F8 = 66;
    private static final int OUTLINE = 0xFFFF40C0;
    private static final int HOT = 0xFFFFE040;

    /** The renderer whose fills and texts are recorded, and the colours the widget being drawn sent. */
    public static Draw target;
    public static Set<Integer> inks;

    private static boolean outlines;
    private static final Map<BaseComponent, Set<Integer>> drawn = new IdentityHashMap<>();

    private static final String[] ROLE_NAMES = {"frame", "frameDark", "frameLight", "trim", "surface", "surfaceEdge",
            "section", "well", "wellEdge", "text", "textSoft", "textFaint", "raised", "raisedHover", "raisedText",
            "primary", "primaryHover", "primaryText", "accent", "accentDeep", "good", "warn", "bad", "destructive",
            "destructiveHover", "destructiveText", "track", "disabled", "disabledText", "shadow", "link", "backdrop",
            "line", "mark"};
    private static final List<ToIntFunction<Palette>> ROLES = List.of(Palette::frame, Palette::frameDark,
            Palette::frameLight, Palette::trim, Palette::surface, Palette::surfaceEdge, Palette::section, Palette::well,
            Palette::wellEdge, Palette::text, Palette::textSoft, Palette::textFaint, Palette::raised,
            Palette::raisedHover, Palette::raisedText, Palette::primary, Palette::primaryHover, Palette::primaryText,
            Palette::accent, Palette::accentDeep, Palette::good, Palette::warn, Palette::bad, Palette::destructive,
            Palette::destructiveHover, Palette::destructiveText, Palette::track, Palette::disabled,
            Palette::disabledText, Palette::shadow, Palette::link, Palette::backdrop, Palette::line,
            Palette::mark, Palette::slot);

    private Inspector() {
    }

    /** True when {@code code} is the toggle key; F8 in GLFW's or LWJGL 2's table, told apart by Escape's code. */
    public static boolean toggle(int code, KeyCodes codes) {
        int key = KEY >= 0 ? KEY : codes.escape() == 256 ? GLFW_F8 : LWJGL2_F8;
        if (code != key) {
            return false;
        }
        outlines = !outlines;
        return true;
    }

    /** Before a root panel draws its widgets: forgets the last frame's colours. */
    public static void beginFrame(Draw d) {
        drawn.clear();
        target = d;
    }

    /** Records the colours {@code widget} sends until {@link #endWidget}. */
    public static void beginWidget(BaseComponent widget) {
        inks = drawn.computeIfAbsent(widget, w -> new LinkedHashSet<>());
    }

    public static void endWidget() {
        inks = null;
    }

    /** A widget that holds widgets of its own the inspector cannot see through {@code stops()}: a stage view. */
    public interface Host {
        /** Hands the held widgets to {@code walk} with the screen transform they draw under. */
        void inspect(Walk walk);
    }

    /** One widget found under a root panel, its box in screen pixels clipped to the hosts above it. */
    private static final class Hit {
        final BaseComponent widget;
        final Hit parent;
        final int x0;
        final int y0;
        final int x1;
        final int y1;

        Hit(BaseComponent widget, Hit parent, int x0, int y0, int x1, int y1) {
            this.widget = widget;
            this.parent = parent;
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }

        boolean over(int mx, int my) {
            return mx >= this.x0 && mx < this.x1 && my >= this.y0 && my < this.y1;
        }
    }

    /** The widgets of a panel and of every host and scroll box inside it, parents before their children. */
    public static final class Walk {
        private final List<Hit> hits = new ArrayList<>();
        private final Set<BaseComponent> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        private Hit parent;

        /** Adds {@code widgets} drawn at {@code origin + local * scale}, inside the host being walked. */
        public void widgets(List<? extends BaseComponent> widgets, float ox, float oy, float scale) {
            for (BaseComponent w : new ArrayList<>(widgets)) {
                add(w, ox, oy, scale);
            }
        }

        private void add(BaseComponent w, float ox, float oy, float s) {
            if (w == null || !w.visible || !this.seen.add(w)) {
                return;
            }
            int x0 = Math.round(ox + w.x * s);
            int y0 = Math.round(oy + w.y * s);
            int x1 = Math.round(ox + (w.x + w.w) * s);
            int y1 = Math.round(oy + (w.y + w.h) * s);
            Hit up = this.parent;
            if (up != null) {
                x0 = Math.max(x0, up.x0);
                y0 = Math.max(y0, up.y0);
                x1 = Math.min(x1, up.x1);
                y1 = Math.min(y1, up.y1);
                if (x1 <= x0 || y1 <= y0) {
                    return;
                }
            }
            Hit hit = new Hit(w, up, x0, y0, x1, y1);
            this.hits.add(hit);
            this.parent = hit;
            if (w instanceof ScrollComponent) {
                widgets(((ScrollComponent) w).children(), ox, oy, s);
            } else if (w instanceof Host) {
                ((Host) w).inspect(this);
            }
            for (BaseComponent stop : w.stops()) {
                if (stop != w) {
                    add(stop, ox, oy, s);
                }
            }
            this.parent = up;
        }
    }

    /** Queues the outlines and the card of the widget under the pointer on the panel's layer. */
    public static void queue(Draw d, PanelComponent panel, Theme theme, int mx, int my) {
        target = null;
        inks = null;
        List<BaseComponent> widgets = panel.widgets();
        Walk walk = new Walk();
        walk.widgets(widgets, 0, 0, 1F);
        List<Hit> hits = walk.hits;
        Hit hot = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).over(mx, my)) {
                hot = hits.get(i);
                break;
            }
        }
        if (hot == null && !outlines) {
            return;
        }
        BaseComponent anchor = hot != null ? hot.widget : widgets.isEmpty() ? null : widgets.get(0);
        if (anchor == null) {
            return;
        }
        Hit pick = hot;
        d.overlay(anchor, mx, my, (on, x, y) -> {
            if (outlines) {
                for (Hit h : hits) {
                    outline(on, h, h.parent == null || panel.stops().contains(h.widget) ? OUTLINE : OUTLINE & 0x80FFFFFF);
                }
            }
            if (pick != null) {
                outline(on, pick, HOT);
                card(on, panel, theme, pick, x, y);
            }
        }).openWhile(() -> false);
    }

    private static void outline(Draw d, Hit h, int color) {
        d.fill(h.x0, h.y0, h.x1, h.y0 + 1, color);
        d.fill(h.x0, h.y1 - 1, h.x1, h.y1, color);
        d.fill(h.x0, h.y0, h.x0 + 1, h.y1, color);
        d.fill(h.x1 - 1, h.y0, h.x1, h.y1, color);
    }

    private static void card(Draw d, PanelComponent panel, Theme theme, Hit hit, int mx, int my) {
        BaseComponent w = hit.widget;
        List<String> lines = new ArrayList<>();
        Class<?> type = w.getClass();
        while (type.getSimpleName().isEmpty() && type.getSuperclass() != null) {
            type = type.getSuperclass();
        }
        lines.add(type.getName().replace("dev.dryaides.minuit.", ""));
        lines.add("box " + w.x + "," + w.y + " " + w.w + "x" + w.h);
        boolean stop = panel.stops().contains(w);
        lines.add((stop ? "stop" : "no stop") + (panel.cursor() == w ? ", cursor" : "")
                + (panel.focus() == w ? ", focus" : "") + (w.active ? "" : ", inactive"));
        Set<Integer> inks = null;
        for (Hit h = hit; h != null && inks == null; h = h.parent) {
            inks = drawn.get(h.widget);
        }
        if (inks == null) {
            for (Map.Entry<BaseComponent, Set<Integer>> e : drawn.entrySet()) {
                if (e.getKey().stops().contains(w)) {
                    inks = e.getValue();
                }
            }
        }
        String roles = roles(theme.palette(), inks);
        lines.add(roles.isEmpty() ? "roles: none matched" : "roles: " + roles);
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, d.width(line));
        }
        int height = lines.size() * 10 + 4;
        Box area = panel.overlayArea();
        int x = mx + 12;
        int y = my + 12;
        if (x + width + 3 > area.right()) {
            x = mx - 12 - width;
        }
        if (y - 3 + height > area.bottom()) {
            y = my - 12 - height + 3;
        }
        x = Math.max(area.x() + 3, x);
        y = Math.max(area.y() + 3, y);
        d.fill(x - 3, y - 3, x + width + 3, y + lines.size() * 10 + 1, 0xE0101018);
        for (int i = 0; i < lines.size(); i++) {
            d.text(lines.get(i), x, y + i * 10, i == 0 ? HOT : 0xFFE0E0E0);
        }
    }

    private static String roles(Palette palette, Set<Integer> inks) {
        if (inks == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < ROLES.size(); i++) {
            int role = ROLES.get(i).applyAsInt(palette);
            if (inks.contains(role) || inks.contains(role | 0xFF000000)) {
                out.append(out.length() == 0 ? "" : " ").append(ROLE_NAMES[i]);
            }
        }
        return out.toString();
    }
}
