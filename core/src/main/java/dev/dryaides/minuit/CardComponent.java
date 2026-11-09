package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;

/**
 * A block with an optional picture, a header, a wrapped text and a footer of buttons, for grids of things to
 * choose from. With {@link #onClick(Runnable)} the whole card is a button. It lifts a little under the pointer;
 * {@link #lift(boolean)} turns that off, and reduced motion keeps the lift but drops its easing.
 *
 * <pre>{@code
 * Box[] places = CardComponent.grid(at(new Box("maps", 10, 10, 280, 0)), 2, 6, 74, maps.size());
 * for (int i = 0; i < places.length; i++) {
 *     MapInfo map = maps.get(i);
 *     add(new CardComponent(places[i]).title(map.name()).subtitle(map.author())
 *             .text(map.about()).image(map.picture(), 28)
 *             .onClick(() -> open(map)).footerButton(words.get("mymod.play"), ButtonComponent.Role.PRIMARY, () -> play(map)));
 * }
 * }</pre>
 *
 * <p>{@link GridComponent} remains the better choice for many plain thumbnails with one selection: it is one
 * widget with its own keyboard. Use cards when each cell has words or buttons of its own. A footer button
 * takes its own Tab stop after the card's, and a click on it never counts as a click on the card. Text that
 * does not fit is cut with dots on a whole line, and a cut title shows in full as a tooltip.
 */
public class CardComponent extends BaseComponent {
    private static final int PAD = 6;
    private static final int GAP = 3;
    private static final int LINE = SurfaceLook.LINE;
    private static final int FONT_H = SurfaceLook.FONT_H;
    private static final int MEDIA_INSET = 3;
    private static final int ICON_GAP = 4;
    private static final int BUTTON_H = 16;
    private static final int BUTTON_MIN = 40;
    private static final int BUTTON_PAD = 12;
    private static final int BUTTON_GAP = 4;
    private static final int RULE_GAP = 3;
    private static final int LIFT = 2;
    private static final int LIFT_MS = 90;
    private static final int HELD_ALPHA = 0x20;
    private static final int MIN_ROOM = 12;

    private final List<ButtonComponent> buttons = new ArrayList<>();
    private final SurfaceLook.Tween lifted = new SurfaceLook.Tween(LIFT_MS, 0F);
    private final RowLook.HoverFade hoverFade = new RowLook.HoverFade();

    private String title = "";
    private String subtitle = "";
    private String text = "";
    private String footerText = "";
    private IconArt icon;
    private IconArt mediaArt;
    private Picture picture;
    private ItemView hoveredItem;
    private int mediaH;
    private Runnable onClick;
    private boolean lift = true;
    private boolean selected;

    private List<String> lines = List.of();
    private String laidText;
    private int laidRoom = -1;
    private boolean holding;
    private boolean titleCut;
    private int liftNow;
    private int lastButtonsW = -1;

    public CardComponent(Box box) {
        super(box);
    }

    // ---- setup ----

    public CardComponent title(String title) {
        this.title = title == null ? "" : title;
        return this;
    }

    /** A quieter line under the title. */
    public CardComponent subtitle(String subtitle) {
        this.subtitle = subtitle == null ? "" : subtitle;
        return this;
    }

    /** The wrapped body. */
    public CardComponent text(String text) {
        this.text = text == null ? "" : text;
        return this;
    }

    /** A small icon before the title: an item, a sprite or a theme's icon. */
    public CardComponent icon(IconArt icon) {
        this.icon = icon;
        return this;
    }

    /**
     * A picture across the top, this tall, cropped to fill it around its middle ({@link Picture.Fit#COVER}).
     * It shows the loading and failed looks. {@link #picture(Picture, int)} chooses the fit and focal point.
     */
    public CardComponent image(AsyncImage image, int height) {
        return picture(image == null ? null : Picture.of(image).fit(Picture.Fit.COVER), height);
    }

    /**
     * An item or icon, centred on a well across the top, this tall. An item, a sprite or a {@link Picture}
     * grows by whole pixels to fill the well, so a sword stays crisp; a theme icon keeps its size.
     */
    public CardComponent media(IconArt art, int height) {
        if (art instanceof ItemView || art instanceof SpriteIcon || art instanceof Picture) {
            return picture(Picture.of(art), height);
        }
        this.mediaArt = art;
        this.picture = null;
        this.mediaH = Math.max(0, height);
        return this;
    }

    /**
     * Any picture across the top, this tall: a screenshot (cover it with {@code fit(Fit.COVER)}), an item,
     * a texture. Images fill the media box, items and sprites sit on a well, scaled by whole pixels.
     */
    public CardComponent picture(Picture picture, int height) {
        this.picture = picture;
        this.mediaArt = null;
        this.mediaH = Math.max(0, height);
        return this;
    }

    /** The picture across the top, or null. */
    public Picture picture() {
        return picture;
    }

    /** Words at the start of the footer: a price, a date. */
    public CardComponent footerText(String footerText) {
        this.footerText = footerText == null ? "" : footerText;
        return this;
    }

    /** Adds a button to the footer, right aligned, left to right. */
    public CardComponent footerButton(String label, ButtonComponent.Role role, Runnable onPress) {
        ButtonComponent button = new ButtonComponent(new Box("card-action", x, y, BUTTON_MIN, BUTTON_H), label, onPress);
        button.role(role == null ? ButtonComponent.Role.SECONDARY : role);
        buttons.add(button);
        lastButtonsW = -1;
        return this;
    }

    /** Makes the whole card a button: a click, Enter or Space runs this. */
    public CardComponent onClick(Runnable onClick) {
        this.onClick = onClick;
        return this;
    }

    /** False to keep the card still under the pointer. On by default. */
    public CardComponent lift(boolean lift) {
        this.lift = lift;
        return this;
    }

    /** Marks the card as the chosen one, with a ring in the primary colour. */
    public CardComponent selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public CardComponent active(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public CardComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    /** Sets the height to what the content needs at the current width, and returns this. */
    public CardComponent fit() {
        h = preferredHeight();
        return this;
    }

    /** What the content needs at the current width. */
    public int preferredHeight() {
        wrap(textRoom());
        int top = contentTop(0) + headerH();
        if (!lines.isEmpty()) {
            top += (headerH() > 0 ? GAP : 0) + lines.size() * LINE - 2;
        }
        return top + footerH() + PAD;
    }

    /**
     * Places for cards in rows.
     *
     * @param area left, top and width of the grid; its height is ignored
     * @param count how many places
     */
    public static Box[] grid(Box area, int columns, int gap, int cardH, int count) {
        int cols = Math.max(1, columns);
        int cellW = Math.max(1, (area.w() - gap * (cols - 1)) / cols);
        Box[] out = new Box[Math.max(0, count)];
        for (int i = 0; i < out.length; i++) {
            out[i] = new Box(area.name() + i, area.x() + (i % cols) * (cellW + gap), area.y() + (i / cols) * (cardH + gap), cellW, cardH);
        }
        return out;
    }

    // ---- state ----

    public boolean selected() {
        return selected;
    }

    /** How far the card is lifted now, in pixels. */
    int liftNow() {
        return liftNow;
    }

    /** The footer's buttons, to read or to turn off one at a time. */
    public List<ButtonComponent> buttons() {
        return List.copyOf(buttons);
    }

    /** The picture's place. */
    Box mediaBox() {
        return new Box("media", x + MEDIA_INSET, y - liftNow + MEDIA_INSET, Math.max(0, w - 2 * MEDIA_INSET), mediaH);
    }

    int lineCount() {
        wrap(textRoom());
        return lines.size();
    }

    // ---- layout ----

    private boolean hasMedia() {
        return mediaH > 0 && (picture != null || mediaArt != null) && w > 2 * MEDIA_INSET;
    }

    private int contentTop(int cardY) {
        return hasMedia() ? cardY + MEDIA_INSET + mediaH + GAP + 1 : cardY + PAD;
    }

    private int headerH() {
        int lineCount = (title.isEmpty() ? 0 : 1) + (subtitle.isEmpty() ? 0 : 1);
        int textH = lineCount == 0 ? 0 : lineCount * LINE - 2;
        return icon == null ? textH : Math.max(textH, icon.height());
    }

    private int footerH() {
        if (!buttons.isEmpty()) {
            return RULE_GAP + 1 + RULE_GAP + BUTTON_H;
        }
        return footerText.isEmpty() ? 0 : RULE_GAP + 1 + RULE_GAP + FONT_H;
    }

    private int innerW() {
        return Math.max(0, w - 2 * PAD);
    }

    private int textRoom() {
        return innerW();
    }

    private void wrap(int room) {
        if (room == laidRoom && text.equals(laidText)) {
            return;
        }
        laidRoom = room;
        laidText = text;
        lines = text.isEmpty() || room <= 0 ? List.of() : TextMeasure.wrapAll(text, room);
    }

    /** The buttons follow the card, lift included, so that their hover, clicks and rings all stay true. */
    private void placeButtons() {
        int count = buttons.size();
        if (count == 0) {
            return;
        }
        int inner = innerW();
        if (lastButtonsW != inner) {
            lastButtonsW = inner;
            int total = BUTTON_GAP * (count - 1);
            for (int i = 0; i < count; i++) {
                ButtonComponent button = buttons.get(i);
                button.w = Math.max(BUTTON_MIN, TextMeasure.width(button.label) + BUTTON_PAD);
                total += button.w;
            }
            if (total > inner) {
                int share = Math.max(1, (inner - BUTTON_GAP * (count - 1)) / count);
                for (int i = 0; i < count; i++) {
                    buttons.get(i).w = share;
                }
            }
        }
        int right = x + w - PAD;
        int by = y - liftNow + h - PAD - BUTTON_H;
        for (int i = count - 1; i >= 0; i--) {
            ButtonComponent button = buttons.get(i);
            right -= button.w;
            button.x = right;
            button.y = by;
            right -= BUTTON_GAP;
            button.active = active;
            button.visible = h >= footerH() + 2 * PAD;
        }
    }

    private boolean overFooterButton(double mx, double my) {
        for (int i = 0; i < buttons.size(); i++) {
            ButtonComponent button = buttons.get(i);
            if (button.visible && button.isOver(mx, my)) {
                return true;
            }
        }
        return false;
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        if (w <= 0 || h <= 0) {
            return;
        }
        Palette p = t.palette();
        FrameStyle frame = t.frame();
        boolean over = active && isOver(mx, my);
        boolean pressed = holding && over;
        lifted.go(lift && active && over && !pressed ? LIFT : 0F);
        liftNow = Math.round(lifted.value());
        placeButtons();
        int cy = y - liftNow;

        if (liftNow > 0) {
            d.nextLayer(); // the shadow may fall on a neighbour's words
            frame.dropShadow(d, p, x, y, w, h, liftNow);
        }
        // The panel's look, not the section's: in bedrock a section is one rule, which does not read as a block.
        frame.panel(d, p, x, cy, w, h);
        hoverFade.step(over && onClick != null ? 0 : -1);
        if (active && onClick != null && over) {
            int wash = pressed ? Shapes.alpha(p.text(), HELD_ALPHA) : RowLook.tint(p, 0.5F * hoverFade.fade());
            frame.plate(d, p, x + 1, cy + 1, w - 2, h - 2, wash, 0);
        }
        d.scissor(x, cy, x + w, cy + h);
        drawMedia(d, t, p, cy);
        hoveredItem = null;
        ItemView item = picture != null ? picture.item() : null;
        if (item != null && active && hasMedia() && mediaBox().contains(mx, my) && !overFooterButton(mx, my)) {
            hoveredItem = item;
        }
        drawWords(d, t, p, cy);
        d.endScissor();
        if (selected) {
            int color = active ? p.primary() : p.disabledText();
            frame.panelEdge(d, p, x, cy, w, h, 2, color);
        }
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).draw(d, t, mx, my, delta);
        }
        if (hoveredItem != null && tooltip == null) {
            d.itemTooltip(hoveredItem, mx, my); // the game's own item tooltip: the item is the content here
        }
    }

    private void drawMedia(Draw d, Theme t, Palette p, int cy) {
        if (!hasMedia()) {
            return;
        }
        int mx0 = x + MEDIA_INSET;
        int my0 = cy + MEDIA_INSET;
        int mw = w - 2 * MEDIA_INSET;
        int shownH = Math.min(mediaH, h - 2 * MEDIA_INSET);
        if (shownH <= 0) {
            return;
        }
        if (picture != null && picture.art() != null) {
            // An item or sprite: on the well, grown by whole pixels, 2 units clear of its edge.
            PictureLook.flat(d, t, mx0, my0, mw, shownH, false);
            picture.draw(d, t, mx0 + 2, my0 + 2, mw - 4, shownH - 4, true); // the veil below covers it when disabled
        } else if (picture != null) {
            picture.draw(d, t, mx0, my0, mw, shownH, true);
            if (PictureLook.canRound(t)) {
                Picture.corners(d, mx0, my0, mw, shownH, Math.max(0, t.frame().corner() - 1), p.surface() | 0xFF000000);
            }
        } else {
            PictureLook.flat(d, t, mx0, my0, mw, shownH, false);
            mediaArt.draw(d, mx0 + (mw - mediaArt.width()) / 2, my0 + (shownH - mediaArt.height()) / 2, active ? p.text() : p.disabledText(), 1F);
        }
        if (!active) {
            d.fill(mx0, my0, mx0 + mw, my0 + shownH, Shapes.alpha(p.surface() | 0xFF000000, 0x90));
        }
    }

    private void drawWords(Draw d, Theme t, Palette p, int cy) {
        int inner = innerW();
        titleCut = false;
        if (inner < MIN_ROOM) {
            return;
        }
        int under = sectionSolid(p);
        int ink = active ? Ink.on(p, under) : p.disabledText();
        int soft = active ? Ink.soft(p, under) : p.disabledText();
        int left = x + PAD;
        int line = contentTop(cy);
        int bottom = cy + h - PAD - footerH();
        int headH = headerH();
        if (!hasMedia() && line + Math.min(headH, FONT_H) > cy + h - 1 && h >= FONT_H + 2) {
            line = cy + (h - FONT_H) / 2; // a box of one line: the title centred, not lost
        }
        if (headH > 0 && line + Math.min(headH, FONT_H) <= cy + h - 1) {
            int textLeft = left;
            int room = inner;
            if (icon != null) {
                icon.draw(d, left, line + (headH - icon.height()) / 2, ink, 1F);
                textLeft += icon.width() + ICON_GAP;
                room -= icon.width() + ICON_GAP;
            }
            int textTop = line + (headH - ((title.isEmpty() ? 0 : 1) + (subtitle.isEmpty() ? 0 : 1)) * LINE + 2) / 2;
            if (room >= MIN_ROOM) {
                if (!title.isEmpty()) {
                    titleCut = d.width(title) > room;
                    d.textFit(title, textLeft, textTop, room, ink);
                    textTop += LINE;
                }
                if (!subtitle.isEmpty() && textTop + FONT_H <= cy + h - 1) {
                    d.textFit(subtitle, textLeft, textTop, room, soft);
                }
            }
            line += headH + GAP;
        }
        wrap(textRoom());
        int fit = Math.max(0, (bottom - line + 2) / LINE);
        int count = Math.min(fit, lines.size());
        for (int i = 0; i < count; i++) {
            String row = lines.get(i);
            if (i == count - 1 && count < lines.size()) {
                row = d.ellipsize(row + Draw.ELLIPSIS, inner);
            }
            d.text(row, left, line, soft);
            line += LINE;
        }
        drawFooter(d, p, cy, soft);
    }

    private void drawFooter(Draw d, Palette p, int cy, int soft) {
        int footer = footerH();
        if (footer == 0 || h < footer + 2 * PAD) {
            return;
        }
        int ruleY = cy + h - PAD - footer + RULE_GAP - 1;
        d.fill(x + PAD, ruleY, x + w - PAD, ruleY + 1, p.surfaceEdge());
        if (!footerText.isEmpty()) {
            int room = innerW();
            int barH = footer - RULE_GAP - 1 - RULE_GAP;
            if (!buttons.isEmpty()) {
                room = buttons.get(0).x - BUTTON_GAP - (x + PAD);
            }
            if (room >= MIN_ROOM) {
                d.textFit(footerText, x + PAD, cy + h - PAD - barH + (barH - FONT_H) / 2 + 1, room, soft);
            }
        }
    }

    private static int sectionSolid(Palette p) {
        return p.surface() | 0xFF000000;
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!isOver(mx, my)) {
            return null;
        }
        for (int i = 0; i < buttons.size(); i++) {
            ButtonComponent button = buttons.get(i);
            if (button.visible && button.isOver(mx, my)) {
                return button.tooltipAt(mx, my);
            }
        }
        if (tooltip != null) {
            return tooltip;
        }
        if (hoveredItem != null) {
            return List.of(); // the item's own tooltip is drawn instead
        }
        return titleCut ? List.of(title) : null;
    }

    // ---- input ----

    /** A lifted card reaches higher: without this, the pointer on its top edge would drop it and lift it again. */
    @Override
    public boolean isOver(double mx, double my) {
        return visible && mx >= x && mx < x + w && my >= y - liftNow && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible || !active) {
            return false;
        }
        placeButtons();
        for (int i = 0; i < buttons.size(); i++) {
            ButtonComponent part = buttons.get(i);
            if (part.visible && part.isOver(mx, my) && part.mouseClicked(mx, my, button)) {
                return true;
            }
        }
        if (button == 0 && onClick != null && isOver(mx, my)) {
            holding = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean used = false;
        for (int i = 0; i < buttons.size(); i++) {
            used |= buttons.get(i).mouseReleased(mx, my, button);
        }
        boolean was = holding;
        holding = false;
        if (was && visible && active && isOver(mx, my) && !overFooterButton(mx, my)) {
            BaseComponent.click();
            onClick.run();
        }
        return used || was;
    }

    @Override
    public void tick() {
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).tick();
        }
    }

    @Override
    protected boolean navigable() {
        return visible && active && onClick != null;
    }

    @Override
    protected boolean activate() {
        if (!navigable()) {
            return false;
        }
        BaseComponent.click();
        onClick.run();
        return true;
    }

    @Override
    public List<BaseComponent> stops() {
        if (buttons.isEmpty()) {
            return super.stops();
        }
        placeButtons();
        List<BaseComponent> out = new ArrayList<>(buttons.size() + 1);
        if (navigable()) {
            out.add(this);
        }
        for (int i = 0; i < buttons.size(); i++) {
            out.addAll(buttons.get(i).stops());
        }
        return out;
    }

    @Override
    protected Box focusBox() {
        return new Box("card", x, y - liftNow, w, h);
    }

    @Override
    protected int focusCorner(FrameStyle frame) {
        return frame.corner();
    }
}
