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
 * at once, each in a {@link ScrollComponent} when there are more. In game, a last row holds the switch that
 * draws every tooltip of the game in minuit's theme ({@link #itemTips}).
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
    private static final int CHIP_H = 10;
    /** Three across leave a name 54 px beside its swatch strip: four left 31 and cut Bedrock, Amethyst, Minimalist. */
    private static final int CHIP_COLUMNS = 3;
    /** Room under a chip for the selection ring. */
    private static final int CHIP_STEP = CHIP_H + 6;

    /** The strip under the last row that holds minuit's version, bottom right. */
    static final int VERSION_ROW = 10;
    public static final Box HEADER = new Box("header", 6, 6, 288, 18);
    public static final Box TITLE = new Box("title", 14, 10, 100, LINE);
    public static final Box EVERY_MOD = new Box("every mod", 118, 7, 153, 14);
    /** "Apply everywhere" beside the demo button, when the host offers the demo. */
    public static final Box EVERY_MOD_SHORT = new Box("every mod", 118, 7, 113, 14);
    public static final Box DEMO = new Box("demo", 235, 7, 36, 14);
    public static final Box CLOSE = new Box("close", 275, 8, 15, 15);
    public static final Box FAMILY_LABEL = new Box("family label", 14, 28, 272, LINE);
    /** The family cards scroll in here; it leaves the cards' rings room on every side. */
    public static final Box FAMILY_SCROLL = new Box("family scroll", 10, 39, 278, 101);
    public static final Box FAMILY_GRID = new Box("family cards", 12, 41, 268, CARD_H);
    public static final Box CHROMA_LABEL = new Box("chroma label", 14, 142, 272, LINE);
    public static final Box CHROMA_SCROLL = new Box("chroma scroll", 10, 153, 278, 51);
    public static final Box CHROMA_GRID = new Box("chroma chips", 12, 155, 268, CHIP_H);
    public static final Box VARIANT_LABEL = new Box("variant label", 14, 206, 92, LINE);
    public static final Box VARIANT_ROW = new Box("variant buttons", 108, 204, 180, 14);
    public static final Box ACCENT_LABEL = new Box("accent label", 14, 224, 92, LINE);
    /** "None", then one swatch per {@link Accent#presets()}. */
    public static final Box ACCENT_ROW = new Box("accent swatches", 108, 222, 180, 14);
    private static final int SWATCH = 14;
    /** The page with the {@link #itemTips} row under the accents. */
    public static final int HEIGHT_WITH_ITEM_TIPS = HEIGHT + 18;
    public static final Box ITEM_TIPS_ROW = new Box("item tips", 14, 240, 272, 14);
    /** The height an optional row under the accents adds ({@link #itemTips}, {@link #background}). */
    public static final int OPTION_ROW = 18;

    private final Theme own;
    /** The family the mod's own theme draws with, or null: what the "Default" card picks with the mod's palette. */
    private final ThemeFamily ownFamily;
    private final ThemeChoice current;
    private final Consumer<ThemeChoice> pick;
    private final List<ButtonComponent> swatches = new ArrayList<>();
    /** How many optional rows sit under the accents. */
    private int rows;
    private Box backgroundLabel;

    /**
     * @param own      the theme the hosting mod registered; its id is the mod whose look the page sets
     * @param current  the player's choice for that mod now, or null while it wears {@code own}
     * @param pick     a new choice for that mod, for the host to save and show; null clears the mod's choice
     *                 (the "Default" card, shown when the mod registered a theme of its own)
     * @param everyMod "Apply everywhere": {@code current} for every mod, for the host to save; null clears every
     *                 mod's choice while the mod wears its own look
     * @param close    the cross: closes the screen and every page under it
     * @param back     the arrow: back to the page this one was opened from, or null when there is none
     */
    public ThemePickerPanel(Words words, int left, int top, Theme own, ThemeChoice current,
                            Consumer<ThemeChoice> pick, Consumer<ThemeChoice> everyMod, Runnable close,
                            Runnable back) {
        this(words, left, top, own, current, pick, everyMod, close, back, null);
    }

    /** @param demo the header's "Demo" button: opens the components in this look, or null for no button */
    public ThemePickerPanel(Words words, int left, int top, Theme own, ThemeChoice current,
                            Consumer<ThemeChoice> pick, Consumer<ThemeChoice> everyMod, Runnable close,
                            Runnable back, Runnable demo) {
        super(Themes.resolve(own, current), words, left, top);
        this.own = own;
        this.ownFamily = Themes.ownFamily(own.id());
        this.current = current != null ? current : this.ownFamily != null ? ownDefault() : defaultChoice(own);
        this.pick = choice -> pick.accept(isOwnDefault(choice) ? null : choice);
        add(new ButtonComponent(at(CLOSE), "", close).icon(Icon.CLOSE).style(ButtonComponent.Style.GHOST)
                .tooltip(words.get("minuit.close")));
        add(new ButtonComponent(at(demo == null ? EVERY_MOD : EVERY_MOD_SHORT), words.get("minuit.picker.every_mod"),
                () -> everyMod.accept(isOwnDefault(this.current) ? null : this.current)).active(current != null || hasOwnLook()).tooltip(words.get("minuit.picker.every_mod_tip")));
        if (demo != null) {
            add(new ButtonComponent(at(DEMO), words.get("minuit.demo.button"), demo)
                    .tooltip(words.get("minuit.demo.button_tip")));
        }
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

    /** The top of a page that gets the {@link #itemTips} row. */
    public static int topWithItemTips(int screenHeight) {
        return (screenHeight - HEIGHT_WITH_ITEM_TIPS) / 2;
    }

    /**
     * Adds the switch "Theme item tooltips everywhere" under the accents: {@code on} as the player left it,
     * {@code set} saving a flip. The setting is the player's for the whole game, whichever mod's page shows it; place
     * the page with {@link #topWithItemTips}.
     */
    public ThemePickerPanel itemTips(boolean on, Consumer<Boolean> set) {
        Box row = optionRow(ITEM_TIPS_ROW.x(), ITEM_TIPS_ROW.w());
        add(new ToggleComponent(at(row), this.words.get("minuit.picker.item_tips"), on, set))
                .tooltip(this.words.get("minuit.picker.item_tips_tip"));
        return this;
    }

    /** The top of a page with {@code rows} optional rows under the accents. */
    public static int top(int screenHeight, int rows) {
        return (screenHeight - HEIGHT - rows * OPTION_ROW) / 2;
    }

    /** The next optional row's box, page coordinates, and counts it. */
    private Box optionRow(int x, int w) {
        Box row = new Box("option row", x, HEIGHT + this.rows * OPTION_ROW, w, 14);
        this.rows++;
        return row;
    }

    /**
     * Adds the "Background" row under the accents: the arrows and the name step through
     * {@link ScreenBackground#all()} from {@code id}, {@code set} saving each step. The setting is the player's
     * for the whole game, whichever mod's page shows it; place the page with {@link #top(int, int)}.
     */
    public ThemePickerPanel background(String id, Consumer<String> set) {
        Box row = optionRow(VARIANT_ROW.x(), VARIANT_ROW.w());
        this.backgroundLabel = new Box("background label", VARIANT_LABEL.x(), row.y() + 2, VARIANT_LABEL.w(), LINE);
        String[] at = {ScreenBackground.byId(id).id()};
        List<Consumer<Integer>> steps = new ArrayList<>(1);
        ButtonComponent name = new ButtonComponent(at(new Box("background", row.x() + 16, row.y(), row.w() - 32, 14)),
                backgroundName(at[0]), () -> steps.get(0).accept(1));
        Consumer<Integer> step = by -> {
            List<ScreenBackground> all = ScreenBackground.all();
            int i = 0;
            while (i < all.size() && !all.get(i).id().equals(at[0])) {
                i++;
            }
            at[0] = all.get(Math.floorMod((i == all.size() ? 0 : i) + by, all.size())).id();
            name.label = backgroundName(at[0]);
            set.accept(at[0]);
        };
        steps.add(step);
        add(new ButtonComponent(at(new Box("background prev", row.x(), row.y(), 14, 14)), "", () -> step.accept(-1))
                .icon(Icon.PREV));
        add(name);
        add(new ButtonComponent(at(new Box("background next", row.x() + row.w() - 14, row.y(), 14, 14)), "",
                () -> step.accept(1)).icon(Icon.NEXT));
        return this;
    }

    /**
     * Adds the "Export as resource pack" row under the accents: {@code export} writes the look on show (family,
     * chroma, variant, accent) as a pack for the host to save and report. Place the page with {@link #top(int, int)}.
     */
    public ThemePickerPanel exportPack(Consumer<ThemeChoice> export) {
        Box row = optionRow(VARIANT_ROW.x(), VARIANT_ROW.w());
        add(new ButtonComponent(at(row), this.words.get("minuit.picker.export"), () -> export.accept(this.current))
                .tooltip(this.words.get("minuit.picker.export_tip")));
        return this;
    }

    private String backgroundName(String id) {
        String key = "minuit.background." + id;
        String name = this.words.get(key);
        return name == null || name.equals(key) ? id : name;
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT + this.rows * OPTION_ROW + VERSION_ROW;
    }

    /** One card of a grid: the look it shows, its name, what a click picks. */
    private record Card(Theme look, String name, ThemeChoice choice) {
    }

    private void addFamilies() {
        List<Card> cards = new ArrayList<>();
        Card chosen = null;
        if (hasOwnLook()) {
            // A null choice is "no choice": the host clears the mod's entry.
            Card own = new Card(this.own, this.words.get("minuit.picker.family.default"), null);
            cards.add(own);
            if (this.current == null || this.ownFamily != null && this.current.ownChroma()
                    && chosenFamily() == this.ownFamily) {
                chosen = own;
            }
        }
        Card ownCard = chosen;
        for (ThemeFamily family : Themes.families(this.own.id())) {
            ThemeFamily.Chroma first = family.first();
            Card card = new Card(look(family, first), this.words.get(family.nameKey()),
                    new ThemeChoice(family.id(), first.id(), variant(), accent()));
            cards.add(card);
            if (ownCard == null && family == chosenFamily()) {
                chosen = card;
            }
        }
        scrolled(FAMILY_SCROLL, FAMILY_GRID, COLUMNS, cards, chosen, CARD_H, ROW_STEP,
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
        List<ThemeFamily.Chroma> chromas = new ArrayList<>();
        if (hasOwnLook()) {
            chromas.add(Themes.ownChroma(this.own));
        }
        chromas.addAll(family.chromas());
        for (ThemeFamily.Chroma chroma : chromas) {
            String name = ThemeChoice.OWN_CHROMA.equals(chroma.id()) ? Themes.ownName(this.own.id())
                    : this.words.get(chroma.nameKey());
            Card card = new Card(look(family, chroma), name,
                    new ThemeChoice(family.id(), chroma.id(), variant(), accent()));
            cards.add(card);
            if (chroma == chosenChroma(family)) {
                chosen = card;
            }
        }
        scrolled(CHROMA_SCROLL, CHROMA_GRID, CHIP_COLUMNS, cards, chosen, CHIP_H, CHIP_STEP,
                (d, theme, card, cell, glow, selected) -> ThemePreview.paintPalette(d, theme, card.look(), card.name(),
                        cell, glow, selected));
    }

    /** A grid of cards in a scroll box, opened scrolled to the chosen card. */
    private void scrolled(Box box, Box grid, int columns, List<Card> cards, Card chosen, int cellH, int step,
                          GridComponent.GlowingCell<Card> paint) {
        ScrollComponent scroll = add(new ScrollComponent(at(box)));
        scroll.bottomRoom = 4;
        scroll.step = step;
        GridComponent<Card> cells = scroll.add(new GridComponent<>(at(grid), columns, cellH, GAP, step - cellH, cards,
                GridComponent.glowing(paint), card -> this.pick.accept(card.choice())).selected(chosen));
        if (chosen != null) {
            Box cell = cells.cell(cards.indexOf(chosen));
            scroll.reveal(new Box("chosen", cell.x(), cell.y() - 2, cell.w(), cell.h() + 6));
        }
    }

    private void addVariants() {
        ThemeFamily family = chosenFamily();
        ThemeFamily.Chroma chroma = family == null ? null : chosenChroma(family);
        ThemeChoice.Variant[] variants = {ThemeChoice.Variant.LIGHT, ThemeChoice.Variant.DARK, ThemeChoice.Variant.GLASS,
                ThemeChoice.Variant.AUTO};
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
     * colour chip is the button's content, centred on its face.
     */
    private void addAccents() {
        Box row = at(ACCENT_ROW);
        boolean on = chosenFamily() != null;
        ButtonComponent none = add(new ButtonComponent(new Box("accent none", row.x(), row.y(), 2 * SWATCH + 4, SWATCH),
                this.words.get("minuit.picker.accent_none"), () -> this.pick.accept(this.current.withAccent(""))));
        none.selected(on && accent().isEmpty()).active(on);
        int x = row.x() + 2 * SWATCH + 8;
        for (Accent a : Accent.presets()) {
            ButtonComponent swatch = add(new ButtonComponent(new Box("accent " + a.id(), x, row.y(), SWATCH, SWATCH), "",
                    () -> this.pick.accept(this.current.withAccent(a.id()))));
            swatch.selected(on && a.id().equals(accent())).active(on)
                    .tooltip(this.words.get("minuit.picker.accent." + a.id()));
            // The chip rides the face (h - lip), so it centres on what reads as the button and sinks with it.
            swatch.content((dd, fx, fy, fw, fh, ink) -> {
                Palette p = this.theme.palette();
                int color = a.on(p);
                if (!swatch.active) {
                    color = Shapes.mix(color, p.disabled(), 0.6F);
                }
                int side = fw - 8;
                int cy = fy + (fh - side) / 2;
                dd.fill(fx + 4, cy, fx + 4 + side, cy + side, color);
            });
            this.swatches.add(swatch);
            x += SWATCH + 4;
        }
    }

    /** True when the mod registered a look of its own, one the player can go back to. */
    private boolean hasOwnLook() {
        return Themes.isRegistered(this.own.id());
    }

    /** The mod's own look as a choice: its family and its palette, as the mod made it. */
    private ThemeChoice ownDefault() {
        return new ThemeChoice(this.ownFamily.id(), ThemeChoice.OWN_CHROMA, ThemeChoice.Variant.AUTO);
    }

    /**
     * True for the choice that only restates the mod's own look; it is saved as no choice, so the mod keeps
     * following its registered theme (and stays marked own-look under a global choice).
     */
    private boolean isOwnDefault(ThemeChoice choice) {
        return choice != null && this.ownFamily != null && choice.equals(ownDefault());
    }

    /** The kit's default look, as the choice that selects it, while {@code own} still wears it and the player picked nothing. */
    private static ThemeChoice defaultChoice(Theme own) {
        boolean wearsDefault = !Themes.isRegistered(own.id());
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
        if (this.current.ownChroma() && hasOwnLook()) {
            return Themes.ownChroma(this.own);
        }
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
        frame.panel(d, p, this.left, this.top, WIDTH, height());
        Box header = at(HEADER);
        frame.header(d, p, header.x(), header.y(), header.w(), header.h());
        d.textIn(this.words.get("minuit.picker.title"),
                at(new Box("title", titleX(), TITLE.y(), TITLE.w() - (titleX() - TITLE.x()), LINE)), p.text());
        d.textIn(this.words.get("minuit.picker.family"), at(FAMILY_LABEL), p.textSoft());
        d.textIn(this.words.get("minuit.picker.variant"), at(VARIANT_LABEL), p.textSoft());
        d.textIn(this.words.get("minuit.picker.accent"), at(ACCENT_LABEL), p.textSoft());
        if (this.backgroundLabel != null) {
            d.textIn(this.words.get("minuit.picker.background"), at(this.backgroundLabel), p.textSoft());
        }
        if (chosenFamily() == null) {
            d.textIn(this.words.get("minuit.picker.own_note"), at(CHROMA_LABEL), p.textFaint());
        } else {
            d.textIn(this.words.get("minuit.picker.chroma"), at(CHROMA_LABEL), p.textSoft());
        }
        String version = "minuit " + dev.dryaides.minuit.internal.MinuitVersion.VERSION;
        d.text(version, this.left + WIDTH - 8 - d.width(version), this.top + height() - 6 - 8, p.textFaint());
    }

}
