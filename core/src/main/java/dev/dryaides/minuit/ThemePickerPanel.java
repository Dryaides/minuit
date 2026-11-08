package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Accent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The page a player picks the look of one mod's screens on: a card per family the mod is offered
 * (the shared ones and its own), then a card per chroma of the chosen family, the family's
 * recommended ones first, then light, dark or auto. Each card is a {@link ThemePreview} drawn in
 * the look it stands for. "Apply everywhere" hands the choice to every mod. The page wears the
 * mod's look, so a pick shows at once when the host builds a new page for it;
 * {@code client.ThemeScreen} is that host in game.
 *
 * <p>Shows {@link #COLUMNS} times {@link #FAMILY_ROWS} family cards and one row of chroma cards
 * at once, each in a {@link ScrollComponent} when there are more.
 */
public final class ThemePickerPanel extends PanelComponent {

    public static final int WIDTH = 300;
    public static final int HEIGHT = 240;
    public static final int COLUMNS = 3;
    public static final int FAMILY_ROWS = 2;

    private static final int LINE = 10;
    private static final int CARD_H = 42;
    /** Room under a card for its shadow and the selection ring. */
    private static final int ROW_STEP = CARD_H + 8;
    private static final int GAP = 8;
    private static final int CHIP_H = 14;
    /** Room under a chip for the selection ring. */
    private static final int CHIP_STEP = CHIP_H + 6;

    public static final Box HEADER = new Box("header", 6, 6, 288, 18);
    public static final Box TITLE = new Box("title", 14, 10, 100, LINE);
    public static final Box EVERY_MOD = new Box("every mod", 118, 8, 153, 14);
    public static final Box CLOSE = new Box("close", 275, 8, 15, 15);
    public static final Box FAMILY_LABEL = new Box("family label", 14, 28, 272, LINE);
    /** The family cards scroll in here; it leaves the cards' rings room on every side. */
    public static final Box FAMILY_SCROLL = new Box("family scroll", 10, 39, 278, 96);
    public static final Box FAMILY_GRID = new Box("family cards", 12, 41, 268, CARD_H);
    public static final Box CHROMA_LABEL = new Box("chroma label", 14, 141, 272, LINE);
    public static final Box CHROMA_SCROLL = new Box("chroma scroll", 10, 152, 278, 48);
    public static final Box CHROMA_GRID = new Box("chroma chips", 12, 154, 268, CHIP_H);
    public static final Box VARIANT_LABEL = new Box("variant label", 14, 203, 92, LINE);
    public static final Box VARIANT_ROW = new Box("variant buttons", 108, 201, 180, 14);
    public static final Box ACCENT_LABEL = new Box("accent label", 14, 221, 92, LINE);
    /** "None", then one swatch per {@link Accent#PRESETS}. */
    public static final Box ACCENT_ROW = new Box("accent swatches", 108, 219, 180, 14);
    private static final int SWATCH = 14;

    private final Theme own;
    private final ThemeChoice current;
    private final Consumer<ThemeChoice> pick;
    private final List<ButtonComponent> swatches = new ArrayList<>();

    /**
     * @param own      the theme the hosting mod registered; its id is the mod whose look the page sets
     * @param current  the player's choice for that mod now, or null while it wears {@code own}
     * @param pick     a new choice for that mod, for the host to save and show
     * @param everyMod "Apply everywhere": {@code current} for every mod, for the host to save
     * @param close    the cross: closes the screen and every page under it
     * @param back     the arrow: back to the page this one was opened from, or null when there is none
     */
    public ThemePickerPanel(Words words, int left, int top, Theme own, ThemeChoice current,
                            Consumer<ThemeChoice> pick, Consumer<ThemeChoice> everyMod, Runnable close,
                            Runnable back) {
        super(Themes.resolve(own, current), words, left, top);
        this.own = own;
        this.current = current != null ? current : defaultChoice(own);
        this.pick = pick;
        add(new ButtonComponent(at(CLOSE), "", close).icon(Icon.CLOSE).style(ButtonComponent.Style.GHOST)
                .tooltip(words.get("minuit.close")));
        add(new ButtonComponent(at(EVERY_MOD), words.get("minuit.picker.every_mod"), () -> everyMod.accept(this.current))
                .active(current != null).tooltip(words.get("minuit.picker.every_mod_tip")));
        addBack(back);
        addFamilies();
        addChromas();
        addVariants();
        addAccents();
    }

    public static int left(int screenWidth) {
        return (screenWidth - WIDTH) / 2;
    }

    public static int top(int screenHeight) {
        return (screenHeight - HEIGHT) / 2;
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    /** One card of a grid: the look it shows, its name, what a click picks. */
    private record Card(Theme look, String name, ThemeChoice choice) {
    }

    private void addFamilies() {
        List<Card> cards = new ArrayList<>();
        Card chosen = null;
        for (ThemeFamily family : Themes.families(this.own.id())) {
            ThemeFamily.Chroma first = family.first();
            Card card = new Card(look(family, first), this.words.get(family.nameKey()),
                    new ThemeChoice(family.id(), first.id(), variant(), accent()));
            cards.add(card);
            if (family == chosenFamily()) {
                chosen = card;
            }
        }
        scrolled(FAMILY_SCROLL, FAMILY_GRID, cards, chosen, CARD_H, ROW_STEP,
                (d, theme, card, cell, glow, selected) -> ThemePreview.paint(d, theme, card.look(), card.name(), cell,
                        glow, selected));
    }

    private void addChromas() {
        ThemeFamily family = chosenFamily();
        if (family == null) {
            return;
        }
        List<Card> cards = new ArrayList<>();
        Card chosen = null;
        for (ThemeFamily.Chroma chroma : family.chromas()) {
            Card card = new Card(look(family, chroma), this.words.get(chroma.nameKey()),
                    new ThemeChoice(family.id(), chroma.id(), variant(), accent()));
            cards.add(card);
            if (chroma == chosenChroma(family)) {
                chosen = card;
            }
        }
        scrolled(CHROMA_SCROLL, CHROMA_GRID, cards, chosen, CHIP_H, CHIP_STEP,
                (d, theme, card, cell, glow, selected) -> ThemePreview.paintPalette(d, theme, card.look(), card.name(),
                        cell, glow, selected));
    }

    /** A grid of cards in a scroll box, opened scrolled to the chosen card. */
    private void scrolled(Box box, Box grid, List<Card> cards, Card chosen, int cellH, int step,
                          GridComponent.GlowingCell<Card> paint) {
        ScrollComponent scroll = add(new ScrollComponent(at(box)));
        scroll.bottomRoom = 4;
        scroll.step = step;
        GridComponent<Card> cells = scroll.add(new GridComponent<>(at(grid), COLUMNS, cellH, GAP, step - cellH, cards,
                GridComponent.glowing(paint), card -> this.pick.accept(card.choice())).selected(chosen));
        if (chosen != null) {
            Box cell = cells.cell(cards.indexOf(chosen));
            scroll.reveal(new Box("chosen", cell.x(), cell.y() - 2, cell.w(), cell.h() + 6));
        }
    }

    private void addVariants() {
        ThemeFamily family = chosenFamily();
        ThemeFamily.Chroma chroma = family == null ? null : chosenChroma(family);
        ThemeChoice.Variant[] variants = {ThemeChoice.Variant.LIGHT, ThemeChoice.Variant.DARK, ThemeChoice.Variant.AUTO};
        Box[] cells = at(VARIANT_ROW).columns(variants.length, 4);
        for (int i = 0; i < variants.length; i++) {
            ThemeChoice.Variant variant = variants[i];
            ButtonComponent button = add(new ButtonComponent(cells[i], this.words.get("minuit.picker." + variant.id()),
                    () -> this.pick.accept(new ThemeChoice(this.current.family(), this.current.chroma(), variant, accent()))));
            button.selected(chroma != null && variant == variant());
            button.active(chroma != null && chroma.has(variant));
            if (variant == ThemeChoice.Variant.AUTO) {
                button.tooltip(this.words.get("minuit.picker.auto_tip"));
            }
        }
    }

    /**
     * The accent overlay row: "None" as wide as two swatches, then a square per preset. A swatch's
     * colour chip is drawn by {@link #drawForeground}, on the button the row adds for it.
     */
    private void addAccents() {
        Box row = at(ACCENT_ROW);
        boolean on = chosenFamily() != null;
        ButtonComponent none = add(new ButtonComponent(new Box("accent none", row.x(), row.y(), 2 * SWATCH + 4, SWATCH),
                this.words.get("minuit.picker.accent_none"), () -> this.pick.accept(this.current.withAccent(""))));
        none.selected(on && accent().isEmpty()).active(on);
        int x = row.x() + 2 * SWATCH + 8;
        for (Accent a : Accent.PRESETS) {
            ButtonComponent swatch = add(new ButtonComponent(new Box("accent " + a.id(), x, row.y(), SWATCH, SWATCH), "",
                    () -> this.pick.accept(this.current.withAccent(a.id()))));
            swatch.selected(on && a.id().equals(accent())).active(on)
                    .tooltip(this.words.get("minuit.picker.accent." + a.id()));
            this.swatches.add(swatch);
            x += SWATCH + 4;
        }
    }

    /** The kit's default look, as the choice that selects it, while {@code own} still wears it and the player picked nothing. */
    private static ThemeChoice defaultChoice(Theme own) {
        boolean wearsDefault = own.palette().equals(Themes.defaultTheme().palette());
        boolean offered = Themes.family(own.id(), Themes.defaultTheme().id()) != null;
        return wearsDefault && offered
                ? new ThemeChoice(Themes.defaultTheme().id(), Themes.defaultTheme().id(), ThemeChoice.Variant.LIGHT)
                : null;
    }

    private String accent() {
        return this.current == null ? "" : this.current.accent();
    }

    /** The picked family, or null while the mod wears its own look or for a family it is not offered. */
    private ThemeFamily chosenFamily() {
        return this.current == null ? null : Themes.family(this.own.id(), this.current.family());
    }

    private ThemeChoice.Variant variant() {
        return this.current == null ? ThemeChoice.Variant.AUTO : this.current.variant();
    }

    private ThemeFamily.Chroma chosenChroma(ThemeFamily family) {
        ThemeFamily.Chroma chroma = family.chroma(this.current.chroma());
        return chroma != null ? chroma : family.first();
    }

    /** A chroma as its card shows it: in the variant the player chose. */
    private Theme look(ThemeFamily family, ThemeFamily.Chroma chroma) {
        return new Theme(family.id(), chroma.palette(variant()), family.frame());
    }

    @Override
    public List<Box> textBoxes() {
        List<Box> boxes = new ArrayList<>(super.textBoxes());
        boxes.add(at(new Box("title", titleX(), TITLE.y(), TITLE.w() - (titleX() - TITLE.x()), LINE)));
        for (Box label : List.of(FAMILY_LABEL, CHROMA_LABEL, VARIANT_LABEL, ACCENT_LABEL)) {
            boxes.add(at(label));
        }
        return boxes;
    }

    @Override
    protected void drawBackground(Draw d, int mx, int my) {
        Palette p = this.theme.palette();
        FrameStyle frame = this.theme.frame();
        frame.panel(d, p, this.left, this.top, WIDTH, HEIGHT);
        Box header = at(HEADER);
        frame.header(d, p, header.x(), header.y(), header.w(), header.h());
        d.textIn(this.words.get("minuit.picker.title"),
                at(new Box("title", titleX(), TITLE.y(), TITLE.w() - (titleX() - TITLE.x()), LINE)), p.text());
        d.textIn(this.words.get("minuit.picker.family"), at(FAMILY_LABEL), p.textSoft());
        d.textIn(this.words.get("minuit.picker.variant"), at(VARIANT_LABEL), p.textSoft());
        d.textIn(this.words.get("minuit.picker.accent"), at(ACCENT_LABEL), p.textSoft());
        if (chosenFamily() == null) {
            d.textIn(this.words.get("minuit.picker.own_note"), at(CHROMA_LABEL), p.textFaint());
        } else {
            d.textIn(this.words.get("minuit.picker.chroma"), at(CHROMA_LABEL), p.textSoft());
        }
    }

    /** Each swatch's chip: a whole-pixel square inset in its button, in the accent as this page's variant shows it. */
    @Override
    protected void drawForeground(Draw d, int mx, int my) {
        super.drawForeground(d, mx, my);
        Palette p = this.theme.palette();
        for (int i = 0; i < this.swatches.size(); i++) {
            Box b = this.swatches.get(i).box();
            int color = Accent.PRESETS.get(i).on(p);
            if (!this.swatches.get(i).active) {
                color = Shapes.mix(color, p.disabled(), 0.6F);
            }
            d.fill(b.x() + 4, b.y() + 4, b.x() + b.w() - 4, b.y() + b.h() - 4, color);
        }
    }
}
