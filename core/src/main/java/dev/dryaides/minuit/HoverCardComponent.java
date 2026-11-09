package dev.dryaides.minuit;

import java.util.List;
import java.util.Objects;

/**
 * A richer tooltip: when the pointer rests on its anchor, a card with a picture, a title and a few lines comes
 * up after a delay, and stays while the pointer moves onto it. Use {@link PopoverComponent} for anything to
 * press: this card has no widgets, and a player on a keyboard only reads it.
 *
 * <pre>{@code
 * add(new HoverCardComponent(playerHead, this).media(head).title(name).subtitle(rank).text(about));
 * }</pre>
 *
 * <p>Add it <b>after every other widget</b>. It opens with the pointer or with the keyboard's ring on the
 * anchor, flips to the other side when it does not fit, and stays inside the panel or {@link #area(Box)}. While
 * it is open the anchor's own tooltip stays away. A click on the anchor closes it until the pointer leaves, and
 * the click still reaches the anchor. Reduced motion drops the glide, not the delays. On the idle path it only
 * compares numbers.
 */
public class HoverCardComponent extends BaseComponent {
    private static final int PAD = 6;
    private static final int LINE = SurfaceLook.LINE;
    private static final int FONT_H = SurfaceLook.FONT_H;
    private static final int MEDIA_GAP = 5;
    private static final int TEXT_GAP = 3;
    private static final int OPEN_MS = 110;
    private static final int SLIDE = 5;
    private static final int MIN_W = 40;
    private static final long NEVER = -1L;

    private final PanelComponent host;
    private final SurfaceAnchor anchor;
    private String title = "";
    private String subtitle = "";
    private String text = "";
    private IconArt media;
    private int cardW = 150;
    private int openDelay = 400;
    private int closeDelay = 150;
    private Runnable onOpen;
    private Runnable onClose;

    private Glide glide = new Glide(Slide.UP, OPEN_MS);
    private boolean open;
    private boolean shut;
    private boolean cursorByMouse;
    private boolean dirty = true;
    private List<String> lines = List.of();
    private int shownLines;
    private int cardH;
    private long overSince = NEVER;
    private long awaySince = NEVER;

    public HoverCardComponent(BaseComponent anchor, PanelComponent host) {
        this(Objects.requireNonNull(anchor, "anchor"), null, host);
    }

    /** @param anchor a place to rest the pointer on, in the panel's coordinates */
    public HoverCardComponent(Box anchor, PanelComponent host) {
        this(null, Objects.requireNonNull(anchor, "anchor"), host);
    }

    private HoverCardComponent(BaseComponent widget, Box box, PanelComponent host) {
        super(0, 0, 0, 0);
        this.host = Objects.requireNonNull(host, "host");
        this.anchor = new SurfaceAnchor(widget, box, host);
        glide.snap(false);
    }

    // ---- setup ----

    public HoverCardComponent title(String title) {
        this.title = title == null ? "" : title;
        dirty = true;
        return this;
    }

    public HoverCardComponent subtitle(String subtitle) {
        this.subtitle = subtitle == null ? "" : subtitle;
        dirty = true;
        return this;
    }

    public HoverCardComponent text(String text) {
        this.text = text == null ? "" : text;
        dirty = true;
        return this;
    }

    /** An item, a sprite, a head or a {@link Picture} (give it a {@code size}, as 64 by 36 for a screenshot), beside the title. */
    public HoverCardComponent media(IconArt media) {
        this.media = media;
        dirty = true;
        return this;
    }

    /** The card's width, 150 by default. It stays inside the area. */
    public HoverCardComponent width(int width) {
        cardW = Math.max(MIN_W, width);
        dirty = true;
        return this;
    }

    /** How long the pointer rests before it opens, and how long it may be away before it closes. 400 and 150 ms by default. */
    public HoverCardComponent delays(int openMs, int closeMs) {
        openDelay = Math.max(0, openMs);
        closeDelay = Math.max(0, closeMs);
        return this;
    }

    /** The side it would rather sit on. Below by default. */
    public HoverCardComponent side(SurfaceSide side) {
        anchor.side = side == null ? SurfaceSide.BOTTOM : side;
        return this;
    }

    /** How it lines up with the anchor along that side. Its start by default. */
    public HoverCardComponent align(SurfaceAlign align) {
        anchor.align = align == null ? SurfaceAlign.START : align;
        return this;
    }

    public HoverCardComponent gap(int gap) {
        anchor.gap = Math.max(0, gap);
        return this;
    }

    /** False for no small pointer towards the anchor. On by default. */
    public HoverCardComponent arrow(boolean arrow) {
        anchor.arrow = arrow;
        return this;
    }

    /** The box it stays in, in the panel's coordinates. The panel's bounds by default. */
    public HoverCardComponent area(Box area) {
        anchor.area = area;
        return this;
    }

    public HoverCardComponent onOpen(Runnable onOpen) {
        this.onOpen = onOpen;
        return this;
    }

    public HoverCardComponent onClose(Runnable onClose) {
        this.onClose = onClose;
        return this;
    }

    public HoverCardComponent active(boolean active) {
        this.active = active;
        return this;
    }

    // ---- state ----

    public boolean isOpen() {
        return open;
    }

    /** The card's place. */
    Box cardBox() {
        layout();
        return new Box("card", anchor.x, anchor.y, anchor.w, anchor.h);
    }

    public SurfaceSide placedSide() {
        layout();
        return anchor.placed;
    }

    public boolean showsArrow() {
        layout();
        return anchor.arrowAt >= 0;
    }

    int shownLines() {
        layout();
        return shownLines;
    }

    /** Closes at once, until the pointer next leaves the anchor. */
    public void close() {
        shut = true;
        overSince = NEVER;
        setOpen(false);
    }

    private void setOpen(boolean on) {
        if (on == open) {
            return;
        }
        open = on;
        awaySince = NEVER;
        if (on) {
            dirty = true;
            layout();
            glide = new Glide(anchor.slideFrom(), OPEN_MS);
            if (reducedMotion) {
                glide.snap(true);
            } else {
                glide.replay();
            }
            if (onOpen != null) {
                onOpen.run();
            }
        } else {
            if (reducedMotion) {
                glide.snap(false);
            } else {
                glide.hide();
            }
            if (onClose != null) {
                onClose.run();
            }
        }
    }

    private boolean drawn() {
        return open || glide.moving();
    }

    // ---- what it follows ----

    private boolean anchorRinged() {
        BaseComponent widget = anchor.widget();
        if (widget == null) {
            return false;
        }
        // A click puts a panel's cursor on a widget too: only a cursor that came by the keyboard counts.
        if (host.cursor() != widget) {
            cursorByMouse = false;
        }
        return widget.focused || (host.cursor() == widget && !cursorByMouse);
    }

    private void follow(int mx, int my) {
        long now = clock.getAsLong();
        boolean live = active && anchor.shown();
        boolean onAnchor = live && (anchor.contains(mx, my) || anchorRinged());
        boolean onCard = open && live && overCard(mx, my);
        boolean want = onAnchor || onCard;
        if (!onAnchor) {
            shut = false;
        }
        if (want && !shut) {
            awaySince = NEVER;
            if (overSince == NEVER) {
                overSince = now;
            }
            if (!open && now - overSince >= openDelay) {
                setOpen(true);
            }
            return;
        }
        overSince = NEVER;
        if (!open) {
            return;
        }
        if (!live || shut) {
            setOpen(false);
            return;
        }
        if (awaySince == NEVER) {
            awaySince = now;
        }
        if (now - awaySince >= closeDelay) {
            setOpen(false);
        }
    }

    private boolean overCard(double mx, double my) {
        return mx >= anchor.x && mx < anchor.x + anchor.w && my >= anchor.y && my < anchor.y + anchor.h;
    }

    // ---- layout ----

    private int mediaW() {
        return media == null ? 0 : media.width() + MEDIA_GAP;
    }

    private int headH() {
        int words = (title.isEmpty() ? 0 : 1) + (subtitle.isEmpty() ? 0 : 1);
        int wordsH = words == 0 ? 0 : words * LINE - 2;
        return Math.max(wordsH, media == null ? 0 : media.height());
    }

    private void layout() {
        if (dirty) {
            dirty = false;
            int inner = Math.max(1, Math.min(cardW, anchor.maxWidth()) - 2 * PAD);
            lines = text.isEmpty() ? List.of() : TextMeasure.wrapAll(text, inner);
            int head = headH();
            cardH = 2 * PAD + head + (head > 0 && !lines.isEmpty() ? TEXT_GAP : 0) + (lines.isEmpty() ? 0 : lines.size() * LINE - 2);
        }
        anchor.place(Math.min(cardW, anchor.maxWidth()), cardH);
        int head = headH();
        int room = anchor.h - 2 * PAD - head - (head > 0 ? TEXT_GAP : 0) + 2;
        shownLines = Math.min(lines.size(), Math.max(0, room / LINE));
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        follow(mx, my);
        if (!drawn()) {
            return;
        }
        layout();
        x = anchor.x;
        y = anchor.y;
        w = anchor.w;
        h = anchor.h;
        d.nextLayer();
        boolean moving = glide.moving();
        if (moving) {
            int pad = SurfaceAnchor.ARROW_H;
            d.scissor(x - pad, y - pad, x + w + pad, y + h + pad);
        }
        d.push(Math.round(glide.offsetX(SLIDE)), Math.round(glide.offsetY(SLIDE)), 1F);
        t.frame().popup(d, t.palette(), x, y, w, h);
        anchor.drawArrow(d, t);
        drawWords(d, t);
        d.pop();
        if (moving) {
            d.endScissor();
        }
    }

    private void drawWords(Draw d, Theme t) {
        Palette p = t.palette();
        int under = p.surface() | 0xFF000000;
        int inner = w - 2 * PAD;
        if (inner < 8) {
            return;
        }
        int ink = Ink.on(p, under);
        int soft = Ink.soft(p, under);
        int head = headH();
        int line = y + PAD;
        d.scissor(x, y, x + w, y + h);
        if (media != null) {
            if (media instanceof Picture picture) {
                // With the theme: the loading and failed looks, and a framed screenshot.
                picture.draw(d, t, x + PAD, line + (head - media.height()) / 2, media.width(), media.height());
            } else {
                media.draw(d, x + PAD, line + (head - media.height()) / 2, ink, 1F);
            }
        }
        int words = (title.isEmpty() ? 0 : 1) + (subtitle.isEmpty() ? 0 : 1);
        int room = inner - mediaW();
        if (words > 0 && room >= 8) {
            int top = line + (head - (words * LINE - 2)) / 2;
            if (!title.isEmpty()) {
                d.textFit(title, x + PAD + mediaW(), top, room, ink);
                top += LINE;
            }
            if (!subtitle.isEmpty()) {
                d.textFit(subtitle, x + PAD + mediaW(), top, room, soft);
            }
        }
        line += head + (head > 0 ? TEXT_GAP : 0);
        for (int i = 0; i < shownLines && line + FONT_H <= y + h; i++) {
            String row = lines.get(i);
            d.text(i == shownLines - 1 && shownLines < lines.size() ? d.ellipsize(row + Draw.ELLIPSIS, inner) : row, x + PAD, line, soft);
            line += LINE;
        }
        d.endScissor();
    }

    /** Empty, not null, while open: a panel goes on to the widgets beneath on null, and the anchor's tooltip would show too. */
    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!open) {
            return null;
        }
        return overCard(mx, my) || anchor.contains(mx, my) ? List.of() : null;
    }

    // ---- input ----

    @Override
    public boolean isOver(double mx, double my) {
        return open && overCard(mx, my);
    }

    @Override
    protected boolean navigable() {
        return false;
    }

    @Override
    public List<BaseComponent> stops() {
        return List.of();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        cursorByMouse = true;
        if (open && overCard(mx, my)) {
            return true;
        }
        if (anchor.contains(mx, my)) {
            close();
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        return open && overCard(mx, my);
    }
}
