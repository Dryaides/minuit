package dev.dryaides.minuit;

import java.util.List;
import java.util.function.Consumer;

/**
 * A closed button with an arrow; a click lays its list over the panel through {@link PanelComponent#overlay}.
 * The list has group headers and entries, each with its own state and action. The list may be wider
 * than the button: it takes {@code listX} and {@code listW} from the panel.
 */
public final class DropdownComponent extends BaseComponent {

    public enum State {
        /** In effect now: a filled mark, not clickable. */
        SELECTED,
        /** Ready: a click makes it the selection. */
        SELECTABLE,
        /** Not there yet: a click fetches it, never selects it. */
        GET,
        /** On its way: a bar, and a click calls it off. */
        DOWNLOADING,
        /** The fetch failed: a red line, a click tries again. */
        FAILED,
        /** Not available on this machine: faint, not clickable. */
        DISABLED
    }

    public sealed interface Line permits Header, Entry {
    }

    public record Header(String text) implements Line {
    }

    /**
     * @param detail   the short line after the name: a tag, a failure, a percentage
     * @param fraction how far a download got, negative when unknown
     */
    public record Entry(String name, String detail, String size, String ram, State state, float fraction,
                        List<String> tooltip, Runnable action) implements Line {
    }

    public static final int LINE = 12;
    public static final int HEADER = 11;

    public String label;
    public boolean primary;
    public List<Line> lines = List.of();
    private final int listX;
    private final int listW;
    private final Consumer<BaseComponent> show;

    /** @param show the panel's overlay setter: the list to open it, null to close it */
    public DropdownComponent(Box box, String label, int listX, int listW, Consumer<BaseComponent> show) {
        super(box);
        this.label = label;
        this.listX = listX;
        this.listW = listW;
        this.show = show;
    }

    /** The list's height for these lines, frame included, in the vanilla family. */
    public static int listHeight(List<Line> lines) {
        return listHeight(FrameStyles.VANILLA, lines);
    }

    /** The list's height for these lines in {@code frame}: the rows keep {@link PopupList#inset} on every side. */
    public static int listHeight(FrameStyle frame, List<Line> lines) {
        Insets in = PopupList.inset(frame);
        int h = in.top() + in.bottom();
        for (Line line : lines) {
            h += line instanceof Header ? HEADER : LINE;
        }
        return h;
    }

    @Override
    protected Box focusBox(FrameStyle frame) {
        return FrameStyle.withBleed(frame, focusBox());
    }

    @Override
    protected int focusCorner(FrameStyle frame) {
        return frame.buttonCorner();
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        int textColor;
        if (!this.active && !FrameStyle.bleedOf(theme.frame()).equals(Insets.NONE)) {
            theme.frame().button(d, p, this.x, this.y, this.w, this.h, null, p.disabled(), Shapes.Relief.RAISED);
            textColor = p.disabledText();
        } else if (!this.active) {
            Shapes.rounded(d, this.x, this.y, this.w, this.h, 3, p.wellEdge());
            Shapes.rounded(d, this.x + 1, this.y + 1, this.w - 2, this.h - 2, 2, p.disabled());
            textColor = p.disabledText();
        } else {
            int base = this.primary ? p.primary() : p.raised();
            int hover = this.primary ? p.primaryHover() : p.raisedHover();
            theme.frame().button(d, p, this.x, this.y, this.w, this.h, null, Shapes.mix(base, hover, this.hoverAnim),
                    Shapes.Relief.RAISED);
            textColor = this.primary ? p.primaryText() : Shapes.textFor(p, p.raisedText(), base);
        }
        IconArt caret = theme.icon(Icon.CARET);
        d.textIn(this.label, new Box("", this.x + 5, this.y, this.w - 14 - caret.width(), this.h), textColor);
        caret.draw(d, this.x + this.w - 5 - caret.width(), Draw.iconTop(this.y + (this.h - 8) / 2, caret.height()),
                textColor, this.active ? 1F : 0.55F);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.active || !isOver(mx, my) || button != 0) {
            return false;
        }
        return activate();
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
        this.show.accept(new Popup());
        return true;
    }

    private static boolean clickable(Entry entry) {
        return entry.state() != State.SELECTED && entry.state() != State.DISABLED;
    }

    /**
     * The open list. Its height follows the lines, which the panel refreshes every tick. Up and Down
     * move a ring over the entries that can be clicked, Enter and Space click the ringed one.
     */
    private final class Popup extends BaseComponent {

        /** The ringed line, an index into the dropdown's lines; -1 before the first arrow. */
        private int keyed = -1;
        private Insets in = PopupList.inset(FrameStyles.VANILLA);

        Popup() {
            super(DropdownComponent.this.listX, DropdownComponent.this.y + DropdownComponent.this.h + 1, DropdownComponent.this.listW,
                    listHeight(DropdownComponent.this.lines));
        }

        @Override
        protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
            Palette p = theme.palette();
            this.in = PopupList.inset(theme.frame());
            this.h = listHeight(theme.frame(), DropdownComponent.this.lines);
            // below the band a family draws under the dropdown's box
            this.y = DropdownComponent.this.y + DropdownComponent.this.h + 1 + FrameStyle.bleedOf(theme.frame()).bottom();
            this.tooltip = null;
            theme.frame().popup(d, p, this.x, this.y, this.w, this.h);
            int y = this.y + this.in.top();
            for (Line line : DropdownComponent.this.lines) {
                if (line instanceof Header header) {
                    d.textIn(header.text(), new Box("", this.x + 8, y + 1, this.w - 12, HEADER - 1), p.textSoft());
                    y += HEADER;
                    continue;
                }
                Entry entry = (Entry) line;
                boolean over = mx >= this.x && mx < this.x + this.w && my >= y && my < y + LINE;
                boolean clickable = clickable(entry);
                if (over && clickable) {
                    theme.frame().row(d, p, this.x + in.left(), y, this.w - in.left() - in.right(), LINE, p.raisedHover());
                    this.tooltip = entry.tooltip().isEmpty() ? null : entry.tooltip();
                } else if (over && !entry.tooltip().isEmpty()) {
                    this.tooltip = entry.tooltip();
                }
                drawEntry(d, theme, p, entry, y, over && clickable);
                if (DropdownComponent.this.lines.indexOf(line) == this.keyed) {
                    theme.frame().plate(d, p, this.x + in.left() - 1, y - 1, this.w - in.left() - in.right() + 2, LINE + 2, 0, p.text());
                }
                y += LINE;
            }
        }

        private void drawEntry(Draw d, Theme theme, Palette p, Entry entry, int y, boolean lit) {
            boolean faint = entry.state() == State.DISABLED;
            // the lit row sits on raisedHover, so its ink is the button ink, not the surface text
            int litInk = Shapes.textFor(p, p.raisedText(), p.raisedHover());
            int text = faint ? p.textFaint() : lit ? litInk : p.text();
            int quiet = lit ? Shapes.alpha(litInk, 0xB0) : p.textFaint();
            int mark = this.x + 10;
            int my = y + LINE / 2;
            switch (entry.state()) {
                case SELECTED -> {
                    ring(d, mark, my, p.accent());
                    // the filled inner square, one pixel clear of the ring all round
                    d.fill(mark - 1, my - 1, mark + 2, my + 2, p.accent());
                }
                case SELECTABLE -> ring(d, mark, my, lit ? litInk : p.textSoft());
                case GET -> icon(d, theme, Icon.DOWNLOAD, mark, my, p.accent());
                case DOWNLOADING -> icon(d, theme, Icon.CLOSE, mark, my, p.textSoft());
                case FAILED -> icon(d, theme, Icon.REFRESH, mark, my, p.bad());
                default -> {
                }
            }
            int nameX = this.x + 20;
            int detailX = this.x + 112;
            int sizeRight = this.x + this.w - 92;
            d.textIn(entry.name(), new Box("", nameX, y + 1, detailX - nameX - 4, LINE - 2), text);
            int detailW = sizeRight - 52 - detailX;
            if (entry.state() == State.DOWNLOADING) {
                int barW = Math.min(110, detailW - 36);
                theme.frame().bar(d, p, detailX, y + 4, barW, 4, Math.max(0F, entry.fraction()), p.accent());
                d.textIn(entry.detail(), new Box("", detailX + barW + 4, y + 1, detailW - barW - 4, LINE - 2),
                        lit ? quiet : p.textSoft());
            } else {
                d.textIn(entry.detail(), new Box("", detailX, y + 1, detailW, LINE - 2),
                        entry.state() == State.FAILED ? p.bad() : quiet);
            }
            d.textInRight(entry.size(), new Box("", sizeRight - 48, y + 1, 48, LINE - 2), quiet);
            d.textInRight(entry.ram(), new Box("", this.x + this.w - 86, y + 1, 80, LINE - 2), quiet);
        }

        /**
         * The radio mark: a 7 x 7 box with its corner pixels cut, centred on pixel ({@code cx}, {@code cy}),
         * which is the middle row of the label's capitals; the same box on every row.
         */
        private void ring(Draw d, int cx, int cy, int color) {
            d.fill(cx - 2, cy - 3, cx + 3, cy - 2, color);
            d.fill(cx - 2, cy + 3, cx + 3, cy + 4, color);
            d.fill(cx - 3, cy - 2, cx - 2, cy + 3, color);
            d.fill(cx + 3, cy - 2, cx + 4, cy + 3, color);
        }

        private void icon(Draw d, Theme theme, Icon icon, int cx, int cy, int color) {
            IconArt drawn = theme.icon(icon);
            drawn.draw(d, cx - drawn.width() / 2, cy - drawn.height() / 2, color, 1F);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int y = this.y + this.in.top();
            for (Line line : DropdownComponent.this.lines) {
                int h = line instanceof Header ? HEADER : LINE;
                if (line instanceof Entry entry && my >= y && my < y + h) {
                    pick(entry);
                    return true;
                }
                y += h;
            }
            return true;
        }

        private void pick(Entry entry) {
            switch (entry.state()) {
                case SELECTABLE -> {
                    click();
                    DropdownComponent.this.show.accept(null);
                    entry.action().run();
                }
                case GET, DOWNLOADING, FAILED -> {
                    click();
                    entry.action().run();
                }
                default -> {
                }
            }
        }

        @Override
        public boolean keyPressed(InputKey key, boolean shift) {
            int dir = key == InputKey.UP ? -1 : key == InputKey.DOWN ? 1 : 0;
            List<Line> lines = DropdownComponent.this.lines;
            if (dir == 0 || lines.isEmpty()) {
                return false;
            }
            int at = this.keyed;
            for (int i = 0; i < lines.size(); i++) {
                at = Math.floorMod(at < 0 && dir < 0 ? -1 : at + dir, lines.size());
                if (lines.get(at) instanceof Entry entry && clickable(entry)) {
                    this.keyed = at;
                    return true;
                }
            }
            return true;
        }

        @Override
        protected boolean activate() {
            List<Line> lines = DropdownComponent.this.lines;
            if (this.keyed >= 0 && this.keyed < lines.size() && lines.get(this.keyed) instanceof Entry entry) {
                pick(entry);
                return true;
            }
            return false;
        }
    }
}
