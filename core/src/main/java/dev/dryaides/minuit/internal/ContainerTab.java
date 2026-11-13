package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.ThemeTab;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.TipDelay;
import dev.dryaides.minuit.client.MinuitClient;
import dev.dryaides.minuit.internal.port.Platform;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The theme tab on the game's own container screens (inventory, chests, furnaces, other mods' menus):
 * the way to the theme page when no minuit screen is open. It sits on the container's top edge at the
 * left, where the recipe book, JEI and EMI side panels, Curios' inventory button and Inventory Profiles
 * Next's top right buttons leave room, and follows the container when the recipe book moves it. It
 * restyles nothing: the screen keeps its own look. {@code containerTab} in {@code minuit-client.json}
 * hides it or moves it. Not API; the per-version mixins call it.
 */
public final class ContainerTab {

    /** The id the theme page sets: minuit's own, which its "every mod" pick extends to all. */
    public static final String THEME_ID = "minuit";
    /** A click counts only on a tab drawn this recently: a screen that skipped its frame (the recipe book over a narrow inventory) shows no tab. */
    static final long DRAWN_MS = 250;

    private static final TipDelay TIP = new TipDelay();
    /** Weak: a closed screen holds its menu, the player and the level, and must not outlive the screen stack because of the tab. */
    private static WeakReference<Object> drawnOn = new WeakReference<>(null);
    private static long drawnAt;
    private static WeakReference<Object> away = new WeakReference<>(null);

    private static final List<Class<?>> HIDDEN_CLASSES = new CopyOnWriteArrayList<>();
    private static final List<String> HIDDEN_PACKAGES = new CopyOnWriteArrayList<>();

    private ContainerTab() {
    }

    public static void hideOn(Class<?> screenClass) {
        HIDDEN_CLASSES.add(screenClass);
    }

    public static void hideIn(String packagePrefix) {
        HIDDEN_PACKAGES.add(packagePrefix.endsWith(".") ? packagePrefix : packagePrefix + ".");
    }

    static boolean hiddenOn(Object screen) {
        for (Class<?> c : HIDDEN_CLASSES) {
            if (c.isInstance(screen)) {
                return true;
            }
        }
        String name = screen.getClass().getName();
        for (String prefix : HIDDEN_PACKAGES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** The tab of a container whose corner is at {@code left}, {@code top}, or null when the player hid it or the screen leaves no room above. */
    public static Box at(ClientConfigFile.TabPlace place, int left, int top) {
        if (!place.shown()) {
            return null;
        }
        Box tab = ThemeTab.onEdge(left + place.offsetX(), top + place.offsetY());
        return tab.y() < 0 ? null : tab;
    }

    /**
     * Draws the tab of {@code screen}, its container's corner at {@code left}, {@code top}, the mouse at
     * {@code mx}, {@code my}; {@code d} draws with its origin at {@code originX}, {@code originY}.
     */
    public static void draw(Object screen, Draw d, int originX, int originY, int left, int top, int mx, int my) {
        Box tab = hiddenOn(screen) ? null : at(MinuitClient.containerTab(), left, top);
        if (tab == null) {
            return;
        }
        drawnOn = new WeakReference<>(screen);
        drawnAt = BaseComponent.clock().getAsLong();
        ThemeTab.drawOnEdge(d, Themes.byId(THEME_ID),
                new Box(tab.name(), tab.x() - originX, tab.y() - originY, tab.w(), tab.h()), tab.contains(mx, my));
    }

    /** Draws the tab's tip; after the screen, with {@code d}'s origin at the screen's corner. */
    public static void drawTip(Object screen, Draw d, int left, int top, int mx, int my) {
        TIP.draw(d, over(screen, left, top, mx, my) ? List.of(Platform.words().get("minuit.picker.title")) : null, mx, my);
    }

    /** Opens the theme page on a click on the tab; true when it took the click. */
    public static boolean click(Object screen, int left, int top, double mx, double my) {
        if (!over(screen, left, top, mx, my)) {
            return false;
        }
        BaseComponent.click();
        away = new WeakReference<>(screen);
        Platform.screens().openThemePage(screen, THEME_ID);
        return true;
    }

    /**
     * True once for the {@code removed} of a screen the tab left for the theme page: the screen comes
     * back from it, so it keeps its menu open, as a minuit container screen does.
     */
    public static boolean leaving(Object screen) {
        if (screen == null || away.get() != screen) {
            return false;
        }
        away = new WeakReference<>(null);
        return true;
    }

    static boolean over(Object screen, int left, int top, double mx, double my) {
        if (screen != drawnOn.get() || BaseComponent.clock().getAsLong() - drawnAt > DRAWN_MS) {
            return false;
        }
        Box tab = at(MinuitClient.containerTab(), left, top);
        return tab != null && tab.contains(mx, my);
    }
}
