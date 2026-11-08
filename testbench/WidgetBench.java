import dev.dryaides.minuit.AsyncImage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.FrameStyle;
import dev.dryaides.minuit.PlayerHeadComponent;
import dev.dryaides.minuit.StepperComponent;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.DropdownComponent;
import dev.dryaides.minuit.GridComponent;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.Icon;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.SpriteIcon;
import dev.dryaides.minuit.ListComponent;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.ScrollComponent;
import dev.dryaides.minuit.SectionComponent;
import dev.dryaides.minuit.SliderComponent;
import dev.dryaides.minuit.TabsComponent;
import dev.dryaides.minuit.TextFieldComponent;
import dev.dryaides.minuit.ToggleComponent;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.ThemeChoice;
import dev.dryaides.minuit.ThemeFamily;
import dev.dryaides.minuit.ThemePickerPanel;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.internal.ThemeFiles;
import dev.dryaides.minuit.Words;

import site.SiteScenes.Scene;
import static site.SiteScenes.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Shots and checks of the kit's standard widgets: tabs, text field, scroll container, tooltip, card
 * grid, each in two families, light and dark. Every drawn string must sit inside a widget's box;
 * the scripted input checks what a player would see happen.
 *
 * <p>Run with {@code bash testbench/run.sh}; shots go to the folder given as first argument.
 */
public final class WidgetBench {

    static final int W = 427;
    static final int H = 240;
    static final Words WORDS = (key, args) -> key;
    static final List<String> problems = new ArrayList<>();

    /** The kit's own theme files, read as the game reads them; run from the repository root. */
    static final Path THEMES = Path.of("core/src/main/resources/assets/minuit/minuit");

    /** @param args the shots folder, then optionally the looks to shoot, e.g. {@code glass/green,bubbly/lilac} */
    public static void main(String[] args) throws IOException {
        Themes.load(ThemeFiles.assemble(ThemeFiles.fromDirectory(THEMES, "minuit"), problems::add));
        File out = new File(args[0]);
        out.mkdirs();
        BenchDraw.McFont font = new BenchDraw.McFont();
        BaseComponent.clock = () -> now;
        int shots = 0;
        checks();
        String looks = args.length > 1 ? args[1] : "bedrock/stone,slate/teal";
        for (String look : looks.split(",")) {
            String familyId = look.split("/")[0];
            ThemeFamily family = Themes.family(null, familyId);
            ThemeFamily.Chroma chroma = family.chroma(look.split("/")[1]);
            for (ThemeChoice.Variant variant : List.of(ThemeChoice.Variant.LIGHT, ThemeChoice.Variant.DARK)) {
                Theme theme = new Theme(familyId, chroma.palette(variant), family.frame());
                String name = familyId + (chroma == family.first() ? "" : "-" + chroma.id()) + "-" + variant.id();
                shots += shot(font, tabsScene(theme), 40, 34, out, "tabs-" + name);
                shots += shot(font, fieldScene(theme), -1, -1, out, "field-" + name);
                shots += shot(font, scrollScene(theme), 120, 70, out, "scroll-" + name);
                shots += shot(font, controlsScene(theme, false), -1, -1, out, "controls-" + name);
                shots += shot(font, controlsScene(theme, false), 20, 38, out, "controls-hover-check-" + name);
                shots += shot(font, controlsScene(theme, false), 200, 66, out, "controls-hover-slider-" + name);
                shots += shot(font, controlsScene(theme, false), 60, 94, out, "controls-hover-dropdown-" + name);
                shots += shot(font, controlsScene(theme, true), -1, -1, out, "controls-open-" + name);
                shots += shot(font, tooltipScene(theme), 60, 34, out, "tooltip-" + name);
                shots += shot(font, gridScene(theme), 140, 50, out, "grid-" + name);
                shots += shot(font, rolesScene(theme), 60, 34, out, "roles-" + name);
                shots += shot(font, sectionScene(theme), -1, -1, out, "section-" + name);
                shots += shot(font, iconsScene(theme), -1, -1, out, "icons-" + name);
                shots += navShots(font, theme, out, name);
                shots += shot(font, mecchaScene(theme), 250, 34, out, "meccha-tabtip-" + name);
                shots += shot(font, mecchaScene(theme), 36, 62, out, "meccha-hover-" + name);
                shots += shot(font, roomsScene(theme), 120, 69, out, "meccha-rooms-" + name);
            }
        }
        System.out.println(shots + " shots, " + problems.size() + " problems");
        problems.stream().limit(30).forEach(System.out::println);
    }

    // --- scenes -------------------------------------------------------------------------------

    /** The kit's 9 px glyph, an item and an atlas sprite on a 20 px and a 14 px button each. */
    static Scene iconsScene(Theme theme) {
        Scene s = new Scene(theme, "Icons");
        s.put(new ButtonComponent(s.place(12, 30, 80, 20), "Leave", () -> { }).icon(Icon.EXIT));
        s.put(new ButtonComponent(s.place(98, 30, 80, 20), "Shop", () -> { }).icon(ItemView.of("minecraft:diamond")));
        s.put(new ButtonComponent(s.place(184, 30, 20, 20), "", () -> { }).icon(SpriteIcon.item("minecraft:item/filled_map")));
        s.put(new ButtonComponent(s.place(12, 58, 80, 14), "Leave", () -> { }).icon(Icon.EXIT));
        s.put(new ButtonComponent(s.place(98, 58, 80, 14), "Shop", () -> { }).icon(ItemView.of("minecraft:diamond")));
        s.put(new ButtonComponent(s.place(184, 58, 14, 14), "", () -> { }).icon(SpriteIcon.item("minecraft:item/filled_map")));
        s.put(new ButtonComponent(s.place(12, 80, 80, 20), "Token", () -> { }).icon(ItemView.of("minecraft:emerald")));
        return s;
    }

    /** What Meccha's room screens ask for: a disabled tab with its tip, steppers, a stepped slider, heads. */
    static Scene mecchaScene(Theme theme) {
        Scene s = new Scene(theme, "Room");
        TabsComponent tabs = s.put(new TabsComponent(s.place(12, 26, 276, 14), List.of("Browser", "Room", "Maps", "Editor"),
                1, i -> { }));
        tabs.disabled(3, true, "Creative mode or op only");
        s.put(new ButtonComponent(s.place(12, 56, 60, 14), "Start", () -> { }));
        s.put(new ButtonComponent(s.place(78, 56, 60, 14), "Leave", () -> { }).role(ButtonComponent.Role.DESTRUCTIVE));
        s.put(new StepperComponent(s.place(12, 78, 70, 14), 1, 10, 1, v -> { }).label(v -> "Rounds " + v));
        StepperComponent at = s.put(new StepperComponent(s.place(88, 78, 70, 14), 2, 16, 16, v -> { }));
        at.focused = true;
        s.put(StepperComponent.delta(s.place(164, 78, 70, 14), d -> { }).caption("90 s"));
        s.put(new SliderComponent(s.place(12, 100, 146, 12), 0, 300, 120, v -> { }).step(30)
                .label(v -> "Time " + v + "s"));
        AsyncImage steve = AsyncImage.of("minecraft:textures/entity/player/wide/steve.png", 64, 64);
        ListComponent<String> roster = s.put(new ListComponent<>(s.place(170, 100, 118, 66), 16,
                PlayerHeadComponent.row(n -> n.equals("Waiting") ? AsyncImage.pending() : steve, n -> n), n -> { }));
        roster.setItems(List.of("Steve", "Alex", "Waiting", "Notch"));
        s.put(new PlayerHeadComponent(s.place(12, 120, 32, 32), steve));
        s.put(new PlayerHeadComponent(s.place(50, 128, 16, 16), steve));
        s.put(new PlayerHeadComponent(s.place(72, 132, 12, 12), steve));
        return s;
    }

    /**
     * Meccha's room list: rows that hold a Join button, the hovered row glowing, a seeker stepper held
     * at its cap by a veto and written in the accent, Leave with the door icon.
     */
    static Scene roomsScene(Theme theme) {
        Scene s = new Scene(theme, "Rooms");
        ListComponent<String> rooms = s.put(new ListComponent<String>(s.place(12, 28, 200, 70), 16,
                (d, t, item, row, hovered, selected) -> d.textIn(item, new Box("name", row.x(), row.y() + 4, row.w() - 40, 9),
                        t.palette().text()), (item, index, mx, my) -> { }));
        rooms.setItems(List.of("Arena 1", "Castle", "Village", "Docks", "Library"));
        rooms.rowWidgets((item, index, row) -> List.of(new ButtonComponent(
                new Box("join", row.right() - 36, row.y() + 2, 36, 12), "Join", () -> { })));
        int players = 4;
        s.put(new StepperComponent(s.place(12, 104, 90, 14), 1, 16, 4, v -> { })
                .canStep((from, to) -> to <= players)
                .labelColor((p, v) -> v >= players ? p.accent() : p.text())
                .label(v -> "Seekers " + v));
        s.put(new ButtonComponent(s.place(12, 124, 70, 14), "Leave", () -> { }).icon(Icon.EXIT)
                .role(ButtonComponent.Role.DESTRUCTIVE));
        return s;
    }

    /**
     * The game's controls side by side: a checkbox off and on, a slider at the game's button height with
     * its value inside and a plain one, a closed dropdown; {@code open} opens the dropdown's list.
     */
    static Scene controlsScene(Theme theme, boolean open) {
        Scene s = new Scene(theme, "Controls");
        DropdownComponent mode = s.put(new DropdownComponent(s.place(12, 84, 130, 20), "Normal", (W - 300) / 2 + 12, 200,
                s::show));
        mode.lines = List.of(new DropdownComponent.Header("Difficulty"),
                entry("Peaceful", DropdownComponent.State.SELECTABLE), entry("Normal", DropdownComponent.State.SELECTED),
                entry("Hard", DropdownComponent.State.DISABLED));
        s.put(new ToggleComponent(s.place(12, 28, 130, 20), "Checkbox off", false, v -> { }));
        s.put(new ToggleComponent(s.place(150, 28, 130, 20), "Checkbox on", true, v -> { }));
        s.put(new SliderComponent(s.place(12, 56, 130, 20), 0, 100, 40, v -> { }).label(v -> "Volume: " + v + "%"));
        s.put(new SliderComponent(s.place(150, 56, 130, 20), 0, 8, 6, v -> { }));
        s.put(new ToggleComponent(s.place(150, 84, 130, 20), "Checkbox off", false, v -> { })).active = false;
        if (open) {
            mode.mouseClicked(mode.x + 4, mode.y + 4, 0);
        }
        return s;
    }

    /** A settings page: tabs, a toggle, a dropdown, a slider, a disabled button. */
    static Scene downloadsScene(Theme theme) {
        Scene s = new Scene(theme, "Settings");
        s.put(new TabsComponent(s.place(12, 28, 276, 14), List.of("Singleplayer", "Multiplayer"), 0, i -> { }));
        s.put(new ToggleComponent(s.place(12, 50, 200, 12), "Show coordinates", true, v -> { }));
        DropdownComponent model = s.put(new DropdownComponent(s.place(12, 70, 160, 14), "Normal", (W - 300) / 2 + 12, 276,
                s::show));
        model.lines = List.of(new DropdownComponent.Header("Difficulty"),
                entry("Peaceful", DropdownComponent.State.SELECTABLE), entry("Normal", DropdownComponent.State.SELECTED),
                entry("Hard", DropdownComponent.State.DISABLED));
        s.put(new ButtonComponent(s.place(180, 70, 108, 14), "Delete", () -> { }).active(false));
        s.put(new SliderComponent(s.place(12, 92, 200, 12), 0, 8, 4, v -> { }));
        s.put(new ButtonComponent(s.place(12, 150, 90, 16), "Done", () -> { }).style(ButtonComponent.Style.PRIMARY));
        return s;
    }

    static ThemePickerPanel themePage(Theme theme) {
        return new ThemePickerPanel(WORDS, ThemePickerPanel.left(W), ThemePickerPanel.top(H), theme, null, c -> { },
                c -> { }, () -> { }, () -> { });
    }

    /**
     * The focus ring in each family: the theme page and the settings page driven by keys only, a
     * shot after each step of the sequence.
     */
    static int navShots(BenchDraw.McFont font, Theme theme, File out, String name) {
        int shots = 0;
        ThemePickerPanel page = themePage(theme);
        String[] pageSteps = {"tab", "tab", "tab", "down", "right"};
        for (int i = 0; i < pageSteps.length; i++) {
            press(page, pageSteps[i]);
            shots += shot(font, page, -1, -1, out, "nav-theme-" + (i + 1) + "-" + pageSteps[i] + "-" + name);
        }
        String[] parts = name.split("-");
        ThemeFamily fam = Themes.family("minuit", parts[0]);
        if (fam != null) {
            ThemeChoice picked = new ThemeChoice(fam.id(), fam.first().id(),
                    ThemeChoice.Variant.valueOf(parts[1].toUpperCase(java.util.Locale.ROOT)), "");
            shots += shot(font, new ThemePickerPanel(WORDS, ThemePickerPanel.left(W), ThemePickerPanel.top(H), theme,
                    picked, c -> { }, c -> { }, () -> { }, () -> { }), -1, -1, out, "theme-picked-" + name);
        }
        Scene downloads = downloadsScene(theme);
        String[] dlSteps = {"tab", "right", "tab", "tab", "enter", "down", "down"};
        for (int i = 0; i < dlSteps.length; i++) {
            press(downloads, dlSteps[i]);
            shots += shot(font, downloads, -1, -1, out, "nav-downloads-" + (i + 1) + "-" + dlSteps[i] + "-" + name);
        }
        return shots;
    }

    static void press(PanelComponent panel, String step) {
        switch (step) {
            case "tab" -> panel.keyPressed(InputKey.TAB, false);
            case "down" -> panel.keyPressed(InputKey.DOWN, false);
            case "right" -> panel.keyPressed(InputKey.RIGHT, false);
            case "enter" -> panel.keyPressed(InputKey.ENTER, false);
            default -> throw new IllegalArgumentException(step);
        }
    }

    // --- checks -------------------------------------------------------------------------------

    static void expect(String what, Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            problems.add(what + ": expected " + expected + ", got " + actual);
        }
    }

    static void checks() {
        Theme theme = Themes.byId("");
        // Tabs: a click on the third tab shows it and tells the panel.
        Scene tabs = new Scene(theme, "t");
        int[] picked = {-1};
        TabsComponent row = tabs.put(new TabsComponent(tabs.place(12, 28, 276, 14), List.of("A", "B", "C"), 0, i -> picked[0] = i));
        tabs.mouseClicked(row.x + row.w - 5, row.y + 5, 0);
        expect("tab clicked", 2, picked[0]);
        expect("tab shown", 2, row.index());

        // Text field: a typed character replaces the selection; the limit holds; Escape gives back the keyboard.
        Scene f = new Scene(theme, "f");
        List<String> changes = new ArrayList<>();
        TextFieldComponent field = f.put(new TextFieldComponent(f.place(12, 28, 200, 16), "", changes::add).maxLength(5));
        f.charTyped('x');
        expect("keys before a click go nowhere", "", field.text());
        f.mouseClicked(field.x + 3, field.y + 3, 0);
        expect("click focuses", field, f.focus());
        type(f, "abc");
        f.keyPressed(InputKey.LEFT, false);
        f.keyPressed(InputKey.LEFT, true);
        type(f, "X");
        expect("typing replaces the selection", "aXc", field.text());
        type(f, "defg");
        expect("typing stops at the length limit", "aXdec", field.text());
        f.keyPressed(InputKey.SELECT_ALL, false);
        f.keyPressed(InputKey.CUT, false);
        expect("cut empties", "", field.text());
        f.keyPressed(InputKey.PASTE, false);
        expect("paste brings it back", "aXdec", field.text());
        expect("one change per edit", 8, changes.size());
        f.keyPressed(InputKey.BACKSPACE, false);
        f.keyPressed(InputKey.HOME, false);
        f.keyPressed(InputKey.DELETE, false);
        expect("backspace and delete", "Xde", field.text());
        expect("escape is used", true, f.keyPressed(InputKey.ESCAPE, false));
        expect("escape unfocuses", null, f.focus());
        expect("escape then closes the screen", false, f.keyPressed(InputKey.ESCAPE, false));
        f.mouseClicked(field.x + 3, field.y + 3, 0);
        f.mouseClicked(field.x + 3, field.y + 60, 0);
        expect("click elsewhere unfocuses", false, field.focused);

        // Scroll: wheel moves by a step a notch, a child that uses the wheel goes first, clicks reach scrolled children.
        Scene sc = new Scene(theme, "s");
        ScrollComponent scroll = sc.put(new ScrollComponent(sc.place(12, 28, 276, 100)));
        int[] hits = new int[12];
        for (int i = 0; i < 12; i++) {
            int n = i;
            scroll.add(new ButtonComponent(sc.place(16, 32 + i * 18, 200, 14), "b", () -> hits[n]++)).tooltip("tip " + i);
        }
        ListComponent<String> inner = scroll.add(new ListComponent<>(sc.place(16, 32 + 12 * 18, 200, 30), 10,
                (d, t, item, r, h, sel) -> { }, x -> { }));
        inner.setItems(List.of("1", "2", "3", "4", "5", "6"));
        expect("content height", 4 + 12 * 18 + 30, scroll.contentHeight());
        sc.mouseScrolled(scroll.x + 20, scroll.y + 20, -3);
        expect("three notches", 30, scroll.scroll());
        int rowThree = scroll.y + 4 + 3 * 18 - 30 + 5;
        sc.mouseClicked(scroll.x + 20, rowThree, 0);
        expect("click reaches the row under the mouse after scrolling", 1, hits[3]);
        expect("tooltip of a scrolled child", List.of("tip 3"), sc.tooltipAt(scroll.x + 20, rowThree));
        scroll.setScroll(10_000);
        expect("scroll stops at the end", scroll.contentHeight() - scroll.h, scroll.scroll());
        double before = inner.scroll();
        sc.mouseScrolled(inner.x + 5, inner.y + 5, -1);
        expect("the inner list takes the wheel first", true, inner.scroll() > before);
        int end = scroll.scroll();
        sc.mouseClicked(scroll.x + scroll.w - 3, scroll.y + scroll.h - 6, 0);
        sc.mouseDragged(scroll.x + scroll.w - 3, scroll.y + 6, 0, 0, -scroll.h);
        sc.mouseReleased(scroll.x + scroll.w - 3, scroll.y + 6, 0);
        expect("dragging the thumb to the top", 0, scroll.scroll());
        expect("drag started at the end", true, end > 0);

        // Section: a header click folds it, the height follows, a folded child gets no click.
        Scene se = new Scene(theme, "se");
        int[] pressed = {0};
        int[] toggled = {0};
        SectionComponent sec = se.put(new SectionComponent(se.place(12, 28, 200, 0), "S", true).onToggle(() -> toggled[0]++));
        sec.add(new ButtonComponent(se.place(16, 46, 100, 16), "b", () -> pressed[0]++));
        expect("open height", 36, sec.h);
        se.mouseClicked(sec.x + 20, sec.y + 4, 0);
        expect("folded height", SectionComponent.HEADER, sec.h);
        expect("fold told the panel", 1, toggled[0]);
        se.mouseClicked(sec.x + 20, sec.y + 22, 0);
        expect("a folded child gets no click", 0, pressed[0]);
        se.mouseClicked(sec.x + 20, sec.y + 4, 0);
        se.mouseClicked(sec.x + 20, sec.y + 22, 0);
        expect("an open child gets the click", 1, pressed[0]);

        // Grid: a click on a cell gives its item; the gap between cells holds none.
        Scene g = new Scene(theme, "g");
        List<String> clicked = new ArrayList<>();
        GridComponent<String> grid = g.put(new GridComponent<>(g.place(12, 28, 276, 0), 3, 40, 8, 8, List.of("a", "b", "c", "d", "e"),
                (d, t, item, cell, h, sel) -> { }, clicked::add));
        expect("two rows", 88, grid.h);
        Box e = grid.cell(4);
        g.mouseClicked(e.x() + 2, e.y() + 2, 0);
        g.mouseClicked(grid.cell(0).right() + 3, grid.cell(0).y() + 5, 0);
        expect("cell clicks", List.of("e"), clicked);

        navChecks(theme);
    }

    /** Keyboard navigation as a player uses it: reading order, disabled skipped, Enter and Space, Escape back. */
    static void navChecks(Theme theme) {
        Scene n = new Scene(theme, "n");
        int[] presses = new int[2];
        ToggleComponent below = n.put(new ToggleComponent(n.place(12, 52, 120, 12), "C", false, v -> { }));
        n.put(new ButtonComponent(n.place(140, 28, 60, 14), "B", () -> presses[1]++).active(false));
        ButtonComponent first = n.put(new ButtonComponent(n.place(12, 28, 60, 14), "A", () -> presses[0]++));
        expect("no stop before a key", null, n.cursor());
        n.keyPressed(InputKey.TAB, false);
        expect("Tab starts top left, whatever the add order", first, n.cursor());
        n.keyPressed(InputKey.ENTER, false);
        expect("Enter presses the button", 1, presses[0]);
        n.keyPressed(InputKey.TAB, false);
        expect("Tab skips the disabled button", below, n.cursor());
        n.keyPressed(InputKey.SPACE, false);
        expect("Space flips the toggle", true, below.value);
        n.keyPressed(InputKey.TAB, false);
        expect("Tab wraps to the first stop", first, n.cursor());
        n.keyPressed(InputKey.TAB, true);
        expect("Shift+Tab goes back", below, n.cursor());
        expect("Escape without a back arrow leaves the key to the screen", false, n.keyPressed(InputKey.ESCAPE, false));
        int[] backs = {0};
        n.back(() -> backs[0]++);
        n.keyPressed(InputKey.ESCAPE, false);
        expect("Escape goes back", 1, backs[0]);
        n.mouseClicked(first.x + 2, first.y + 2, 0);
        expect("a click puts the keyboard where it clicked", first, n.cursor());

        // A field reached by Tab takes the typing; Up leaves it, Left stays in it.
        Scene f = new Scene(theme, "f");
        ButtonComponent before = f.put(new ButtonComponent(f.place(12, 28, 60, 14), "A", () -> { }));
        TextFieldComponent field = f.put(new TextFieldComponent(f.place(12, 52, 120, 16), "", t -> { }));
        f.keyPressed(InputKey.TAB, false);
        f.keyPressed(InputKey.TAB, false);
        type(f, "hi");
        expect("typing after Tab into a field", "hi", field.text());
        f.keyPressed(InputKey.LEFT, false);
        expect("Left moves the text cursor, not the focus", field, f.cursor());
        f.keyPressed(InputKey.UP, false);
        expect("Up leaves the field", before, f.cursor());
        expect("the field lets the keyboard go", false, field.focused);

        // A grid in a scroll box: the arrows walk the cards, the box follows, past the last row the keyboard leaves.
        Scene g = new Scene(theme, "g");
        List<String> picked = new ArrayList<>();
        ScrollComponent scroll = g.put(new ScrollComponent(g.place(12, 28, 276, 60)));
        scroll.add(new GridComponent<>(g.place(16, 32, 250, 0), 3, 36, 8, 8, List.of("a", "b", "c", "d", "e", "f", "g"),
                (d, t, item, cell, h, sel) -> { }, picked::add));
        ButtonComponent after = g.put(new ButtonComponent(g.place(12, 150, 60, 14), "after", () -> { }));
        g.keyPressed(InputKey.TAB, false);
        g.keyPressed(InputKey.DOWN, false);
        g.keyPressed(InputKey.DOWN, false);
        g.keyPressed(InputKey.ENTER, false);
        expect("two rows down from the first card", List.of("g"), picked);
        expect("the scroll box shows the third row", true, scroll.scroll() > 0);
        g.keyPressed(InputKey.DOWN, false);
        expect("past the last row the keyboard leaves the grid", after, g.cursor());

        // A dropdown: Enter opens it, Down skips what cannot be clicked, Enter picks, Escape only closes the list.
        Scene dd = new Scene(theme, "d");
        List<String> chosen = new ArrayList<>();
        dd.put(new DropdownComponent(dd.place(12, 28, 120, 14), "pick", (W - 300) / 2 + 12, 200, dd::show)).lines = List.of(
                new DropdownComponent.Header("h"),
                new DropdownComponent.Entry("now", "", "", "", DropdownComponent.State.SELECTED, -1F, List.of(), () -> chosen.add("now")),
                new DropdownComponent.Entry("off", "", "", "", DropdownComponent.State.DISABLED, -1F, List.of(), () -> chosen.add("off")),
                new DropdownComponent.Entry("other", "", "", "", DropdownComponent.State.SELECTABLE, -1F, List.of(),
                        () -> chosen.add("other")));
        dd.keyPressed(InputKey.TAB, false);
        dd.keyPressed(InputKey.ENTER, false);
        expect("Enter opens the list", true, dd.overlay() != null);
        expect("Escape closes the list and keeps the screen", true, dd.keyPressed(InputKey.ESCAPE, false));
        expect("the list is closed", null, dd.overlay());
        dd.keyPressed(InputKey.SPACE, false);
        dd.keyPressed(InputKey.DOWN, false);
        dd.keyPressed(InputKey.ENTER, false);
        expect("Down lands on the only clickable entry", List.of("other"), chosen);
        expect("picking closes the list", null, dd.overlay());

        // A slider takes Left and Right and the ends.
        Scene sl = new Scene(theme, "s");
        int[] value = {5};
        SliderComponent slider = sl.put(new SliderComponent(sl.place(12, 28, 200, 12), 0, 10, 5, v -> value[0] = v));
        sl.keyPressed(InputKey.TAB, false);
        sl.keyPressed(InputKey.RIGHT, false);
        sl.keyPressed(InputKey.RIGHT, false);
        expect("Right raises the slider by one a press", 7, value[0]);
        sl.keyPressed(InputKey.END, false);
        expect("End goes to the top", 10, slider.value);
    }

    // --- shots --------------------------------------------------------------------------------

    /**
     * The room a family may draw outside a button (FrameStyle.bleed): neighbours that share a row or a
     * column stand at least BLEED_AXIS apart, and each keeps BLEED_SIDE inside the panel.
     */
    static void bleedRoom(PanelComponent panel, String name) {
        List<BaseComponent> raised = new ArrayList<>();
        for (BaseComponent w : panel.widgets()) {
            if (w instanceof ScrollComponent scroll) {
                scroll.children().stream().filter(c -> bleeds(c) && c.box().y() >= scroll.y && c.box().bottom() <= scroll.y + scroll.h)
                        .forEach(raised::add);
            } else if (bleeds(w)) {
                raised.add(w);
            }
        }
        Box frame = panel.bounds();
        for (int i = 0; i < raised.size(); i++) {
            Box a = raised.get(i).box();
            if (!raised.get(i).visible) {
                continue;
            }
            int edge = Math.min(Math.min(a.x() - frame.x(), a.y() - frame.y()), Math.min(frame.right() - a.right(), frame.bottom() - a.bottom()));
            if (edge < FrameStyle.BLEED_SIDE) {
                problems.add(name + ": " + a.name() + " " + edge + " px from the panel edge");
            }
            for (int j = i + 1; j < raised.size(); j++) {
                Box b = raised.get(j).box();
                boolean row = a.y() < b.bottom() && b.y() < a.bottom();
                boolean column = a.x() < b.right() && b.x() < a.right();
                int gap = row == column ? Integer.MAX_VALUE : row ? Math.max(b.x() - a.right(), a.x() - b.right())
                        : Math.max(b.y() - a.bottom(), a.y() - b.bottom());
                if (raised.get(j).visible && gap < FrameStyle.BLEED_AXIS) {
                    problems.add(name + ": " + a + " and " + b + " " + gap + " px apart");
                }
            }
        }
    }

    static boolean bleeds(BaseComponent w) {
        return w instanceof ButtonComponent || w instanceof DropdownComponent || w instanceof TabsComponent
                || w instanceof dev.dryaides.minuit.ToggleGroupComponent;
    }

    static long now = 10_000;

    static int shot(BenchDraw.McFont font, PanelComponent panel, int mxRel, int myRel, File dir, String name) {
        int mx = mxRel < 0 ? -100 : (W - 300) / 2 + mxRel;
        int my = myRel < 0 ? -100 : (H - 180) / 2 + myRel;
        BenchDraw.MockDraw d = null;
        // Six frames 200 ms apart: past the tooltip delay, and on a lit phase of the cursor blink.
        for (int frame = 0; frame < 6; frame++) {
            now += 200;
            d = new BenchDraw.MockDraw(font, W, H);
            d.backdrop();
            panel.draw(d, mx, my, 1F);
            panel.drawTooltip(d, mx, my);
        }
        bleedRoom(panel, name);
        List<Box> boxes = panel.textBoxes();
        for (BenchDraw.Drawn text : d.texts) {
            boolean inside = boxes.stream().anyMatch(box -> text.x() >= box.x() - 0.01 && text.y() >= box.y() - 2.01
                    && text.x() + text.width() <= box.right() + 0.01 && text.y() + text.height() <= box.bottom() + 2.01);
            if (!inside) {
                problems.add(name + ": \"" + text.text() + "\" outside");
            }
        }
        if (!font.missing.isEmpty()) {
            problems.add(name + ": missing glyphs " + font.missing);
        }
        d.save(new File(dir, name + ".png"));
        return 1;
    }
}
