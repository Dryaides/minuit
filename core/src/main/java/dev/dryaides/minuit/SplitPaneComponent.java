package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Two panes side by side or one above the other, with a divider the player can drag. Each pane holds one
 * widget, which may be another split, and is placed, clipped and given its input by the split.
 *
 * <pre>{@code
 * SplitPaneComponent split = add(new SplitPaneComponent(at(new Box("split", 8, 8, 280, 140)),
 *         SplitPaneComponent.Orientation.HORIZONTAL, fileTree, preview)
 *         .fraction(0.35)
 *         .limits(SplitPaneComponent.Side.FIRST, 60, 180)
 *         .collapsible(SplitPaneComponent.Side.FIRST)
 *         .onCommit(px -> config.treeWidth = px));
 * }</pre>
 *
 * <p>Drag the divider to resize, double-click it to go back to the starting place, and click its small
 * arrow to fold a {@linkplain #collapsible collapsible} pane. With the keyboard on the divider: the arrows
 * along the axis move it ({@linkplain #keyStep a step}, four steps with shift), Home and End go to the
 * limits, Enter or Space folds or unfolds, or goes back to the starting place when nothing can fold, and
 * Escape drops a drag. The other two arrows and Tab walk the stops: first pane, divider, second pane.
 *
 * <p>The wheel over the divider moves it; over a pane it goes to the pane. A pane keeps a fraction of the
 * room when the box changes size, unless {@link #keepSize} holds one side's pixels. {@code x}, {@code y},
 * {@code w} and {@code h} are read live, so setting them is all a resize takes.
 *
 * <h2>Panes</h2>
 * The split sets its panes' {@code x}, {@code y}, {@code w} and {@code h}, {@linkplain #padding 2 px} in from
 * the pane's edges so that a child's focus ring stays whole. A {@link ScrollComponent}'s children move with
 * it but keep their size. Other widgets that hold parts must place them from their own box: use
 * {@link #onLayout} for that. {@code active = false} turns the panes off too and gives them back as they were.
 * A pane's widget with {@code visible = false} leaves its pane blank. A collapsed pane takes no input or stops.
 *
 * <p>A {@link DropdownComponent}'s popup goes through the panel's overlay, so the pane does not clip it, but
 * its x is fixed when the dropdown is made: give it a new one from {@link #onLayout} if the pane moves. The
 * panel reveals a stop inside a {@link ScrollComponent} pane through this split; it cannot do so for a
 * {@link ListComponent} pane. Give it {@link #popupHost} so that a popup hides what is under it in glass.
 */
public class SplitPaneComponent extends BaseComponent {
    public enum Orientation { HORIZONTAL, VERTICAL }

    public enum Side { FIRST, SECOND }

    /** The divider's thickness unless {@link #dividerSize} says otherwise. Odd, so its line has a middle. */
    public static final int DIVIDER = 7;
    private static final int MIN_HIT = 6;
    private static final int HANDLE_LEN = 11;
    private static final int HANDLE_THICK = 3;
    private static final int ARROW_CELL = 9;
    private static final int LINE_ALPHA = 0x40;
    private static final int HELD_ALPHA = 0x40;
    private static final float HOVER_WASH = 0.5F;
    private static final double MIN_LIT_CONTRAST = 1.5D;
    private static final int DRAG_OUT = 3;
    private static final int KEY_STEPS_SHIFT = 4;
    private static final int OFF_POINTER = Integer.MIN_VALUE;
    private static final long DOUBLE_CLICK_MS = 350L;
    private static final int FOLD_MS = 120;
    private static final double READABLE = 4.5D;
    private static final int LINE_H = RowLook.FONT_H + 2;

    private final Orientation orientation;
    private final boolean horizontal;
    private final BaseComponent[] panes = new BaseComponent[2];
    private final boolean[] paneRest = {true, true};
    private final boolean[] canCollapse = new boolean[2];
    private final int[] min = {0, 0};
    private final int[] max = {Integer.MAX_VALUE, Integer.MAX_VALUE};
    private final Box[] rects = {new Box("first", 0, 0, 0, 0), new Box("second", 0, 0, 0, 0)};
    private final Divider divider = new Divider();
    private final RowLook.HoverFade hover = new RowLook.HoverFade();

    private int thickness = DIVIDER;
    private int padding = 2;
    private int keyStep = 4;
    private boolean framed;
    private String emptyText;
    private List<String> dividerTip;
    private List<String> arrowTip;
    private IntConsumer onChange;
    private IntConsumer onCommit;
    private BiConsumer<Side, Boolean> onCollapse;
    private Consumer<SplitPaneComponent> onLayout;
    private PanelComponent popupHost;
    private BaseComponent backedPopup;

    // What the divider's place is asked to be: a share of the room, or the pixels of the side in keep.
    private double want = 0.5D;
    private Side keep;
    private Side collapsed;
    private double homeFraction = 0.5D;
    private int homePixels = -1;

    private int shownPos;
    private boolean animating;
    private int animFrom;
    private long animAt;

    private boolean dragging;
    private int grab;
    private int pressPos;
    private double pressWant;
    private Side pressCollapsed;
    private long lastPressAt = Long.MIN_VALUE / 2;

    private BaseComponent current;
    private boolean wasFocused;
    private boolean shownActive = true;
    private boolean laying;
    private Words words;

    /** @param first the left or top pane, may be null; @param second the right or bottom pane, may be null */
    public SplitPaneComponent(Box box, Orientation orientation, BaseComponent first, BaseComponent second) {
        super(box);
        this.orientation = Objects.requireNonNull(orientation, "orientation");
        this.horizontal = orientation == Orientation.HORIZONTAL;
        panes[0] = first;
        panes[1] = second;
        rememberRest();
    }

    @Override
    void words(Words words) {
        this.words = words;
        for (BaseComponent pane : panes) {
            if (pane != null) {
                pane.words(words);
            }
        }
    }

    // ---- setup ----

    /**
     * The first pane's share of the room, 0 to 1, now and as the place a double click goes back to.
     * The room is the box's length less the divider.
     */
    public SplitPaneComponent fraction(double share) {
        homeFraction = Math.max(0D, Math.min(1D, share));
        homePixels = -1;
        goHome();
        return this;
    }

    /** The first pane's size in pixels, now and as the place a double click goes back to. */
    public SplitPaneComponent position(int pixels) {
        homePixels = Math.max(0, pixels);
        goHome();
        return this;
    }

    /** The smallest and largest size of a pane. A box too small for both minimums shares what it has. */
    public SplitPaneComponent limits(Side side, int smallest, int largest) {
        int i = side.ordinal();
        min[i] = Math.max(0, smallest);
        max[i] = Math.max(min[i], largest);
        return this;
    }

    /** Holds this side's pixels when the box changes size, instead of a share. Null goes back to a share. */
    public SplitPaneComponent keepSize(Side side) {
        int pos = target();
        keep = side;
        setWant(pos);
        return this;
    }

    /** Lets the player fold these sides, and shows an arrow for each on the divider. */
    public SplitPaneComponent collapsible(Side... sides) {
        canCollapse[0] = false;
        canCollapse[1] = false;
        for (Side side : sides) {
            canCollapse[side.ordinal()] = true;
        }
        return this;
    }

    /** The divider's thickness in pixels. Keep it odd; below 5 it has no handle or arrow. */
    public SplitPaneComponent dividerSize(int size) {
        thickness = Math.max(1, size);
        return this;
    }

    /** Pixels between a pane's edges and its widget. A focus ring takes 2, the default. */
    public SplitPaneComponent padding(int padding) {
        this.padding = Math.max(0, padding);
        return this;
    }

    /** Pixels per key press or wheel notch. Shift moves four steps. */
    public SplitPaneComponent keyStep(int keyStep) {
        this.keyStep = Math.max(1, keyStep);
        return this;
    }

    /** Draws a well behind both panes, with a lit edge while the split has focus. Off by default. */
    public SplitPaneComponent framed(boolean framed) {
        this.framed = framed;
        return this;
    }

    /** Text in a pane with no widget. */
    public SplitPaneComponent emptyText(String emptyText) {
        this.emptyText = emptyText;
        return this;
    }

    /** Lines over the divider. */
    public SplitPaneComponent dividerTooltip(String... lines) {
        dividerTip = lines.length == 0 ? null : List.of(lines);
        return this;
    }

    /** Lines over the collapse arrows, instead of {@link #dividerTooltip}. */
    public SplitPaneComponent arrowTooltip(String... lines) {
        arrowTip = lines.length == 0 ? null : List.of(lines);
        return this;
    }

    /** Called with the first pane's size each time the player moves the divider. */
    public SplitPaneComponent onChange(IntConsumer onChange) {
        this.onChange = onChange;
        return this;
    }

    /** Called with the first pane's size when a move ends: a release, a key or a wheel notch. */
    public SplitPaneComponent onCommit(IntConsumer onCommit) {
        this.onCommit = onCommit;
        return this;
    }

    /** Called when the player folds or unfolds a side, with its new state. */
    public SplitPaneComponent onCollapse(BiConsumer<Side, Boolean> onCollapse) {
        this.onCollapse = onCollapse;
        return this;
    }

    /** Called when a pane's box changed: place a widget's parts here, from {@link #paneBox}. */
    public SplitPaneComponent onLayout(Consumer<SplitPaneComponent> onLayout) {
        this.onLayout = onLayout;
        return this;
    }

    /**
     * The panel whose open popup may lie over this split. A see-through family (glass) would show the panes
     * and the divider through the popup's lines: with this, the split hides what is under it first. Add the
     * split after what else the popup may cover, since this reaches only what is drawn before the split ends.
     */
    public SplitPaneComponent popupHost(PanelComponent panel) {
        popupHost = panel;
        return this;
    }

    public SplitPaneComponent active(boolean active) {
        this.active = active;
        syncActive();
        return this;
    }

    @Override
    public SplitPaneComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    // ---- content ----

    public BaseComponent first() {
        return panes[0];
    }

    public BaseComponent second() {
        return panes[1];
    }

    /** Puts a widget in a pane; null leaves it empty. The old widget gets back its {@code active} and loses focus. */
    public void setPane(Side side, BaseComponent widget) {
        int i = side.ordinal();
        BaseComponent old = panes[i];
        if (old == widget) {
            return;
        }
        if (old != null) {
            dropFlags();
            old.focused = false;
            if (!active) {
                old.active = paneRest[i];
            }
        }
        panes[i] = widget;
        if (widget != null) {
            if (words != null) {
                widget.words(words);
            }
            paneRest[i] = widget.active;
            if (!active) {
                widget.active = false;
            }
        }
    }

    public Orientation orientation() {
        return orientation;
    }

    /** The first pane's size once the divider rests: 0 when folded, the whole room when the second is. */
    public int position() {
        return target();
    }

    /** The second pane's size once the divider rests. */
    public int secondSize() {
        return Math.min(room() - target(), max[1]);
    }

    /** The first pane's share of the room once the divider rests. */
    public double share() {
        int room = room();
        return room == 0 ? 0D : target() / (double) room;
    }

    /** Moves the divider, within the limits, without calling {@link #onChange}. Keeps the starting place. */
    public void setPosition(int pixels) {
        animating = false;
        setWant(clamp(pixels, room()));
    }

    /** Sets the first pane's share of the room, within the limits, without calling {@link #onChange}. */
    public void setShare(double share) {
        setPosition((int) Math.round(Math.max(0D, Math.min(1D, share)) * room()));
    }

    /** Goes back to the starting place and unfolds, with no callback. */
    public void reset() {
        int from = shownNow();
        collapsed = null;
        goHome();
        animate(from);
    }

    /** Which side is folded, or null. */
    public Side collapsed() {
        return collapsed;
    }

    public boolean isCollapsed(Side side) {
        return collapsed == side;
    }

    /** Folds a side, whether or not {@link #collapsible} offers it to the player, with no callback. */
    public void collapse(Side side) {
        Objects.requireNonNull(side, "side");
        if (collapsed == side) {
            return;
        }
        int from = shownNow();
        BaseComponent pane = panes[side.ordinal()];
        boolean moveKeys = current != null && pane != null && pane.stops().contains(current);
        collapsed = side;
        animate(from);
        if (moveKeys) {
            dropFlags();
            divider.focused = true;
        }
    }

    /** Unfolds, with no callback. The pane comes back at the size it had. */
    public void expand() {
        if (collapsed != null) {
            int from = shownNow();
            collapsed = null;
            animate(from);
        }
    }

    /** True while a fold or a reset is sliding. */
    public boolean animating() {
        return animating;
    }

    /** True while the player holds the divider. */
    public boolean dragging() {
        return dragging;
    }

    /** The divider's strip. */
    Box dividerBox() {
        layout();
        return divider.box().named("divider");
    }

    /** The box a pane's widget gets; its size is 0 when the pane has no room. */
    Box paneBox(Side side) {
        layout();
        return inset(rects[side.ordinal()]);
    }

    /** The whole of a pane, widget and padding, which also clips it. */
    Box paneArea(Side side) {
        layout();
        return rects[side.ordinal()];
    }

    /** The stop the keys go to, or null: a widget in a pane, or the divider, which is not a pane's widget. */
    public BaseComponent keyboardAt() {
        return current;
    }

    /** True when the keys go to this split's divider. */
    public boolean dividerHasKeys() {
        return current == divider;
    }

    // ---- geometry ----

    private int inner() {
        return framed ? 1 : 0;
    }

    private int regionX() {
        return x + inner();
    }

    private int regionY() {
        return y + inner();
    }

    private int regionW() {
        return Math.max(0, w - 2 * inner());
    }

    private int regionH() {
        return Math.max(0, h - 2 * inner());
    }

    private int length() {
        return horizontal ? regionW() : regionH();
    }

    private int room() {
        return Math.max(0, length() - thickness);
    }

    private int firstFromWant(int room) {
        if (keep == null) {
            return (int) Math.round(want * room);
        }
        return keep == Side.FIRST ? (int) Math.round(want) : room - (int) Math.round(want);
    }

    private void setWant(int pos) {
        int room = room();
        if (keep == null) {
            if (room > 0) {
                want = pos / (double) room;
            }
        } else {
            want = keep == Side.FIRST ? pos : room - pos;
        }
    }

    private void goHome() {
        int room = room();
        if (homePixels >= 0) {
            setWant(clamp(homePixels, room));
        } else if (keep == null) {
            want = homeFraction;
        } else {
            setWant((int) Math.round(homeFraction * room));
        }
    }

    /** The first pane's size within the limits. */
    private int clamp(int pos, int room) {
        int lowest;
        int highest;
        long minimums = (long) min[0] + min[1];
        if (minimums > room) {
            // Both minimums do not fit: each gets its part of what there is.
            lowest = (int) (room * (long) min[0] / Math.max(1L, minimums));
            highest = lowest;
        } else {
            lowest = (int) Math.max(min[0], room - (long) max[1]);
            highest = (int) Math.min(max[0], room - (long) min[1]);
            if (lowest > highest) {
                // More room than both maximums: what is left stays blank after the second pane.
                lowest = highest;
            }
        }
        return Math.max(lowest, Math.min(highest, Math.max(0, Math.min(room, pos))));
    }

    /** Where the divider rests. */
    private int target() {
        int room = room();
        if (collapsed == Side.FIRST) {
            return 0;
        }
        if (collapsed == Side.SECOND) {
            return room;
        }
        return clamp(firstFromWant(room), room);
    }

    private void animate(int from) {
        animating = false;
        if (!reducedMotion && from != target()) {
            animating = true;
            animFrom = from;
            animAt = clock.getAsLong();
        }
    }

    private int shownNow() {
        layout();
        return shownPos;
    }

    private int placeNow() {
        int goal = target();
        if (!animating) {
            return goal;
        }
        long age = clock.getAsLong() - animAt;
        if (reducedMotion || age >= FOLD_MS) {
            animating = false;
            return goal;
        }
        float t = 1F - Math.max(0F, (float) age) / FOLD_MS;
        float eased = 1F - t * t * t;
        return animFrom + Math.round((goal - animFrom) * eased);
    }

    private Box area(String name, int along, int size) {
        return horizontal
                ? new Box(name, regionX() + along, regionY(), size, regionH())
                : new Box(name, regionX(), regionY() + along, regionW(), size);
    }

    private Box inset(Box rect) {
        return new Box(rect.name(), rect.x() + padding, rect.y() + padding,
                Math.max(0, rect.w() - 2 * padding), Math.max(0, rect.h() - 2 * padding));
    }

    private void layout() {
        if (laying) {
            return;
        }
        laying = true;
        int room = room();
        int pos = Math.max(0, Math.min(room, placeNow()));
        shownPos = pos;
        Box first = area("first", 0, Math.min(pos, max[0]));
        Box second = area("second", pos + thickness, Math.max(0, Math.min(room - pos, max[1])));
        divider.place(area("divider", pos, Math.min(thickness, Math.max(0, length() - pos))));
        boolean changed = !first.equals(rects[0]) || !second.equals(rects[1]);
        rects[0] = first;
        rects[1] = second;
        for (int i = 0; i < 2; i++) {
            if (panes[i] != null) {
                place(panes[i], inset(rects[i]));
            }
        }
        laying = false;
        if (changed && onLayout != null) {
            onLayout.accept(this);
        }
    }

    private static void place(BaseComponent widget, Box to) {
        move(widget, to.x() - widget.x, to.y() - widget.y);
        widget.w = to.w();
        widget.h = to.h();
        if (widget instanceof SplitPaneComponent split) {
            split.layout();
        }
    }

    private static void move(BaseComponent widget, int dx, int dy) {
        if (dx == 0 && dy == 0) {
            return;
        }
        widget.x += dx;
        widget.y += dy;
        if (widget instanceof ScrollComponent scroll) {
            for (BaseComponent child : scroll.children()) {
                move(child, dx, dy);
            }
        }
    }

    private boolean paneLive(int i) {
        BaseComponent pane = panes[i];
        return pane != null && pane.visible && pane.w > 0 && pane.h > 0 && rects[i].w() > 0 && rects[i].h() > 0;
    }

    /** Live and not folding: a pane on its way shut is still drawn, but takes no more input. */
    private boolean paneOpen(int i) {
        return paneLive(i) && (collapsed == null || collapsed.ordinal() != i);
    }

    private int paneAt(double mx, double my) {
        for (int i = 0; i < 2; i++) {
            if (paneOpen(i) && rects[i].contains(mx, my)) {
                return i;
            }
        }
        return -1;
    }

    private boolean overDivider(double mx, double my) {
        Box g = divider.box();
        if (g.w() <= 0 || g.h() <= 0 || !isOver(mx, my)) {
            return false;
        }
        int slop = Math.max(0, (MIN_HIT - thickness + 1) / 2);
        return horizontal
                ? mx >= g.x() - slop && mx < g.right() + slop && my >= g.y() && my < g.bottom()
                : my >= g.y() - slop && my < g.bottom() + slop && mx >= g.x() && mx < g.right();
    }

    private int alongOf(double mx, double my) {
        return (int) Math.floor(horizontal ? mx : my);
    }

    /** The sides that have an arrow right now, in the order they are drawn. */
    private List<Side> arrows() {
        if (thickness < 5) {
            return List.of();
        }
        List<Side> out = new ArrayList<>(2);
        if (collapsed != null) {
            if (canCollapse[collapsed.ordinal()]) {
                out.add(collapsed);
            }
        } else {
            if (canCollapse[0]) {
                out.add(Side.FIRST);
            }
            if (canCollapse[1]) {
                out.add(Side.SECOND);
            }
        }
        Box g = divider.box();
        int along = horizontal ? g.h() : g.w();
        return out.size() * ARROW_CELL + 2 > along || (horizontal ? g.w() : g.h()) < thickness ? List.of() : out;
    }

    private Side arrowAt(double mx, double my) {
        List<Side> sides = arrows();
        Box g = divider.box();
        int offset = horizontal ? (int) Math.floor(my) - g.y() : (int) Math.floor(mx) - g.x();
        int cell = offset < 1 ? -1 : (offset - 1) / ARROW_CELL;
        return cell >= 0 && cell < sides.size() && overDivider(mx, my) ? sides.get(cell) : null;
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        syncActive();
        layout();
        sync();
        Palette p = t.palette();
        if (framed) {
            t.frame().well(d, p, x, y, w, h, focused && active);
        }
        int pane = dragging ? -1 : paneAt(mx, my);
        for (int i = 0; i < 2; i++) {
            Box rect = rects[i];
            if (rect.w() <= 0 || rect.h() <= 0) {
                continue;
            }
            if (panes[i] == null) {
                drawEmpty(d, p, rect);
            } else if (paneLive(i)) {
                d.scissor(rect.x(), rect.y(), rect.right(), rect.bottom());
                panes[i].draw(d, t, pane == i ? mx : OFF_POINTER, pane == i ? my : OFF_POINTER, delta);
                d.endScissor();
            }
        }
        drawDivider(d, p, mx, my, hovered);
        backPopup(d, t);
    }

    private void backPopup(Draw d, Theme t) {
        BaseComponent popup = popupHost == null ? null : popupHost.overlay();
        // A popup places itself as it draws, so its box is only known from its second frame.
        if (popup != null && popup == backedPopup && t.palette().surface() >>> 24 != 0xFF) {
            Box box = popup.box();
            int solid = t.palette().surface() | 0xFF000000;
            int corner = Math.max(1, t.frame().corner());
            d.nextLayer();
            d.fill(box.x() + corner, box.y() + 1, box.right() - corner, box.bottom() - 1, solid);
            d.fill(box.x() + 1, box.y() + corner, box.right() - 1, box.bottom() - corner, solid);
        }
        backedPopup = popup;
    }

    private void drawEmpty(Draw d, Palette p, Box rect) {
        if (emptyText == null || emptyText.isEmpty()) {
            return;
        }
        int back = (framed ? p.well() : p.surface()) | 0xFF000000;
        int ink = !active ? p.disabledText() : Shapes.contrast(p.textSoft(), back) >= READABLE ? p.textSoft() : p.text();
        int room = Math.max(1, rect.w() - 6);
        List<String> lines = d.wrapAll(emptyText, room);
        int count = Math.min(Math.max(1, rect.h() / LINE_H), lines.size());
        int top = rect.y() + Math.max(0, (rect.h() - count * LINE_H) / 2) + 1;
        int middle = rect.x() + rect.w() / 2;
        d.scissor(rect.x(), rect.y(), rect.right(), rect.bottom());
        for (int i = 0; i < count; i++) {
            String line = i == count - 1 && count < lines.size() ? d.ellipsize(lines.get(i) + Draw.ELLIPSIS, room) : lines.get(i);
            d.text(line, middle - d.width(line) / 2, top + i * LINE_H, ink);
        }
        d.endScissor();
    }

    private void drawDivider(Draw d, Palette p, int mx, int my, boolean hovered) {
        Box g = divider.box();
        if (g.w() <= 0 || g.h() <= 0) {
            hover.step(-1);
            return;
        }
        boolean over = active && hovered && !dragging && overDivider(mx, my);
        Side overArrow = over ? arrowAt(mx, my) : null;
        hover.step(!over ? -1 : overArrow == null ? 0 : 1 + overArrow.ordinal());
        boolean lit = active && (dragging || divider.focused);
        int soft = softInk(p);

        // The hover is the slightest of washes: it must stay quieter than the held or focused divider.
        if (dragging) {
            d.fill(g.x(), g.y(), g.right(), g.bottom(), Shapes.alpha(p.primary(), HELD_ALPHA));
        } else if (over && overArrow == null) {
            d.fill(g.x(), g.y(), g.right(), g.bottom(), RowLook.tint(p, HOVER_WASH * hover.fade()));
        }
        List<Side> sides = arrows();
        int cross = (thickness - 1) / 2;
        int along = horizontal ? g.h() : g.w();
        // A ring runs along the strip's edges: the line stops short of its ends.
        int ringGap = divider.focused && !dragging ? 2 + inner() : 0;
        int lineFrom = sides.isEmpty() ? ringGap : sides.size() * ARROW_CELL + 1;
        int lineTo = along - ringGap;
        int lineInk = !active ? Shapes.alpha(p.disabledText(), LINE_ALPHA)
                : dragging ? p.primary() : Shapes.alpha(p.text(), LINE_ALPHA);
        if (lineFrom < lineTo) {
            strip(d, g, lineFrom, lineTo, cross, cross + 1, lineInk);
        }
        for (int i = 0; i < sides.size(); i++) {
            drawArrow(d, p, g, i, sides.get(i), sides.get(i) == overArrow, soft);
        }
        if (thickness >= 5 && (collapsed == null || sides.isEmpty())) {
            int handle = Math.min(HANDLE_LEN, along - 2);
            int from = (along - handle) / 2;
            if (handle >= 3 && from >= lineFrom) {
                int ink = !active ? p.disabledText() : lit ? litInk(p) : soft;
                int across = (thickness - HANDLE_THICK) / 2;
                strip(d, g, from, from + handle, across, across + HANDLE_THICK, ink);
            }
        }
    }

    /** The accent, unless the theme makes it too close to what is behind it to tell. */
    private int litInk(Palette p) {
        int back = (framed ? p.well() : p.surface()) | 0xFF000000;
        return Shapes.contrast(p.primary(), back) >= MIN_LIT_CONTRAST ? p.primary() : p.text();
    }

    /** Fills part of the divider: along its length, then across its thickness. */
    private void strip(Draw d, Box g, int fromAlong, int toAlong, int fromCross, int toCross, int color) {
        if (horizontal) {
            d.fill(g.x() + fromCross, g.y() + fromAlong, g.x() + toCross, g.y() + toAlong, color);
        } else {
            d.fill(g.x() + fromAlong, g.y() + fromCross, g.x() + toAlong, g.y() + toCross, color);
        }
    }

    private void drawArrow(Draw d, Palette p, Box g, int index, Side side, boolean over, int soft) {
        int cellFrom = 1 + index * ARROW_CELL;
        if (over) {
            strip(d, g, cellFrom, cellFrom + ARROW_CELL, 0, thickness, RowLook.tint(p, hover.fade()));
        }
        int ink = !active ? p.disabledText() : over ? p.text() : soft;
        // It points the way the divider will go: towards the side it folds, away from a folded side.
        boolean towardsFirst = (side == Side.FIRST) != (collapsed == side);
        int middleAlong = cellFrom + ARROW_CELL / 2;
        int middleCross = (thickness - 1) / 2;
        for (int k = 0; k < 3; k++) {
            int rowCross = towardsFirst ? middleCross - 1 + k : middleCross + 1 - k;
            strip(d, g, middleAlong - k, middleAlong + k + 1, rowCross, rowCross + 1, ink);
        }
    }

    private int softInk(Palette p) {
        int back = (framed ? p.well() : p.surface()) | 0xFF000000;
        return Shapes.contrast(p.textSoft(), back) >= READABLE ? p.textSoft() : p.text();
    }

    // ---- state ----

    private void rememberRest() {
        for (int i = 0; i < 2; i++) {
            if (panes[i] != null) {
                paneRest[i] = panes[i].active;
            }
        }
    }

    /** {@code active} is a public field: this notices a change at the next frame or input. */
    private void syncActive() {
        if (active == shownActive) {
            return;
        }
        shownActive = active;
        divider.active = active;
        if (active) {
            for (int i = 0; i < 2; i++) {
                if (panes[i] != null) {
                    panes[i].active = paneRest[i];
                }
            }
        } else {
            endDrag(true);
            rememberRest();
            for (BaseComponent pane : panes) {
                if (pane != null) {
                    pane.active = false;
                }
            }
        }
    }

    private void fireChange(int before) {
        int now = target();
        if (now != before && onChange != null) {
            onChange.accept(now);
        }
    }

    private void fireCommit() {
        if (onCommit != null) {
            onCommit.accept(target());
        }
    }

    private void fireCollapse(Side side, boolean now) {
        if (onCollapse != null) {
            onCollapse.accept(side, now);
        }
    }

    /** The player folds or unfolds a side. */
    private void toggleSide(Side side) {
        BaseComponent.click();
        if (collapsed == side) {
            expand();
            fireCollapse(side, false);
        } else {
            Side before = collapsed;
            if (before != null) {
                fireCollapse(before, false);
            }
            collapse(side);
            fireCollapse(side, true);
        }
    }

    /** Goes back to the starting place, as the player's double click or Enter does. */
    private void resetByPlayer() {
        BaseComponent.click();
        int before = target();
        Side folded = collapsed;
        reset();
        if (folded != null) {
            fireCollapse(folded, false);
        }
        fireChange(before);
        if (target() != before) {
            fireCommit();
        }
    }

    private void nudge(int by) {
        if (collapsed != null) {
            return;
        }
        jump(target() + by);
    }

    private void jump(int to) {
        int before = target();
        animating = false;
        setWant(clamp(to, room()));
        int now = target();
        if (now != before) {
            fireChange(before);
            fireCommit();
        }
    }

    private void endDrag(boolean commit) {
        if (!dragging) {
            return;
        }
        dragging = false;
        if (target() != pressPos || collapsed != pressCollapsed) {
            // A press after a real move starts a new drag: only a press that stayed put can be half of a double click.
            lastPressAt = Long.MIN_VALUE / 2;
            if (commit) {
                fireCommit();
            }
        }
    }

    private void cancelDrag() {
        int before = target();
        Side folded = collapsed;
        dragging = false;
        want = pressWant;
        collapsed = pressCollapsed;
        if (folded != collapsed && pressCollapsed != null) {
            fireCollapse(pressCollapsed, true);
        }
        fireChange(before);
    }

    private void dragTo(double mx, double my) {
        int origin = horizontal ? regionX() : regionY();
        int room = room();
        int to = alongOf(mx, my) - grab - origin;
        if (collapsed != null) {
            // A folded divider stays put until it is pulled out by more than a slip of the hand.
            boolean out = collapsed == Side.FIRST ? to >= DRAG_OUT : to <= room - DRAG_OUT;
            if (!out) {
                return;
            }
            Side was = collapsed;
            collapsed = null;
            fireCollapse(was, false);
        }
        int before = target();
        setWant(clamp(to, room));
        fireChange(before);
    }

    // ---- keyboard's place ----

    private void dropFlags() {
        for (BaseComponent stop : stops()) {
            stop.focused = false;
        }
        divider.focused = false;
        for (BaseComponent pane : panes) {
            if (pane != null) {
                pane.focused = false;
            }
        }
        current = null;
    }

    /**
     * A panel sets a stop's {@code focused} but never clears the one before it when both have the same
     * owner, as all the stops here do. So the flag that is new is the one that counts: it wins, and the
     * panes are told who holds the keys, since a {@link ScrollComponent} clears its children unless focused.
     */
    private void sync() {
        List<BaseComponent> all = stops();
        BaseComponent newest = null;
        for (BaseComponent stop : all) {
            if (stop.focused && stop != current) {
                newest = stop;
            }
        }
        if (newest != null) {
            current = newest;
            focused = true;
            for (BaseComponent stop : all) {
                if (stop != newest) {
                    stop.focused = false;
                }
            }
            reveal(newest);
        } else if (current != null && (!focused || !current.focused || !all.contains(current))) {
            current.focused = false;
            current = null;
        } else if (focused && !wasFocused && current == null && active && all.contains(divider)) {
            // The panel handed its focus to the split itself: the divider is what the keys can move.
            divider.focused = true;
            current = divider;
        }
        wasFocused = focused;
        for (int i = 0; i < 2; i++) {
            BaseComponent pane = panes[i];
            if (pane != null) {
                pane.focused = current != null && paneOpen(i) && pane.stops().contains(current);
            }
        }
    }

    private void reveal(BaseComponent stop) {
        for (BaseComponent pane : panes) {
            if (pane instanceof ScrollComponent scroll && scroll.stops().contains(stop)) {
                Box box = stop.focusBox();
                scroll.reveal(new Box("stop", box.x(), box.y() + scroll.scroll() - 2, box.w(), box.h() + 4));
            }
        }
    }

    // ---- input ----

    private boolean accepts() {
        return visible && active && settled();
    }

    @Override
    public List<BaseComponent> stops() {
        layout();
        List<BaseComponent> out = new ArrayList<>();
        addStops(out, 0);
        if (divider.navigable()) {
            out.add(divider);
        }
        addStops(out, 1);
        return out;
    }

    private void addStops(List<BaseComponent> out, int i) {
        if (paneOpen(i) && panes[i].active) {
            out.addAll(panes[i].stops());
        }
    }

    @Override
    protected boolean takesFocus() {
        return active;
    }

    @Override
    protected boolean capturesKeys() {
        return current != null && current.visible && current.capturesKeys();
    }

    @Override
    public boolean keyCaptured(KeyPress press) {
        return current != null && current.keyCaptured(press);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        syncActive();
        if (!accepts() || !isOver(mx, my)) {
            return false;
        }
        wasFocused = focused;
        layout();
        if (overDivider(mx, my)) {
            dropFlags();
            if (focused) {
                divider.focused = true;
            }
            sync();
            if (button == 0) {
                pressDivider(mx, my);
            }
            return true;
        }
        int i = paneAt(mx, my);
        dropFlags();
        boolean took = false;
        if (i >= 0) {
            BaseComponent pane = panes[i];
            pane.focused = focused && pane.active && pane.takesFocus() && pane.isOver(mx, my);
            took = pane.mouseClicked(mx, my, button);
        }
        sync();
        return took;
    }

    private void pressDivider(double mx, double my) {
        long now = clock.getAsLong();
        Side arrow = arrowAt(mx, my);
        if (arrow != null) {
            lastPressAt = Long.MIN_VALUE / 2;
            toggleSide(arrow);
            return;
        }
        if (now - lastPressAt <= DOUBLE_CLICK_MS) {
            lastPressAt = Long.MIN_VALUE / 2;
            resetByPlayer();
            return;
        }
        lastPressAt = now;
        animating = false;
        dragging = true;
        Box g = divider.box();
        grab = alongOf(mx, my) - (horizontal ? g.x() : g.y());
        pressPos = target();
        pressWant = want;
        pressCollapsed = collapsed;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        syncActive();
        if (dragging) {
            if (!visible) {
                endDrag(true);
                return false;
            }
            dragTo(mx, my);
            layout();
            return true;
        }
        if (!accepts()) {
            return false;
        }
        for (int i = 0; i < 2; i++) {
            if (paneOpen(i) && panes[i].mouseDragged(mx, my, button, dx, dy)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        syncActive();
        boolean was = dragging && button == 0;
        if (was) {
            endDrag(true);
        }
        for (BaseComponent pane : panes) {
            if (pane != null) {
                was |= pane.mouseReleased(mx, my, button);
            }
        }
        return was;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        syncActive();
        if (!visible || !settled() || !isOver(mx, my)) {
            return false;
        }
        layout();
        if (overDivider(mx, my)) {
            if (!active) {
                return false;
            }
            if (!dragging) {
                int by = (int) Math.round(amount * keyStep);
                nudge(by != 0 ? -by : amount > 0 ? -keyStep : keyStep);
            }
            return true;
        }
        int i = paneAt(mx, my);
        return i >= 0 && panes[i].mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        syncActive();
        if (!visible || !settled()) {
            return false;
        }
        layout();
        sync();
        return active && current != null && current.keyPressed(key, shift);
    }

    @Override
    public boolean charTyped(int codePoint) {
        syncActive();
        if (!accepts()) {
            return false;
        }
        sync();
        return current != null && current.charTyped(codePoint);
    }

    @Override
    public void tick() {
        for (BaseComponent pane : panes) {
            if (pane != null) {
                pane.tick();
            }
        }
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!visible || !settled() || !isOver(mx, my)) {
            return null;
        }
        if (dragging) {
            return List.of();
        }
        layout();
        if (overDivider(mx, my)) {
            if (active && arrowTip != null && arrowAt(mx, my) != null) {
                return arrowTip;
            }
            return dividerTip != null ? dividerTip : tooltip;
        }
        int i = paneAt(mx, my);
        if (i >= 0) {
            List<String> lines = panes[i].tooltipAt(mx, my);
            if (lines != null) {
                return lines;
            }
        }
        return tooltip;
    }

    /** The divider as the panel sees it: its own stop, whose box is the strip and not the whole split. */
    private final class Divider extends BaseComponent {
        Divider() {
            super(0, 0, 0, 0);
        }

        void place(Box box) {
            x = box.x();
            y = box.y();
            w = box.w();
            h = box.h();
        }

        @Override
        protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        }

        @Override
        public boolean isOver(double mx, double my) {
            return SplitPaneComponent.this.visible && overDivider(mx, my);
        }

        @Override
        protected boolean takesFocus() {
            return SplitPaneComponent.this.active;
        }

        @Override
        protected boolean navigable() {
            return SplitPaneComponent.this.active && w > 0 && h > 0;
        }

        /**
         * The ring grows by 2 on each side: this lays it on the strip's edges, so it covers no pane. In a
         * frame it stops 1 px short of the well's edge, so the two never make a double line.
         */
        @Override
        protected Box focusBox() {
            int across = 2;
            int along = 2 + inner();
            return horizontal
                    ? new Box("divider", x + across, y + along, Math.max(0, w - 2 * across), Math.max(0, h - 2 * along))
                    : new Box("divider", x + along, y + across, Math.max(0, w - 2 * along), Math.max(0, h - 2 * across));
        }

        @Override
        protected int focusCorner() {
            return 0;
        }

        @Override
        protected boolean activate() {
            if (!accepts()) {
                return false;
            }
            if (collapsed != null) {
                toggleSide(collapsed);
            } else if (canCollapse[0]) {
                toggleSide(Side.FIRST);
            } else if (canCollapse[1]) {
                toggleSide(Side.SECOND);
            } else {
                resetByPlayer();
            }
            return true;
        }

        @Override
        public boolean keyPressed(InputKey key, boolean shift) {
            if (!accepts()) {
                return false;
            }
            if (key == InputKey.ESCAPE && dragging) {
                cancelDrag();
                return true;
            }
            boolean back = horizontal ? key == InputKey.LEFT : key == InputKey.UP;
            boolean forth = horizontal ? key == InputKey.RIGHT : key == InputKey.DOWN;
            if (back || forth) {
                if (collapsed == null) {
                    int step = shift ? KEY_STEPS_SHIFT * keyStep : keyStep;
                    nudge(back ? -step : step);
                } else if (collapsed == Side.FIRST ? forth : back) {
                    toggleSide(collapsed);
                }
                return true;
            }
            switch (key) {
                case HOME, END -> {
                    if (collapsed != null) {
                        toggleSide(collapsed);
                    }
                    jump(key == InputKey.HOME ? 0 : room());
                    return true;
                }
                case ENTER, SPACE -> {
                    return activate();
                }
                default -> {
                    return false;
                }
            }
        }
    }
}
