package dev.dryaides.minuit;

import java.util.HashMap;
import java.util.Map;

/** The hover glow of each row or card of a widget, eased on {@link BaseComponent#clock} as a button's. */
final class HoverGlows<T> {

    private final Map<T, Float> lit = new HashMap<>();
    private long last;
    private long dt = -1;

    /** Starts a frame; every {@link #of} until the next one eases by the time since the last frame. */
    void frame(long now) {
        this.dt = this.last == 0 ? -1 : now - this.last;
        this.last = now;
    }

    float of(T item, boolean hovered) {
        float now = BaseComponent.glow(this.lit.getOrDefault(item, 0F), hovered, this.dt);
        if (now == 0F) {
            this.lit.remove(item);
        } else {
            this.lit.put(item, now);
        }
        return now;
    }
}
