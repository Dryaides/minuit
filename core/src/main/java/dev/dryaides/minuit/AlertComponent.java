package dev.dryaides.minuit;

import java.util.List;

/**
 * An inline banner: a short message that belongs to the screen, unlike a toast, which floats over the game
 * and goes away. Four variants, each with its own colour and its own shape of icon, so that they differ
 * without relying on the colour alone.
 *
 * <pre>{@code
 * add(new AlertComponent(at(new Box("saved", 10, 10, 200, 0)), AlertComponent.Variant.SUCCESS,
 *         words.get("mymod.saved.title"), words.get("mymod.saved.body"))
 *         .dismissible(true)
 *         .onDismiss(this::layout)
 *         .fit());
 * }</pre>
 *
 * <p>{@link #fit()} gives the banner the height its text needs at its width. A box too short for the text
 * cuts it with dots on the last whole line, and the full text shows as a tooltip. With a close button, Tab
 * stops on it and Enter, Space or Delete dismisses. After the dismissal {@code visible} is false; call
 * {@link #show()} to bring the banner back. {@code active = false} fades it and turns the button off.
 */
public class AlertComponent extends BaseComponent {
    /** What the banner says about. */
    public enum Variant { INFO, SUCCESS, WARNING, ERROR }

    private static final int PAD = 5;
    private static final int ICON = 9;
    private static final int ITEM_ICON = 16;
    private static final int ICON_GAP = 5;
    private static final int CLOSE = 11;
    private static final int CLOSE_GAP = 2;
    private static final int MIN_TEXT = 16;
    private static final int LINE = SurfaceLook.LINE;
    private static final int FONT_H = SurfaceLook.FONT_H;
    private static final int WASH = 0x30;
    private static final int CLOSE_HOVER = 0x30;
    private static final int CLOSE_HELD = 0x60;
    private static final int LEAVE_MS = 130;
    private static final float DISABLED_FADE = 0.55F;
    private static final float SAME_HUE = 35F;

    // Each row is 9 wide. # the colour, + the ink on it. Small enough to draw by runs, and no family has an "i".
    private static final String[] ART_INFO = {
        " ####### ", "#########", "####+####", "#########", "####+####", "####+####", "####+####", "#########", " ####### "};
    private static final String[] ART_SUCCESS = {
        " ####### ", "#########", "#######+#", "######+##", "#+###+###", "##+#+####", "###+#####", "#########", " ####### "};
    private static final String[] ART_WARNING = {
        "    #    ", "   ###   ", "   #+#   ", "  ##+##  ", "  ##+##  ", " ###+### ", " ####### ", "####+####", "#########"};
    private static final String[] ART_ERROR = {
        "  #####  ", " ####### ", "##+###+##", "###+#+###", "####+####", "###+#+###", "##+###+##", " ####### ", "  #####  "};

    private Variant variant;
    private String title;
    private String message;
    private IconArt icon;
    private boolean dismissible;
    private Runnable onDismiss;

    private List<String> lines = List.of();
    private String laidMessage;
    private int laidRoom = -1;
    private boolean holding;
    private boolean leaving;
    private final SurfaceLook.Tween leave = new SurfaceLook.Tween(LEAVE_MS, 1F);
    private boolean cut;

    /**
     * @param box the banner; a height of 0 is fine with {@link #fit()}
     * @param title the bold line; may be null or empty
     * @param message the wrapped text under it; may be null or empty
     */
    public AlertComponent(Box box, Variant variant, String title, String message) {
        super(box);
        this.variant = variant == null ? Variant.INFO : variant;
        this.title = title == null ? "" : title;
        this.message = message == null ? "" : message;
    }

    // ---- setup ----

    /** Shows a close button, and lets Tab stop on it. Off by default. */
    public AlertComponent dismissible(boolean dismissible) {
        this.dismissible = dismissible;
        return this;
    }

    /** Runs when the player dismisses the banner, at the start of its fold. Not through {@link #dismiss()}. */
    public AlertComponent onDismiss(Runnable onDismiss) {
        this.onDismiss = onDismiss;
        return this;
    }

    /** Replaces the variant's icon. Drawn in the variant's colour, 9 by 9 if it can be (larger art shrinks crisply); a Minecraft item ({@link ItemView}) keeps 16 by 16. */
    public AlertComponent icon(IconArt icon) {
        this.icon = icon;
        return this;
    }

    public AlertComponent active(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public AlertComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    /** Sets the height to what the text needs at the current width, and returns this. */
    public AlertComponent fit() {
        h = preferredHeight();
        return this;
    }

    /** What the text needs at the current width. */
    public int preferredHeight() {
        h = Math.max(h, iconBox() + 2);
        wrap(textRoom());
        int textH = (title.isEmpty() ? 0 : LINE) + lines.size() * LINE;
        return Math.max(iconBox(), textH - 2) + 2 * PAD;
    }

    // ---- content ----

    public Variant variant() {
        return variant;
    }

    public AlertComponent variant(Variant variant) {
        this.variant = variant == null ? Variant.INFO : variant;
        return this;
    }

    public String title() {
        return title;
    }

    public AlertComponent title(String title) {
        this.title = title == null ? "" : title;
        return this;
    }

    public String message() {
        return message;
    }

    public AlertComponent message(String message) {
        this.message = message == null ? "" : message;
        return this;
    }

    /** True when the last frame had to cut the text. */
    public boolean textCut() {
        return cut;
    }

    /** How many lines the message wraps to at this width. */
    int lineCount() {
        wrap(textRoom());
        return lines.size();
    }

    public boolean isDismissible() {
        return dismissible;
    }

    /** True while the banner folds away. */
    public boolean leaving() {
        return leaving;
    }

    /** Folds the banner away, without calling back. */
    public void dismiss() {
        if (!visible || leaving) {
            return;
        }
        holding = false;
        leaving = true;
        leave.go(0F);
        settle();
    }

    /** Brings a dismissed banner back. */
    public void show() {
        leaving = false;
        leave.snap(1F);
        visible = true;
    }

    /** Where the close button is, for a script or a test. */
    Box closeBox() {
        return new Box("close", closeX(), closeY(), CLOSE, CLOSE);
    }

    private void settle() {
        if (leaving && !leave.moving()) {
            leaving = false;
            visible = false;
        }
    }

    // ---- geometry ----

    private boolean showsClose() {
        return dismissible && w >= CLOSE + 4 && h >= CLOSE + 2;
    }

    private int closeX() {
        return x + w - CLOSE - Math.min(CLOSE_GAP + 1, (w - CLOSE) / 2);
    }

    private int closeY() {
        return y + (h >= CLOSE + 2 * (CLOSE_GAP + 1) ? CLOSE_GAP + 1 : (h - CLOSE) / 2);
    }

    private boolean overClose(double mx, double my) {
        int cx = closeX();
        int cy = closeY();
        return showsClose() && mx >= cx && mx < cx + CLOSE && my >= cy && my < cy + CLOSE;
    }

    private boolean showsIcon() {
        return w - 2 * PAD - iconBox() - ICON_GAP >= MIN_TEXT && h >= iconBox() + 2;
    }

    /** The icon's square: 16 for a Minecraft item, else the variant icon's 9. */
    private int iconBox() {
        return icon instanceof ItemView ? ITEM_ICON : ICON;
    }

    private int textLeft() {
        return x + PAD + (showsIcon() ? iconBox() + ICON_GAP : 0);
    }

    private int textRoom() {
        int right = x + w - PAD - (dismissible ? CLOSE - 1 : 0);
        return Math.max(0, right - textLeft());
    }

    private void wrap(int room) {
        if (room == laidRoom && message.equals(laidMessage)) {
            return;
        }
        laidRoom = room;
        laidMessage = message;
        lines = message.isEmpty() || room <= 0 ? List.of() : TextMeasure.wrapAll(message, room);
    }

    // ---- drawing ----

    private int colorOf(Palette p) {
        return switch (variant) {
            case INFO -> infoColor(p);
            case SUCCESS -> p.good();
            case WARNING -> p.warn();
            default -> p.bad();
        };
    }

    /** The accent, unless it is close to the good colour (bedrock, vanilla): info must not read as success. */
    private static int infoColor(Palette p) {
        float apart = Math.abs(hue(p.accent()) - hue(p.good()));
        return Math.min(apart, 360F - apart) < SAME_HUE ? p.textSoft() : p.accent();
    }

    private static float hue(int color) {
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        int range = max - Math.min(r, Math.min(g, b));
        if (range == 0) {
            return 0F;
        }
        float h = max == r ? (g - b) / (float) range : max == g ? 2F + (b - r) / (float) range : 4F + (r - g) / (float) range;
        return (h * 60F + 360F) % 360F;
    }

    private String[] art() {
        return switch (variant) {
            case INFO -> ART_INFO;
            case SUCCESS -> ART_SUCCESS;
            case WARNING -> ART_WARNING;
            default -> ART_ERROR;
        };
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        settle();
        if (!visible || w <= 0 || h <= 0) {
            return;
        }
        Palette p = t.palette();
        float shown = leave.value();
        boolean folding = shown < 1F;
        if (folding) {
            d.scissor(x, y, x + w, y + Math.round(h * shown));
        }
        int surface = p.surface() | 0xFF000000;
        int color = colorOf(p) | 0xFF000000;
        if (!active) {
            color = Shapes.mix(color, surface, DISABLED_FADE);
        }
        int under = Shapes.mix(surface, color, WASH / 255F);
        int edge = active ? Ink.readable(color, under, Ink.MARK) : color;
        t.frame().plate(d, p, x, y, w, h, Shapes.alpha(color, WASH), edge);
        wrap(textRoom());
        if (showsIcon()) {
            drawIcon(d, p, edge, under);
        }
        drawText(d, p, under);
        if (showsClose() && active) {
            drawClose(d, t, p, under, mx, my);
        }
        if (folding) {
            d.endScissor();
        }
    }

    private void drawIcon(Draw d, Palette p, int color, int under) {
        int ix = x + PAD;
        int box = iconBox();
        int iy = y + (hasTwoLines() && h >= box + 2 * PAD ? PAD : (h - box) / 2);
        if (icon != null) {
            // An item keeps its 16 px box; other art over 9 shrinks crisply (n/gs). Veiled when off. No item tooltip.
            int artW = ItemArt.rowWidth(icon, box);
            ItemArt.inRow(d, p, icon, ix + (box - artW) / 2, iy, box, box, color, active);
            return;
        }
        int ink = active ? Ink.on(p, color) : under;
        String[] rows = art();
        for (int row = 0; row < rows.length; row++) {
            String cells = rows[row];
            int from = 0;
            while (from < cells.length()) {
                char c = cells.charAt(from);
                int to = from + 1;
                while (to < cells.length() && cells.charAt(to) == c) {
                    to++;
                }
                if (c != ' ') {
                    d.fill(ix + from, iy + row, ix + to, iy + row + 1, c == '#' ? color : ink);
                }
                from = to;
            }
        }
    }

    private boolean hasTwoLines() {
        return (title.isEmpty() ? 0 : 1) + lines.size() > 1;
    }

    private void drawText(Draw d, Palette p, int under) {
        int room = textRoom();
        cut = false;
        if (room < MIN_TEXT || h < FONT_H + 2) {
            cut = !title.isEmpty() || !message.isEmpty();
            return;
        }
        int left = textLeft();
        int titleH = title.isEmpty() ? 0 : LINE;
        // Whole lines only, with at least one pixel to spare at the top and the bottom.
        int fit = Math.max(0, (h - 2 + 2 - titleH) / LINE);
        int count = Math.min(fit, lines.size());
        int textH = titleH + count * LINE - 2;
        int top = !hasTwoLines() || textH > h - 2 * PAD ? y + (h - textH) / 2 : y + PAD;
        int ink = active ? Ink.on(p, under) : p.disabledText();
        int soft = active ? Ink.soft(p, under) : p.disabledText();
        int line = top;
        if (titleH > 0) {
            cut |= d.width(title) > room;
            d.textFit(title, left, line, room, ink);
            line += LINE;
        }
        for (int i = 0; i < count; i++) {
            String text = lines.get(i);
            if (i == count - 1 && count < lines.size()) {
                text = d.ellipsize(text + Draw.ELLIPSIS, room);
            }
            d.text(text, left, line, soft);
            line += LINE;
        }
        cut |= count < lines.size();
    }

    private void drawClose(Draw d, Theme t, Palette p, int under, int mx, int my) {
        int cx = closeX();
        int cy = closeY();
        boolean over = overClose(mx, my);
        int ink = Ink.on(p, under);
        if (over) {
            d.fill(cx, cy, cx + CLOSE, cy + CLOSE, Shapes.alpha(ink, holding ? CLOSE_HELD : CLOSE_HOVER));
        }
        IconArt art = t.icon(Icon.CLOSE);
        d.scissor(cx, cy, cx + CLOSE, cy + CLOSE);
        art.draw(d, cx + (CLOSE - art.width()) / 2, cy + (CLOSE - art.height()) / 2, over ? ink : Ink.soft(p, under), 1F);
        d.endScissor();
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!visible || leaving || !super.isOver(mx, my) || overClose(mx, my)) {
            return null;
        }
        if (tooltip != null) {
            return tooltip;
        }
        if (!cut) {
            return null;
        }
        return title.isEmpty() ? List.of(message) : message.isEmpty() ? List.of(title) : List.of(title, message);
    }

    // ---- input ----

    @Override
    public void tick() {
        settle();
    }

    private boolean live() {
        return visible && active && !leaving;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (live() && button == 0 && overClose(mx, my)) {
            holding = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean was = holding;
        holding = false;
        if (was && live() && overClose(mx, my)) {
            playerDismiss();
        }
        return was;
    }

    private void playerDismiss() {
        BaseComponent.click();
        dismiss();
        if (onDismiss != null) {
            onDismiss.run();
        }
    }

    @Override
    protected boolean navigable() {
        return live() && showsClose();
    }

    @Override
    protected boolean activate() {
        if (!navigable()) {
            return false;
        }
        playerDismiss();
        return true;
    }

    @Override
    protected Box focusBox() {
        return showsClose() ? new Box("close", closeX() + 1, closeY() + 1, CLOSE - 2, CLOSE - 2) : super.focusBox();
    }

    @Override
    protected int focusCorner() {
        return 0;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        return key == InputKey.DELETE && activate();
    }
}
