package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Two lists with buttons between them that move the checked rows across: a whitelist, an item filter's allowed and
 * blocked sides, the modules a profile loads. Both sides keep the order of the list given, so a row moved back
 * returns to its place.
 *
 * <pre>{@code
 * add(new TransferComponent<>(at(new Box("mods", 10, 10, 220, 120)), allMods, enabledMods, Mod::name,
 *         enabled -> save(enabled)).titles("Available", "Enabled"));
 * }</pre>
 *
 * <p>The keyboard stops on the left list, the two buttons, then the right list. In a list, Up and Down move the
 * row cursor, Home and End go to the ends, Enter or Space checks the row; past the first or last row the keys go
 * on to the next stop. The mouse wheel scrolls the list under it.
 */
public class TransferComponent<T> extends BaseComponent {

    public static final int ROW = 13;
    /** The title line above each list. */
    public static final int HEADER = 12;

    private static final int BUTTON = 16;
    private static final int MIDDLE = BUTTON + 8;
    private static final int PAD = 3;

    private final List<T> order;
    private final Function<T, String> name;
    private final Consumer<List<T>> onChange;
    private final Pane left = new Pane();
    private final Pane right = new Pane();
    private final ButtonComponent toRight;
    private final ButtonComponent toLeft;
    private String leftTitle = "";
    private String rightTitle = "";
    private String emptyText;
    private String rightTip;
    private String leftTip;

    /**
     * @param items  every row, in the order both sides show them
     * @param chosen the rows that start on the right side
     */
    public TransferComponent(Box box, List<T> items, Collection<T> chosen, Function<T, String> name,
                             Consumer<List<T>> onChange) {
        super(box);
        this.order = new ArrayList<>(new LinkedHashSet<>(items));
        this.name = name == null ? String::valueOf : name;
        this.onChange = onChange == null ? l -> { } : onChange;
        Set<T> start = chosen == null ? Set.of() : new HashSet<>(chosen);
        for (T item : this.order) {
            (start.contains(item) ? this.right : this.left).items.add(item);
        }
        this.toRight = new ButtonComponent(new Box("toRight", 0, 0, BUTTON, BUTTON), "", () -> move(this.left, this.right))
                .icon(Icon.NEXT);
        this.toLeft = new ButtonComponent(new Box("toLeft", 0, 0, BUTTON, BUTTON), "", () -> move(this.right, this.left))
                .icon(Icon.PREV);
    }

    /** The titles over the two lists; each is followed by its checked and total counts. */
    public TransferComponent<T> titles(String left, String right) {
        this.leftTitle = left == null ? "" : left;
        this.rightTitle = right == null ? "" : right;
        return this;
    }

    /** What an empty list says; null leaves it blank. */
    public TransferComponent<T> emptyText(String text) {
        this.emptyText = text;
        return this;
    }

    /** The buttons' tooltips, over the lang file's. */
    public TransferComponent<T> buttonTips(String moveRight, String moveLeft) {
        this.rightTip = moveRight;
        this.leftTip = moveLeft;
        return this;
    }

    public TransferComponent<T> active(boolean active) {
        this.active = active;
        return this;
    }

    /** The rows on the right side, in the given order. */
    public List<T> chosen() {
        return List.copyOf(this.right.items);
    }

    /** The rows on the left side, in the given order. */
    public List<T> available() {
        return List.copyOf(this.left.items);
    }

    /** Checks or unchecks a row on either side, as a click on it does. */
    public TransferComponent<T> check(T item, boolean on) {
        Pane pane = this.left.items.contains(item) ? this.left : this.right.items.contains(item) ? this.right : null;
        if (pane != null) {
            if (on) {
                pane.checked.add(item);
            } else {
                pane.checked.remove(item);
            }
        }
        return this;
    }

    @Override
    void words(Words words) {
        this.toRight.tooltip(text(this.rightTip, words, "minuit.transfer.right", "Move the checked ones right"));
        this.toLeft.tooltip(text(this.leftTip, words, "minuit.transfer.left", "Move the checked ones left"));
    }

    private void move(Pane from, Pane to) {
        if (from.checked.isEmpty()) {
            return;
        }
        List<T> moving = new ArrayList<>();
        for (T item : from.items) {
            if (from.checked.contains(item)) {
                moving.add(item);
            }
        }
        from.items.removeAll(moving);
        from.checked.clear();
        to.items.addAll(moving);
        to.items.sort((a, b) -> Integer.compare(this.order.indexOf(a), this.order.indexOf(b)));
        from.clampCursor();
        this.onChange.accept(chosen());
    }

    private void layout() {
        int paneW = (this.w - MIDDLE) / 2;
        this.left.place(this.x, this.y + HEADER, paneW, this.h - HEADER);
        this.right.place(this.x + this.w - paneW, this.y + HEADER, paneW, this.h - HEADER);
        int bx = this.x + paneW + (MIDDLE - BUTTON) / 2;
        int mid = this.y + HEADER + (this.h - HEADER) / 2;
        this.toRight.x = bx;
        this.toRight.y = mid - BUTTON - 2;
        this.toLeft.x = bx;
        this.toLeft.y = mid + 2;
        this.toRight.active = this.active && !this.left.checked.isEmpty();
        this.toLeft.active = this.active && !this.right.checked.isEmpty();
        this.left.active = this.active;
        this.right.active = this.active;
    }

    private List<BaseComponent> parts() {
        return List.of(this.left, this.toRight, this.toLeft, this.right);
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        layout();
        Palette p = t.palette();
        header(d, p, this.left, this.leftTitle);
        header(d, p, this.right, this.rightTitle);
        for (BaseComponent part : parts()) {
            part.draw(d, t, mx, my, this.delta);
        }
    }

    private void header(Draw d, Palette p, Pane pane, String title) {
        String count = pane.checked.size() + "/" + pane.items.size();
        int countW = TextMeasure.width(count) - 1;
        int ink = this.active ? p.text() : p.disabledText();
        int soft = this.active ? FormLook.soft(p) : p.disabledText();
        int textY = this.y + 1;
        d.textFit(title, pane.x + 1, textY, pane.w - countW - 6, ink);
        d.text(count, pane.x + pane.w - countW - 1, textY, soft);
    }

    @Override
    public List<BaseComponent> stops() {
        layout();
        List<BaseComponent> out = new ArrayList<>();
        if (!this.active) {
            return out;
        }
        for (BaseComponent part : parts()) {
            if (part.visible && part.active) {
                out.addAll(part.stops());
            }
        }
        return out;
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        for (BaseComponent part : parts()) {
            List<String> lines = part.tooltipAt(mx, my);
            if (lines != null) {
                return lines;
            }
        }
        return super.tooltipAt(mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.active || !isOver(mx, my)) {
            return false;
        }
        layout();
        for (BaseComponent part : parts()) {
            if (part.mouseClicked(mx, my, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean any = false;
        for (BaseComponent part : parts()) {
            any |= part.mouseReleased(mx, my, button);
        }
        return any;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return this.left.mouseDragged(mx, my, button, dx, dy) || this.right.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        return this.left.mouseScrolled(mx, my, amount) || this.right.mouseScrolled(mx, my, amount);
    }

    /** One side: a well of rows with a check box each, a row cursor for the keyboard and a scroll bar. */
    private final class Pane extends BaseComponent {
        final List<T> items = new ArrayList<>();
        final Set<T> checked = new HashSet<>();
        private final ScrollBar bar = new ScrollBar();
        private final HoverGlows<T> glows = new HoverGlows<>();
        private FrameStyle frame = FrameStyles.SOFT;
        /** The keyboard's row, or -1 before the keyboard came here. */
        private int cursor = -1;

        Pane() {
            super(0, 0, 0, 0);
        }

        void place(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.bar.content(this.items.size() * ROW, inner());
        }

        private int inner() {
            return Math.max(0, this.h - 4);
        }

        private int barX() {
            return ScrollBar.wellX(this.frame, this.x, this.w);
        }

        private int rowW() {
            return this.w - 4 - (this.bar.max() > 0 ? ScrollBar.room(this.frame) : 0);
        }

        int indexAt(double mx, double my) {
            if (!isOver(mx, my) || this.bar.max() > 0 && mx >= barX()) {
                return -1;
            }
            int index = (int) Math.floor((my - this.y - 2 + this.bar.offset()) / ROW);
            return index >= 0 && index < this.items.size() ? index : -1;
        }

        void clampCursor() {
            if (this.cursor >= this.items.size()) {
                this.cursor = this.items.size() - 1;
            }
        }

        private void reveal(int index) {
            int top = index * ROW;
            if (top < this.bar.offset()) {
                this.bar.offset(top);
            } else if (top + ROW > this.bar.offset() + inner()) {
                this.bar.offset(top + ROW - inner());
            }
        }

        private void toggle(int index) {
            T item = this.items.get(index);
            if (!this.checked.remove(item)) {
                this.checked.add(item);
            }
            click();
        }

        @Override
        protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
            Palette p = t.palette();
            this.frame = t.frame();
            this.bar.content(this.items.size() * ROW, inner());
            this.frame.well(d, p, this.x, this.y, this.w, this.h, false);
            if (this.items.isEmpty()) {
                RowLook.drawEmpty(d, p, TransferComponent.this.emptyText, this.active, this.x, this.y, this.w, this.h);
                return;
            }
            int hoverIndex = this.active && hovered ? indexAt(mx, my) : -1;
            this.glows.frame(clock().getAsLong());
            int side = FrameStyle.checkboxSide(ROW);
            int scroll = this.bar.offset();
            int ink = RowLook.ink(p, p.well(), false, this.active);
            d.scissor(this.x + 1, this.y + 1, this.x + this.w - 1, this.y + this.h - 1);
            for (int i = scroll / ROW; i < this.items.size(); i++) {
                int ry = this.y + 2 + i * ROW - scroll;
                if (ry >= this.y + this.h - 1) {
                    break;
                }
                T item = this.items.get(i);
                boolean on = i == hoverIndex || i == this.cursor && this.focusedByKeys();
                float glow = this.glows.of(item, on);
                if (glow > 0F && this.active) {
                    this.frame.row(d, p, this.x + 2, ry, rowW(), ROW, RowLook.tint(p, glow));
                }
                if (RowLook.textCut(ry, ROW, this.y + 2, inner())) {
                    continue;
                }
                boolean ticked = this.checked.contains(item);
                this.frame.checkbox(d, p, this.x + 2 + PAD, ry + (ROW - side) / 2, side, ticked, on && this.active, this.active);
                int tx = this.x + 2 + PAD + side + 4;
                d.textFit(TransferComponent.this.name.apply(item), tx, RowLook.textY(ry, ROW) + ROW % 2,
                        this.x + 2 + rowW() - tx - PAD + 1, ink);
            }
            d.endScissor();
            this.bar.draw(d, t, barX(), this.y + 2, this.h - 4, hovered && this.bar.overThumb(mx, my, barX(), this.y + 2, this.h - 4));
        }

        /** The cursor shows while the keyboard is on this list; a mouse click elsewhere does not clear it. */
        private boolean focusedByKeys() {
            return this.cursor >= 0;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!this.active || button != 0 || !isOver(mx, my)) {
                return false;
            }
            if (this.bar.max() > 0 && mx >= barX()) {
                return this.bar.press(mx, my, barX(), this.y + 2, this.h - 4);
            }
            int index = indexAt(mx, my);
            if (index >= 0) {
                toggle(index);
            }
            return true;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            return this.bar.drag(my);
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            boolean was = this.bar.dragging();
            this.bar.release();
            return was;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double amount) {
            return isOver(mx, my) && this.bar.wheel(amount, ROW);
        }

        @Override
        protected boolean navigable() {
            return this.active;
        }

        @Override
        protected void enter(boolean forward) {
            if (!this.items.isEmpty()) {
                this.cursor = forward ? Math.max(0, Math.min(this.cursor, this.items.size() - 1)) : this.items.size() - 1;
                reveal(this.cursor);
            }
        }

        @Override
        public boolean keyPressed(InputKey key, boolean shift) {
            int n = this.items.size();
            if (n == 0) {
                return false;
            }
            int at = Math.max(0, this.cursor);
            int next = switch (key) {
                case UP -> at - 1;
                case DOWN -> at + 1;
                case HOME -> 0;
                case END -> n - 1;
                default -> Integer.MIN_VALUE;
            };
            if (next == Integer.MIN_VALUE || next < 0 || next >= n) {
                return false;
            }
            this.cursor = next;
            reveal(next);
            return true;
        }

        @Override
        protected boolean activate() {
            if (this.items.isEmpty()) {
                return false;
            }
            this.cursor = Math.max(0, Math.min(this.cursor, this.items.size() - 1));
            toggle(this.cursor);
            return true;
        }
    }
}
