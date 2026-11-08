package dev.dryaides.minuit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where {@link WindowComponent}s keep their place, size and fold between openings, by window key. The client
 * host saves to {@code config/minuit-layouts.json}; a bench keeps them in memory.
 */
public interface LayoutStore {

    /**
     * A window's offset from its default position, its scale, and whether it is folded to its title bar.
     */
    record Layout(int dx, int dy, float scale, boolean collapsed) {
        public static final Layout DEFAULT = new Layout(0, 0, 1F);

        /** An unfolded window. */
        public Layout(int dx, int dy, float scale) {
            this(dx, dy, scale, false);
        }
    }

    /** The saved layout, or null when the window never moved. */
    Layout get(String key);

    void put(String key, Layout layout);


    /** A store that lives as long as the game session. */
    static LayoutStore memory() {
        Map<String, Layout> map = new ConcurrentHashMap<>();
        return new LayoutStore() {
            @Override
            public Layout get(String key) {
                return map.get(key);
            }

            @Override
            public void put(String key, Layout layout) {
                map.put(key, layout);
            }
        };
    }
}
