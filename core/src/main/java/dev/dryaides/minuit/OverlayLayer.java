package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The one layer every popup draws in: a dropdown's list, a combo box's list, a date picker's calendar, a
 * context menu, a popover. A popup drawn in its owner's render would be cut by every box around it (a
 * {@link ScrollComponent}'s scissor, a demo stage) and covered by the widgets added after it, so it is queued
 * instead and drawn by the root host once the screen is done, on top and outside every scissor.
 *
 * <h2>Drawing</h2>
 * A host (a {@link PanelComponent}, a site stage) owns one layer and calls {@link #begin} before drawing its
 * widgets: the first host to bind a {@link Draw} is the root, any host drawn inside it finds the layer bound and
 * leaves it be. While it is bound, {@link Draw#overlay} queues a popup with the {@link Draw#push} transform in
 * effect, relative to the root's; the root's {@link #flush} draws the queue in order, each popup under its own
 * transform and a {@link Draw#nextLayer}, so a list opened inside a scaled stage keeps the stage's scale. A popup
 * queued while the layer flushes (a dropdown inside a dialog shown as an overlay) is drawn after it, on top.
 *
 * <h2>Input</h2>
 * The root host offers each event to the topmost open popup first, mapped back into the coordinates it was drawn
 * in, so the nested hosts between the root and the popup (scroll boxes, windows, a stage view) never see it. A
 * popup that answers {@link BaseComponent#isOver} gets the click; a click outside it closes it and goes no
 * further. A {@linkplain Entry#keys() keyed} popup takes every key; the others leave keys to the focused widget
 * and only close on an Escape nothing else used. The queue of the last flushed frame answers input, so a popup
 * stops taking events the moment its {@linkplain Entry#openWhile owner reports it closed}.
 */
@Incubating
public final class OverlayLayer {

    /** Draws a queued popup; {@code mx, my} are in the coordinates it was queued in. */
    @FunctionalInterface
    public interface Painter {
        void paint(Draw d, int mx, int my);
    }

    /** One popup of a frame, with the transform it was queued under. */
    public static final class Entry {
        final BaseComponent target;
        final Painter painter;
        final float x;
        final float y;
        final float scale;
        boolean keys;
        boolean pass;
        BooleanSupplier open;
        Runnable close;
        boolean closed;

        Entry(BaseComponent target, Painter painter, float x, float y, float scale) {
            this.target = target;
            this.painter = painter;
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.open = () -> target.visible;
        }

        /** Takes every key and typed character while open: Enter and Space press it when it leaves them. */
        public Entry keys() {
            this.keys = true;
            return this;
        }

        /**
         * A click outside the target goes to the target too, which closes itself, and on to the widgets beneath
         * when the target leaves it: a popover that lets the click through.
         */
        public Entry passClicks() {
            this.pass = true;
            return this;
        }

        /** While this is false the popup is still drawn (a closing slide) but takes no input. Default: the target is visible. */
        public Entry openWhile(BooleanSupplier open) {
            this.open = open;
            return this;
        }

        /** Run by a click outside the target, or an Escape nothing else used. */
        public Entry onClose(Runnable close) {
            this.close = close;
            return this;
        }

        boolean live() {
            return !this.closed && this.open.getAsBoolean();
        }

        double inX(double mx) {
            return (mx - this.x) / this.scale;
        }

        double inY(double my) {
            return (my - this.y) / this.scale;
        }
    }

    private final List<Entry> queued = new ArrayList<>();
    private List<Entry> shown = List.of();
    private float baseX;
    private float baseY;
    private float baseScale = 1F;

    /**
     * Binds this layer to {@code d} when no host bound one yet, and empties the queue when {@code fresh}.
     *
     * @return true when this host is the root of the frame: it then calls {@link #flush}, or {@link #end} to
     * keep the queue for a later flush on another {@code Draw}
     */
    public boolean begin(Draw d, boolean fresh) {
        if (d.layer() != null) {
            return d.layer() == this;
        }
        if (fresh) {
            this.queued.clear();
        }
        this.baseX = d.atX();
        this.baseY = d.atY();
        this.baseScale = d.atScale();
        d.layer(this);
        return true;
    }

    /** Unbinds without drawing: a container screen draws its items between the widgets and the popups. */
    public void end(Draw d) {
        if (d.layer() == this) {
            d.layer(null);
        }
    }

    Entry add(Draw d, BaseComponent target, Painter painter) {
        Entry e = new Entry(target, painter, (d.atX() - this.baseX) / this.baseScale, (d.atY() - this.baseY) / this.baseScale,
                d.atScale() / this.baseScale);
        this.queued.add(e);
        return e;
    }

    /** Draws the queue on top, then unbinds; the popups drawn answer input until the next flush. */
    public void flush(Draw d, int mx, int my) {
        for (int i = 0; i < this.queued.size(); i++) {
            paint(d, this.queued.get(i), mx, my);
        }
        this.shown = List.copyOf(this.queued);
        this.queued.clear();
        end(d);
    }

    static void paint(Draw d, Entry e, int mx, int my) {
        d.push(e.x, e.y, e.scale);
        d.nextLayer();
        e.painter.paint(d, (int) Math.floor(e.inX(mx)), (int) Math.floor(e.inY(my)));
        d.pop();
    }

    /** The topmost popup still open, or null. */
    Entry top() {
        for (int i = this.shown.size() - 1; i >= 0; i--) {
            Entry e = this.shown.get(i);
            if (e.live()) {
                return e;
            }
        }
        return null;
    }

    /** True while a popup drawn last frame is open. */
    public boolean isOpen() {
        return top() != null;
    }

    /** Closes the topmost open popup. False when none is open or it has no close action. */
    public boolean closeTop() {
        Entry top = top();
        if (top == null || top.close == null) {
            return false;
        }
        top.closed = true;
        top.close.run();
        return true;
    }

    /** The click for the topmost popup. False when none is open or the popup let the click through. */
    public boolean mouseClicked(double mx, double my, int button) {
        return clicked(top(), mx, my, button);
    }

    static boolean clicked(Entry e, double mx, double my, int button) {
        if (e == null) {
            return false;
        }
        double x = e.inX(mx);
        double y = e.inY(my);
        if (e.target.isOver(x, y)) {
            return e.target.mouseClicked(x, y, button) || e.keys;
        }
        if (e.pass) {
            return e.target.mouseClicked(x, y, button);
        }
        if (e.close != null) {
            e.closed = true;
            e.close.run();
        }
        return true;
    }

    /** Offered to the topmost popup; the host sends the release on to its widgets either way. */
    public void mouseReleased(double mx, double my, int button) {
        Entry e = top();
        if (e != null) {
            e.target.mouseReleased(e.inX(mx), e.inY(my), button);
        }
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        Entry e = top();
        return e != null && e.target.mouseDragged(e.inX(mx), e.inY(my), button, dx / e.scale, dy / e.scale);
    }

    /** A keyed popup keeps every wheel turn; another one lets a turn it did not use through. */
    public boolean mouseScrolled(double mx, double my, double amount) {
        return scrolled(top(), mx, my, amount);
    }

    static boolean scrolled(Entry e, double mx, double my, double amount) {
        return e != null && (e.target.mouseScrolled(e.inX(mx), e.inY(my), amount) || e.keys);
    }

    /** Every key for a keyed popup: Escape closes it when it leaves the key, Enter and Space press it. */
    public boolean keyPressed(InputKey key, boolean shift) {
        return keyed(top(), key, shift);
    }

    static boolean keyed(Entry e, InputKey key, boolean shift) {
        if (e == null || !e.keys) {
            return false;
        }
        if (!e.target.keyPressed(key, shift)) {
            if (key == InputKey.ESCAPE) {
                if (e.close != null) {
                    e.closed = true;
                    e.close.run();
                }
            } else if (key == InputKey.ENTER || key == InputKey.SPACE) {
                e.target.activate();
            }
        }
        return true;
    }

    /** True when the topmost popup takes every key, so the focused widget gets none. */
    public boolean takesKeys() {
        Entry e = top();
        return e != null && e.keys;
    }

    public boolean charTyped(int codepoint) {
        Entry e = top();
        if (e == null || !e.keys) {
            return false;
        }
        e.target.charTyped(codepoint);
        return true;
    }

    /**
     * The topmost popup's tip at the pointer, or null when no popup is open or the pointer is outside a popup
     * that lets the widgets beneath answer. A keyed popup hides every tip beneath it.
     */
    public List<String> tooltipAt(double mx, double my) {
        Entry e = top();
        if (e == null) {
            return null;
        }
        double x = e.inX(mx);
        double y = e.inY(my);
        List<String> lines = e.target.isOver(x, y) || e.keys ? e.target.tooltipAt(x, y) : null;
        return lines == null && e.keys ? List.of() : lines;
    }

    /** The height the topmost popup lays its tip from, in the root's coordinates; {@code my} with none open. */
    public int tooltipY(double mx, double my) {
        Entry e = top();
        if (e == null) {
            return (int) Math.floor(my);
        }
        return (int) Math.floor(e.y + e.target.tooltipY(e.inX(mx), e.inY(my)) * e.scale);
    }

    /** The item the topmost popup shows at the pointer, or null; {@code covered} is set when a popup lies there. */
    ItemView itemAt(double mx, double my, boolean[] covered) {
        Entry e = top();
        if (e == null) {
            return null;
        }
        double x = e.inX(mx);
        double y = e.inY(my);
        if (!e.target.isOver(x, y)) {
            return null;
        }
        covered[0] = true;
        return e.target instanceof ItemSource source ? source.itemAt(x, y) : null;
    }
}
