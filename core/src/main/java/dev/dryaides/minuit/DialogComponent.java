package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A modal dialog: dims everything beneath it and takes all input until it closes. It has a title, a
 * wrapped message that scrolls when it does not fit, an optional custom child, and 1 to 3 actions
 * drawn as {@link ButtonComponent}s. Enter runs the default action, Escape cancels, Tab cycles only
 * inside the dialog, and a click on the dim can cancel too ({@link #closeOnBackdrop(boolean)}).
 *
 * <pre>{@code
 * public class WorldsPanel extends PanelComponent {
 *     private final DialogComponent confirm;
 *
 *     public WorldsPanel(Theme theme, Words words, int left, int top) {
 *         super(theme, words, left, top);
 *         add(new ButtonComponent(at(new Box("delete", 10, 10, 90, 20)), words.get("mymod.delete"),
 *                 () -> confirm.open(this)));
 *         // Last, so that it is drawn on top and offered every input first.
 *         confirm = add(DialogComponent.confirmDestructive(bounds(),
 *                 words.get("mymod.delete.title"), words.get("mymod.delete.body"),
 *                 words.get("mymod.delete"), words.get("gui.cancel"), this::delete, null));
 *     }
 * }
 * }</pre>
 *
 * <h2>Putting it in a panel</h2>
 * Add the dialog as a normal widget, <b>after every other widget</b>, and open it with
 * {@link #open(PanelComponent)}. {@code open} throws if another kind of widget comes after it. The
 * box is the area to dim and centre in, in the same coordinates as the panel's widgets; pass the
 * screen's box to dim more than the panel. A panel asks its last widget first for clicks and wheel
 * turns and gives every key and typed character to its focused widget, so the open dialog sees them
 * all, and it swallows them.
 *
 * <p>Do <b>not</b> show it through the panel's {@code overlay} field, the way a dropdown shows its list.
 * An overlay gets no ticks, wheel turns, releases or drags, and the panel closes it on Escape without
 * telling it, so no cancel callback could run. For a {@link DropdownComponent} inside the dialog's
 * content, pass {@link #showOverlay(BaseComponent)} as its overlay consumer.
 *
 * <h2>What the public API leaves open</h2>
 * <ul>
 * <li>Widgets beneath still show their hover under the dim, because a panel gives them the real
 * pointer position. They take no click, key or tooltip.</li>
 * <li>A panel's {@code drawForeground} and its own overlay draw above the dialog, and
 * {@link #open(PanelComponent)} closes that overlay for this reason.</li>
 * <li>A container screen draws its slot items over the panel's widgets, so they show above the
 * dialog there.</li>
 * <li>The screen's theme tab is not part of the panel: override {@code showsThemeTab()} to return
 * {@code !dialog.isOpen()}.</li>
 * </ul>
 *
 * <p>{@code active = false} keeps it open and blocking with its actions off, for a busy state: only
 * the wheel still works. {@code visible = false} hides it and lets input through without closing it.
 * A custom child must place its own parts from its {@code x} and {@code y}, as the dialog moves it
 * when it lays out; give it the size it wants before {@link #content(BaseComponent)}. A dropdown's
 * popup x is fixed when the dropdown is made, so work it out from the card's width and the area.
 */
public final class DialogComponent extends BaseComponent {
    private static final int MAX_ACTIONS = 3;
    private static final int OPEN_MS = 170;
    /** Opacity of the dim, over the palette's shadow colour. */
    private static final int DIM_ALPHA = 0xA0;
    private static final int PAD = 10;
    private static final int GAP = 6;
    private static final int LINE = 10;
    private static final int TITLE_LINES = 2;
    private static final int BUTTON_H = 20;
    private static final int BUTTON_MIN = 56;
    private static final int BUTTON_PAD = 16;
    private static final int MARGIN = 8;
    private static final int SCROLL_ROOM = ScrollComponent.BAR + 3;
    private static final int OFF_POINTER = -100_000;

    private final List<Action> actions = new ArrayList<>();
    private Glide slide = new Glide(Slide.DOWN, OPEN_MS);
    private String title = "";
    private String message = "";
    private BaseComponent content;
    private Runnable onCancel;
    private int preferredWidth = 220;
    private int maxWidth = Integer.MAX_VALUE;
    private int maxHeight = Integer.MAX_VALUE;
    private int defaultAction = -1;
    private int cancelAction = -1;
    private boolean cancellable = true;
    private boolean closeOnBackdrop;

    private PanelComponent host;
    private BaseComponent focusBefore;
    private Body body;
    private ScrollComponent scroll;
    private List<ActionButton> buttons = List.of();
    private List<String> titleLines = List.of();
    private Box card = new Box("card", 0, 0, 0, 0);
    private boolean open;
    private boolean dirty = true;
    private int laidX;
    private int laidY;
    private int laidW;
    private int laidH;

    /** @param area what to dim and centre in; {@code PanelComponent.bounds()} or the screen's box */
    public DialogComponent(Box area) {
        super(area);
        visible = false;
        slide.snap(false);
    }

    // ---- helpers ----

    /** A question with a primary confirm, which is the default, and a cancel that Escape also runs. */
    public static DialogComponent confirm(Box area, String title, String message, String confirmLabel,
            String cancelLabel, Runnable onConfirm, Runnable onCancel) {
        return new DialogComponent(area).title(title).message(message)
                .action(cancelLabel, ButtonComponent.Role.SECONDARY, onCancel)
                .action(confirmLabel, ButtonComponent.Role.PRIMARY, onConfirm)
                .cancelAction(0).defaultAction(1);
    }

    /**
     * A question with a confirm that destroys something. Cancel is the default here, so a stray Enter
     * loses nothing; the player reaches confirm with the mouse or Tab, or call {@link #defaultAction(int)}.
     */
    public static DialogComponent confirmDestructive(Box area, String title, String message, String confirmLabel,
            String cancelLabel, Runnable onConfirm, Runnable onCancel) {
        return new DialogComponent(area).title(title).message(message)
                .action(cancelLabel, ButtonComponent.Role.SECONDARY, onCancel)
                .action(confirmLabel, ButtonComponent.Role.DESTRUCTIVE, onConfirm)
                .cancelAction(0).defaultAction(0);
    }

    /** A notice with one button, which Enter and Escape both run. */
    public static DialogComponent alert(Box area, String title, String message, String okLabel, Runnable onClose) {
        return new DialogComponent(area).title(title).message(message)
                .action(okLabel, ButtonComponent.Role.PRIMARY, onClose)
                .cancelAction(0).defaultAction(0);
    }

    // ---- setup ----

    public DialogComponent title(String title) {
        this.title = title == null ? "" : title;
        dirty = true;
        return this;
    }

    public DialogComponent message(String message) {
        this.message = message == null ? "" : message;
        dirty = true;
        return this;
    }

    /** A custom child under the message, scrolled with it. Null removes it. Its width counts towards the dialog's. */
    public DialogComponent content(BaseComponent content) {
        this.content = content;
        dirty = true;
        return this;
    }

    /**
     * Adds a button, left to right. Pressing it closes the dialog, then runs the callback.
     *
     * @param onPress may be null
     * @throws IllegalStateException past the third action
     */
    public DialogComponent action(String label, ButtonComponent.Role role, Runnable onPress) {
        if (actions.size() >= MAX_ACTIONS) {
            throw new IllegalStateException("A dialog has at most " + MAX_ACTIONS + " actions");
        }
        actions.add(new Action(Objects.requireNonNull(label, "label"), Objects.requireNonNull(role, "role"), onPress));
        dirty = true;
        return this;
    }

    /** Removes every action, to use the same dialog for another question. */
    public DialogComponent clearActions() {
        actions.clear();
        defaultAction = -1;
        cancelAction = -1;
        dirty = true;
        return this;
    }

    /** What Enter runs, and where the keyboard starts. By default the last action that is not destructive. */
    public DialogComponent defaultAction(int index) {
        defaultAction = checked(index);
        dirty = true;
        return this;
    }

    /** What Escape and the dim run in place of {@link #onCancel(Runnable)}. -1 for none. */
    public DialogComponent cancelAction(int index) {
        cancelAction = index < 0 ? -1 : checked(index);
        return this;
    }

    /** Runs when the dialog is cancelled, if no {@link #cancelAction(int)} is set. */
    public DialogComponent onCancel(Runnable onCancel) {
        this.onCancel = onCancel;
        return this;
    }

    /** False to ignore Escape and the dim, so that only an action closes it. On by default. */
    public DialogComponent cancellable(boolean cancellable) {
        this.cancellable = cancellable;
        return this;
    }

    /** True to cancel on a left click on the dim. Off by default. */
    public DialogComponent closeOnBackdrop(boolean closeOnBackdrop) {
        this.closeOnBackdrop = closeOnBackdrop;
        return this;
    }

    /** Turns one action on or off; it can change while the dialog is open. */
    public DialogComponent actionActive(int index, boolean on) {
        actions.get(checked(index)).active = on;
        return this;
    }

    /** A tooltip for one action. */
    public DialogComponent actionTooltip(int index, String... lines) {
        actions.get(checked(index)).tooltip = lines;
        dirty = true;
        return this;
    }

    /** The width it aims for before the content, buttons and cap are counted. 220 by default. */
    public DialogComponent preferredWidth(int width) {
        preferredWidth = width;
        dirty = true;
        return this;
    }

    /** A cap on the card, on top of the area it has to fit in. */
    public DialogComponent maxSize(int width, int height) {
        maxWidth = width;
        maxHeight = height;
        dirty = true;
        return this;
    }

    /** Where the card slides in from. Down by default. */
    public DialogComponent slideFrom(Slide from) {
        Glide fresh = new Glide(from, OPEN_MS);
        fresh.snap(slide.shown());
        slide = fresh;
        return this;
    }

    @Override
    public DialogComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    // ---- opening and closing ----

    /**
     * Opens over the panel and takes its focus.
     *
     * @param panel the panel this dialog was added to
     * @throws IllegalStateException with no action, if the panel does not hold it, or if another
     *         kind of widget comes after it
     */
    public void open(PanelComponent panel) {
        if (actions.isEmpty()) {
            throw new IllegalStateException("A dialog needs at least one action");
        }
        checkPlace(panel);
        if (!open) {
            BaseComponent current = panel.focus();
            focusBefore = current == this ? null : current;
        }
        panel.closeOverlay();
        boolean turningBack = visible && !open;
        host = panel;
        open = true;
        visible = true;
        if (body == null) {
            body = new Body(panel.theme(), panel.words);
        }
        dirty = true;
        if (reducedMotion()) {
            slide.snap(true);
        } else if (turningBack) {
            slide.show();
        } else {
            slide.replay();
        }
        panel.focus(this);
    }

    /** Closes without running any callback. */
    public void close() {
        if (!open) {
            return;
        }
        open = false;
        if (reducedMotion()) {
            slide.snap(false);
        } else {
            slide.hide();
        }
        settle();
        if (host != null) {
            host.focus(focusBefore);
        }
        focusBefore = null;
    }

    /** As Escape does: closes and runs the cancel callback, unless not {@link #cancellable(boolean)}. */
    public void cancel() {
        if (!open || !active || !cancellable) {
            return;
        }
        close();
        Runnable run = cancelAction >= 0 ? actions.get(cancelAction).onPress : onCancel;
        if (run != null) {
            run.run();
        }
    }

    /** True from {@link #open(PanelComponent)} to the moment it closes, before the slide out ends. */
    public boolean isOpen() {
        return open;
    }

    /** True while the card slides. Clicks wait for it to rest; keys do not. */
    public boolean animating() {
        return slide.moving();
    }

    /** The card's place, once it has been drawn or given input. */
    Box cardBox() {
        return card;
    }

    /** The action the keyboard is on, or -1 if it is on the content or nothing. */
    public int focusedAction() {
        BaseComponent at = keyboardAt();
        return at instanceof ActionButton ? buttons.indexOf(at) : -1;
    }

    /** Shows a popup above the card: give this to a {@link DropdownComponent} in the content. */
    public void showOverlay(BaseComponent widget) {
        if (body != null) {
            body.showOverlay(widget);
        }
    }

    private void checkPlace(PanelComponent panel) {
        List<BaseComponent> widgets = panel.widgets();
        int at = widgets.indexOf(this);
        if (at < 0) {
            throw new IllegalStateException("Add the dialog to the panel before opening it");
        }
        for (int i = at + 1; i < widgets.size(); i++) {
            if (!(widgets.get(i) instanceof DialogComponent)) {
                throw new IllegalStateException("A dialog must come after every other widget of its panel");
            }
        }
    }

    private int checked(int index) {
        if (index < 0 || index >= actions.size()) {
            throw new IndexOutOfBoundsException("No action " + index + " of " + actions.size());
        }
        return index;
    }

    private void settle() {
        if (!open && visible && !slide.moving()) {
            visible = false;
        }
    }

    private void press(Action action) {
        close();
        if (action.onPress != null) {
            action.onPress.run();
        }
    }

    // ---- layout ----

    private void layout() {
        if (!dirty && laidX == x && laidY == y && laidW == w && laidH == h) {
            return;
        }
        dirty = false;
        laidX = x;
        laidY = y;
        laidW = w;
        laidH = h;

        int fitW = Math.max(40, w - 2 * MARGIN);
        int fitH = Math.max(40, h - 2 * MARGIN);
        int[] buttonWidths = new int[actions.size()];
        int buttonsW = GAP * Math.max(0, actions.size() - 1);
        for (int i = 0; i < buttonWidths.length; i++) {
            buttonWidths[i] = Math.max(BUTTON_MIN, TextMeasure.width(actions.get(i).label) + BUTTON_PAD);
            buttonsW += buttonWidths[i];
        }
        int contentW = content == null ? 0 : content.w;
        int cardW = Math.max(preferredWidth, Math.max(buttonsW, contentW) + 2 * PAD);
        cardW = Math.min(cardW, Math.min(fitW, maxWidth));
        int inner = Math.max(1, cardW - 2 * PAD);

        titleLines = fitTitle(inner);
        int titleH = titleLines.size() * LINE;
        int chromeH = PAD + titleH + (titleH > 0 ? GAP : 0) + GAP + BUTTON_H + PAD;
        int capH = Math.min(fitH, maxHeight);
        int room = Math.max(LINE, capH - chromeH);

        List<String> lines = wrapMessage(inner);
        int viewW = inner;
        if (flowHeight(lines) > room) {
            viewW = Math.max(1, inner - SCROLL_ROOM);
            lines = wrapMessage(viewW);
        }
        int flowH = flowHeight(lines);
        int viewH = Math.min(flowH, room);
        if (flowH > room && content == null) {
            viewH = Math.max(LINE, room - room % LINE); // whole lines, none cut in half at the bottom
        }
        int cardH = chromeH + viewH;

        int cardX = x + (w - cardW) / 2;
        int cardY = y + Math.max(0, (h - cardH) / 2);
        card = new Box("card", cardX, cardY, cardW, cardH);

        int left = cardX + PAD;
        int top = cardY + PAD + titleH + (titleH > 0 ? GAP : 0);
        // A scroll box only when it is needed: some families inset it, which would scroll a fitting message.
        boolean overflow = flowH > room;
        ScrollComponent view = overflow ? new ScrollComponent(new Box("view", left, top, inner, viewH)) : null;
        if (view != null) {
            view.fit = false;
            view.step = LINE;
        }
        List<BaseComponent> flow = new ArrayList<>();
        int flowY = top;
        if (!lines.isEmpty()) {
            flow.add(new TextBlock(new Box("message", left, flowY, viewW, lines.size() * LINE), lines));
            flowY += lines.size() * LINE + (content != null ? GAP : 0);
        }
        if (content != null) {
            content.x = left;
            content.y = flowY;
            flow.add(content);
        }
        if (view != null) {
            flow.forEach(view::add);
            flow = List.of(view);
        }

        int buttonsTotal = GAP * Math.max(0, actions.size() - 1);
        for (int width : buttonWidths) {
            buttonsTotal += width;
        }
        boolean squeezed = buttonsTotal > inner;
        int share = squeezed ? (inner - GAP * (actions.size() - 1)) / actions.size() : 0;
        int buttonY = cardY + cardH - PAD - BUTTON_H;
        int buttonX = squeezed ? left : left + inner - buttonsTotal;
        List<ActionButton> built = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            int width = squeezed ? share : buttonWidths[i];
            Action action = actions.get(i);
            ActionButton button = new ActionButton(new Box("action" + i, buttonX, buttonY, width, BUTTON_H), action,
                    () -> press(action));
            if (action.tooltip != null) {
                button.tooltip(action.tooltip);
            }
            built.add(button);
            buttonX += width + GAP;
        }
        scroll = view;
        buttons = built;
        syncActive();
        body.rebuild(cardX, cardY, cardW, cardH, flow, built);
        placeKeyboard(built);
    }

    /** A panel has no way to set its keyboard stop except Tab, so Tab walks it to the default action. */
    private void placeKeyboard(List<ActionButton> built) {
        int start = startAction();
        if (start < 0 || !built.get(start).active) {
            return;
        }
        ActionButton target = built.get(start);
        for (int i = body.stops().size(); i > 0 && body.cursor() != target; i--) {
            body.keyPressed(InputKey.TAB, false);
        }
        if (scroll != null) {
            scroll.setScroll(0);
        }
    }

    private int startAction() {
        if (defaultAction >= 0 && defaultAction < actions.size()) {
            return defaultAction;
        }
        for (int i = actions.size() - 1; i >= 0; i--) {
            if (actions.get(i).role != ButtonComponent.Role.DESTRUCTIVE) {
                return i;
            }
        }
        return actions.size() - 1;
    }

    private List<String> fitTitle(int width) {
        if (title.isEmpty()) {
            return List.of();
        }
        List<String> all = TextMeasure.wrapAll(title, width);
        if (all.size() <= TITLE_LINES) {
            return all;
        }
        List<String> kept = new ArrayList<>(all.subList(0, TITLE_LINES));
        String rest = String.join(" ", all.subList(TITLE_LINES - 1, all.size()));
        kept.set(TITLE_LINES - 1, TextMeasure.draw().ellipsize(rest, width));
        return kept;
    }

    private List<String> wrapMessage(int width) {
        return message.isEmpty() ? List.of() : TextMeasure.wrapAll(message, width);
    }

    private int flowHeight(List<String> lines) {
        int height = lines.size() * LINE;
        if (content != null) {
            height += content.h + (lines.isEmpty() ? 0 : GAP);
        }
        return height;
    }

    private void syncActive() {
        for (ActionButton button : buttons) {
            button.active = active && button.action.active;
        }
    }

    /** Lays out if needed and tells if input may go in. The caller swallows the input either way. */
    private boolean ready() {
        layout();
        syncActive();
        return open;
    }

    private BaseComponent keyboardAt() {
        if (body == null) {
            return null;
        }
        BaseComponent focus = body.focus();
        return focus != null ? focus : body.cursor();
    }

    private boolean scrollable() {
        return scroll != null && scroll.contentHeight() > scroll.h;
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        settle();
        if (!visible || body == null) {
            return;
        }
        ready();
        if (open && host != null && host.focus() != this) {
            host.focus(this);
        }
        Palette p = t.palette();
        d.nextLayer();
        int alpha = Math.round(DIM_ALPHA * clamp01(slide.progress()));
        d.fill(x, y, x + w, y + h, (alpha << 24) | (p.shadow() & 0xFFFFFF));
        d.nextLayer();

        boolean moving = slide.moving();
        float dx = slide.offsetX(card.w() + (w - card.w()) / 2F + 4);
        float dy = slide.offsetY(card.h() + (h - card.h()) / 2F + 4);
        boolean live = open && !moving && active;
        body.use(t);
        if (moving) {
            d.scissor(x, y, x + w, y + h);
        }
        d.push(dx, dy, 1F);
        t.frame().popup(d, p, card.x(), card.y(), card.w(), card.h());
        int pointerX = live ? mx : OFF_POINTER;
        int pointerY = live ? my : OFF_POINTER;
        body.drawBase(d, pointerX, pointerY, delta);
        body.drawOverlay(d, pointerX, pointerY, delta);
        d.pop();
        if (moving) {
            d.endScissor();
        }
    }

    private static float clamp01(float value) {
        return Math.max(0F, Math.min(1F, value));
    }

    /** The panel must not ring the whole area; the card's own panel draws the ring on its stops. */
    @Override
    protected boolean drawFocus(Draw d, Theme t) {
        return true;
    }

    @Override
    protected Box focusBox() {
        return card;
    }

    @Override
    public void tick() {
        settle();
        if (visible && body != null) {
            body.tickAll();
        }
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!open) {
            return null;
        }
        // Empty, not null: a panel goes on to the widgets beneath on null.
        if (!active || slide.moving() || body == null) {
            return List.of();
        }
        layout();
        List<String> inner = body.tooltipAt(mx, my);
        if (inner != null && !inner.isEmpty()) {
            return inner;
        }
        boolean overTitle = card.contains(mx, my) && my < card.y() + PAD + titleLines.size() * LINE;
        return overTitle && tooltip != null ? tooltip : List.of();
    }

    // ---- input ----

    @Override
    public boolean isOver(double mx, double my) {
        return open || super.isOver(mx, my);
    }

    @Override
    protected boolean takesFocus() {
        return open;
    }

    @Override
    protected boolean navigable() {
        return open;
    }

    @Override
    public List<BaseComponent> stops() {
        return open ? List.of(this) : List.of();
    }

    @Override
    protected boolean capturesKeys() {
        return open && body != null && body.capturing();
    }

    @Override
    public boolean keyCaptured(KeyPress press) {
        return open && active && body != null && body.rawKey(press);
    }

    @Override
    public boolean keyReleasedCaptured(KeyPress press) {
        return open && active && body != null && body.rawKeyReleased(press);
    }

    /** Runs the default action, as Enter does with nothing else to press. */
    @Override
    protected boolean activate() {
        if (!ready()) {
            return false;
        }
        runDefault();
        return true;
    }

    private void runDefault() {
        if (!active) {
            return;
        }
        int index = startAction();
        if (index >= 0 && index < buttons.size() && buttons.get(index).active) {
            buttons.get(index).fire();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!ready()) {
            return false;
        }
        if (slide.moving() || !active) {
            return true;
        }
        if (body.overlay() != null || card.contains(mx, my)) {
            body.mouseClicked(mx, my, button);
        } else if (closeOnBackdrop && button == 0 && cancellable) {
            BaseComponent.click();
            cancel();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!ready()) {
            return false;
        }
        body.mouseReleased(mx, my, button);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!ready()) {
            return false;
        }
        if (!slide.moving() && active) {
            body.mouseDragged(mx, my, button, dx, dy);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!ready()) {
            return false;
        }
        if (!slide.moving()) {
            body.mouseScrolled(mx, my, amount);
        }
        return true;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!ready()) {
            return false;
        }
        if (body.capturing()) {
            if (active) {
                body.keyPressed(key, shift);
            }
            return true;
        }
        if (!active) {
            return true;
        }
        BaseComponent at = keyboardAt();
        switch (key) {
            case ESCAPE -> {
                if (body.overlay() != null) {
                    body.closeOverlay();
                } else {
                    cancelByKey();
                }
            }
            case ENTER -> enter(at);
            case UP, DOWN, HOME, END -> {
                if (body.overlay() == null && scrollable() && (at == null || at instanceof ActionButton)) {
                    scrollBy(key);
                } else {
                    body.keyPressed(key, shift);
                }
            }
            default -> body.keyPressed(key, shift);
        }
        return true;
    }

    private void cancelByKey() {
        if (cancellable) {
            BaseComponent.click();
            cancel();
        }
    }

    private void enter(BaseComponent at) {
        if (body.overlay() != null) {
            body.keyPressed(InputKey.ENTER, false);
        } else if (typing()) {
            body.keyPressed(InputKey.ENTER, false);
            runDefault();
        } else if (at == null || !at.active || !body.keyPressed(InputKey.ENTER, false)) {
            runDefault();
        }
    }

    /** A text field keeps Enter to itself, and in a dialog it should press the default as well. */
    private boolean typing() {
        if (content == null) {
            return false;
        }
        for (BaseComponent stop : content.stops()) {
            if (stop instanceof TextFieldComponent && stop.focused) {
                return true;
            }
        }
        return false;
    }

    private void scrollBy(InputKey key) {
        switch (key) {
            case UP -> scroll.setScroll(scroll.scroll() - LINE);
            case DOWN -> scroll.setScroll(scroll.scroll() + LINE);
            case HOME -> scroll.setScroll(0);
            default -> scroll.setScroll(scroll.contentHeight());
        }
    }

    @Override
    public boolean charTyped(int codePoint) {
        if (!ready()) {
            return false;
        }
        if (active) {
            body.charTyped(codePoint);
        }
        return true;
    }

    // ---- parts ----

    private static final class Action {
        final String label;
        final ButtonComponent.Role role;
        final Runnable onPress;
        boolean active = true;
        String[] tooltip;

        Action(String label, ButtonComponent.Role role, Runnable onPress) {
            this.label = label;
            this.role = role;
            this.onPress = onPress;
        }
    }

    /** A button with the role of its action, whose press the dialog can run for Enter. */
    private static final class ActionButton extends ButtonComponent {
        final Action action;

        ActionButton(Box box, Action action, Runnable onPress) {
            super(box, action.label, onPress);
            this.action = action;
            role(action.role);
        }

        void fire() {
            activate();
        }

        /** The keyboard can rest on an action that was turned off: no ring on it then. */
        @Override
        protected boolean drawFocus(Draw d, Theme t) {
            return !active || super.drawFocus(d, t);
        }
    }

    /** The message: lines already wrapped to the width they sit in. */
    private static final class TextBlock extends BaseComponent {
        private static final double READABLE = 4.5D;

        private final List<String> lines;

        TextBlock(Box box, List<String> lines) {
            super(box);
            this.lines = lines;
        }

        /** The quieter text, unless the theme makes it too weak on the card to read (vanilla dark). */
        private static int ink(Palette p) {
            return Shapes.contrast(p.textSoft(), p.surface() | 0xFF000000) >= READABLE ? p.textSoft() : p.text();
        }

        @Override
        protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
            int line = y;
            for (String text : lines) {
                d.text(text, x, line, ink(t.palette()));
                line += LINE;
            }
        }
    }

    /**
     * The card. A panel of its own, as a {@link WindowComponent} holds one, because a panel already does
     * Tab that wraps, arrows, the focus ring, typing and popups. Holding only the dialog's parts makes
     * Tab a trap for free. Words are the host panel's, for the default texts of widgets put inside.
     */
    private final class Body extends PanelComponent {
        private int cardW;
        private int cardH;

        Body(Theme theme, Words words) {
            super(theme, words, 0, 0);
        }

        void use(Theme current) {
            theme = current;
        }

        void showOverlay(BaseComponent widget) {
            overlay = widget;
        }

        void rebuild(int cardX, int cardY, int width, int height, List<BaseComponent> flow, List<ActionButton> parts) {
            focus(null);
            widgets.clear();
            left = cardX;
            top = cardY;
            cardW = width;
            cardH = height;
            flow.forEach(this::add);
            parts.forEach(this::add);
        }

        @Override public int width() { return cardW; }
        @Override public int height() { return cardH; }

        @Override
        protected void drawBackground(Draw d, int mx, int my) {
            int line = top + PAD;
            for (String text : titleLines) {
                d.text(text, left + PAD, line, theme.palette().text());
                line += LINE;
            }
        }
    }
}
