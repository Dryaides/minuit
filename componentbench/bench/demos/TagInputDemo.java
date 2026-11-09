package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.BenchAccess;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.SliderComponent;
import dev.dryaides.minuit.TagInputComponent;
import dev.dryaides.minuit.TextFieldComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The tag field, driven by scripted input.
 */
public final class TagInputDemo implements Demo {
    private static final int W = 200;
    private static final int H = 82;
    private static final int FX = 8;
    private static final int FY = 8;
    private static final int FW = 180;
    /** Three whole lines. */
    private static final int FH = 44;
    private static final int OPENED = 150;
    private static final int HELD_REPEAT = 30;
    private static final int PAUSE = 400;
    private static final List<String> THREE = List.of("stone", "oak planks", "glass");
    private static final List<String> MANY = List.of("stone", "cobblestone", "sandstone", "oak planks", "spruce planks",
            "birch planks", "glass", "tinted glass", "dirt", "gravel", "diamond ore", "iron ore", "gold ore", "coal ore");
    private static final List<String> KNOWN = List.of("Stone", "Cobblestone", "Sandstone", "Stone Bricks", "Dirt",
            "Diamond Ore", "Diamond Block", "Oak Planks", "Spruce Planks", "Birch Planks", "Émeraude", "Glass");
    private static final String LONG = "a very long tag that cannot possibly fit in one line of this box at all";

    /** A clipboard for the script: the real one is the game's. */
    private static final class Fake implements TextFieldComponent.Clipboard {
        String value = "";
        @Override public String get() { return value; }
        @Override public void set(String text) { value = text; }
    }

    /** A scene with a field, what its callbacks saw, and the line of values beneath. */
    private static final class Rig {
        final Scene s;
        final List<String> events = new ArrayList<>();
        String note = "";

        Rig(Scene s) {
            this.s = s;
            s.caption(() -> note.isEmpty() ? String.join(" ", events) : note);
        }

        TagInputComponent field() {
            return field(new Box("t", FX, FY, FW, FH));
        }

        TagInputComponent field(Box box) {
            return s.add(new TagInputComponent(box, s.panel())
                    .placeholder("Add a tag")
                    .onAdd(v -> events.add("+" + v))
                    .onRemove(v -> events.add("-" + v))
                    .onRefuse((v, why) -> events.add(why + ":" + v)));
        }

        TagInputComponent with(List<String> tags) {
            TagInputComponent c = field();
            c.setTags(tags);
            return c;
        }

        void clickChip(TagInputComponent c, int index) {
            Box b = BenchAccess.chipBox(c, index);
            s.click(b.x() + 4, b.y() + 6);
        }

        void clickClose(TagInputComponent c, int index) {
            Box b = BenchAccess.closeBox(c, index);
            s.click(b.x() + b.w() / 2, b.y() + b.h() / 2);
        }

        void clickRow(TagInputComponent c, int row) {
            Box b = BenchAccess.rowBox(c, row);
            s.click(b.x() + 10, b.y() + 4);
        }

        /** Lets a refusal's flash end, and the pause that protects chips from a held Backspace. */
        void pause() {
            s.waitMs(PAUSE);
        }
    }

    private static List<String> tags(String... values) {
        return Arrays.asList(values);
    }

    private static List<String> numbered(int count) {
        List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add("tag" + i);
        }
        return out;
    }

    @Override
    public void states(Stage stage) {
        looks(stage);
        adding(stage);
        duplicates(stage);
        limit(stage);
        validation(stage);
        removing(stage);
        keyboard(stage);
        clipboard(stage);
        scrolling(stage);
        suggestions(stage);
        edges(stage);
    }

    private void looks(Stage stage) {
        stage.state("default", W, 32, s -> {
            TagInputComponent c = s.add(new TagInputComponent(new Box("t", FX, FY, FW, 16), s.panel()).placeholder("Add a tag"));
        });

        stage.state("beside button slider field", W, 100, s -> {
            TagInputComponent c = s.add(new TagInputComponent(new Box("t", FX, 8, FW, 30), s.panel()));
            c.setTags(THREE);
            s.add(new TextFieldComponent(new Box("f", FX, 42, FW, 16), "A text field", v -> { }));
            s.add(new ButtonComponent(new Box("b", FX, 62, 80, 16), "Button", () -> { }));
            s.add(new SliderComponent(new Box("s", 92, 62, 96, 16), 0, 100, 40, v -> { }));
            s.add(new ButtonComponent(new Box("p", FX, 80, 80, 16), "Primary", () -> { }).role(ButtonComponent.Role.PRIMARY));
        });

        stage.state("chips at rest", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            Box a = BenchAccess.chipBox(c, 0);
            Box b = BenchAccess.chipBox(c, 1);
        });

        stage.state("one line box", W, 32, s -> {
            TagInputComponent c = s.add(new TagInputComponent(new Box("t", FX, FY, FW, 16), s.panel()));
            c.setTags(tags("a", "bb"));
            Box b = BenchAccess.chipBox(c, 0);
        });

        stage.state("focused by click", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(tags("stone"));
            s.click(FX + 100, FY + 8);
            s.settle(0);
        });

        stage.state("placeholder with caret", W, 32, s -> {
            TagInputComponent c = s.add(new TagInputComponent(new Box("t", FX, FY, FW, 16), s.panel()).placeholder("Add a tag"));
            s.click(FX + 40, FY + 8);
            s.settle(0);
        });

        stage.state("focused by tab shows the ring", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            s.key(InputKey.TAB);
            s.settle(0);
        });

        stage.state("wrapped on three lines", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(MANY.subList(0, 5));
        });

        stage.state("hover chip", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            Box b = BenchAccess.chipBox(c, 1);
            s.move(b.x() + 6, b.y() + 6);
        });

        stage.state("hover chip beside a selected one", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            r.clickChip(c, 0);
            Box b = BenchAccess.chipBox(c, 1);
            s.move(b.x() + 6, b.y() + 6);
        });

        stage.state("hover x", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            Box b = BenchAccess.closeBox(c, 1);
            s.move(b.x() + 5, b.y() + 6);
        });

        stage.state("x held", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            Box b = BenchAccess.closeBox(c, 1);
            s.press(b.x() + 5, b.y() + 6);
        });

        stage.state("selected with focus", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            r.clickChip(c, 1);
        });

        stage.state("selected keeps showing without focus", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            r.clickChip(c, 1);
            s.focus(null);
        });

        stage.state("disabled ignores input", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            r.clickChip(c, 1);
            c.active(false);
            r.clickClose(c, 0);
            r.clickChip(c, 2);
            s.click(FX + 150, FY + 30);
            s.focus(c);
            s.type("x");
            s.key(InputKey.BACKSPACE);
            s.key(InputKey.ENTER);
        });

        stage.state("disabled with a placeholder", W, 32, s -> {
            TagInputComponent c = s.add(new TagInputComponent(new Box("t", FX, FY, FW, 16), s.panel()).placeholder("Add a tag")
                    .active(false));
        });

        stage.state("disabled wheel still scrolls", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(MANY);
            c.active(false);
            s.scroll(FX + 40, FY + 20, -1);
        });
    }

    private void adding(Stage stage) {
        stage.state("enter adds", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field();
            s.focus(c);
            s.type("stone");
            s.key(InputKey.ENTER);
            s.settle(0);
        });

        stage.state("comma adds", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field();
            s.focus(c);
            s.type("stone,oak planks,");
            s.type("gl");
            s.settle(0);
        });

        stage.state("own separators", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().separators(" ;");
            s.focus(c);
            s.type("one two;three ");
            s.type("a,b");
        });

        stage.state("blank and spaces add nothing", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field();
            s.focus(c);
            s.key(InputKey.ENTER);
            s.type(",,");
            s.type("   ");
            s.key(InputKey.ENTER);
        });

        stage.state("text is cleaned", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field();
            c.setTags(tags("  two   spaces  ", "se§ction", "tab\tin", null, ""));
            s.focus(c);
            s.type("  a  b ");
            s.key(InputKey.ENTER);
        });

        stage.state("maxLength stops typing", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().maxLength(5);
            s.focus(c);
            s.type("abcdefgh");
            s.key(InputKey.ENTER);
            c.addTag("123456789");
        });

        stage.state("leaving commits", W, 106, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field();
            ButtonComponent next = s.add(new ButtonComponent(new Box("b", FX, 56, 80, 20), "Next", () -> { }));
            s.focus(c);
            s.type("stone");
            s.key(InputKey.TAB); // a panel's Tab starts from its first stop, which is this field
            s.key(InputKey.TAB);
        });

        stage.state("leaving keeps with commitOnLeave off", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().commitOnLeave(false);
            s.focus(c);
            s.type("stone");
            s.focus(null);
        });

        stage.state("addTag and removeTag", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            c.addTag("dirt");
            c.addTag("DIRT");
            c.addTag(" ");
            c.addTag(null);
            c.removeTag("Stone");
            c.removeTag("nope");
        });

        stage.state("onChange sees the whole list", W, H, s -> {
            Rig r = new Rig(s);
            List<List<String>> seen = new ArrayList<>();
            TagInputComponent c = r.field().onChange(seen::add);
            s.focus(c);
            s.type("a,b,");
            r.pause();
            s.key(InputKey.BACKSPACE);
        });
    }

    private void duplicates(Stage stage) {
        stage.state("duplicate mid shake", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            s.focus(c);
            s.type("GLASS");
            s.key(InputKey.ENTER);
            s.waitMs(32);
            s.settle(0);
        });

        stage.state("duplicate flash ends", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            s.focus(c);
            s.type("stone,");
            s.waitMs(PAUSE);
            s.settle(0);
        });

        stage.state("duplicate under reduced motion", W, H, s -> {
            Rig r = new Rig(s);
            s.paint((d, t) -> BaseComponent.reducedMotion = true);
            TagInputComponent c = r.with(THREE);
            s.paint((d, t) -> BaseComponent.reducedMotion = false);
            s.focus(c);
            BaseComponent.reducedMotion = true;
            try {
                s.type("stone");
                s.key(InputKey.ENTER);
                s.waitMs(64);
                s.settle(0);
            } finally {
                BaseComponent.reducedMotion = false;
            }
        });

        stage.state("case sensitive allows both", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().caseSensitive(true);
            s.focus(c);
            s.type("Stone,stone,Stone,");
            s.waitMs(PAUSE);
            s.settle(0);
        });

        stage.state("duplicate out of view scrolls to it", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(MANY);
            s.focus(c);
            s.scroll(FX + 10, FY + 20, -50);
            s.type("stone");
            s.key(InputKey.ENTER);
            s.waitMs(32);
            s.settle(0);
        });
    }

    private void limit(Stage stage) {
        stage.state("count shows the limit", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().maxTags(5);
            c.setTags(THREE);
        });

        stage.state("full refuses with a shake", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().maxTags(3);
            c.setTags(THREE);
            s.focus(c);
            s.type("x");
            s.waitMs(32);
            s.settle(0);
        });

        stage.state("full under reduced motion", W, H, s -> {
            Rig r = new Rig(s);
            s.paint((d, t) -> BaseComponent.reducedMotion = true);
            TagInputComponent c = r.field().maxTags(3);
            s.paint((d, t) -> BaseComponent.reducedMotion = false);
            c.setTags(THREE);
            s.focus(c);
            BaseComponent.reducedMotion = true;
            try {
                s.type("x");
                s.settle(0);
                s.waitMs(16);
            } finally {
                BaseComponent.reducedMotion = false;
            }
        });

        stage.state("a free place takes again", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().maxTags(3);
            c.setTags(THREE);
            s.focus(c);
            r.pause();
            s.key(InputKey.BACKSPACE);
            s.type("new,");
            s.settle(0);
        });

        stage.state("count hidden", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().maxTags(3).showCount(false);
            c.setTags(THREE);
            s.focus(c);
            s.type("x");
            r.pause();
        });

        stage.state("tags past a lowered limit stay", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            c.maxTags(2);
            c.addTag("more");
        });
    }

    private void validation(Stage stage) {
        stage.state("invalid look", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().validator(t -> t.contains(":") ? null : "Needs a namespace");
            c.setTags(tags("minecraft:stone", "oak", "mymod:gem"));
        });

        stage.state("invalid typed stays with the look", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().validator(t -> t.contains(":") ? null : "Needs a namespace");
            s.focus(c);
            s.type("oak,");
            s.settle(0);
        });

        stage.state("invalid selected", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().validator(t -> t.contains(":") ? null : "");
            c.setTags(tags("a:b", "oak", "c:d"));
            r.clickChip(c, 1);
        });

        stage.state("invalid hover", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().validator(t -> t.contains(":") ? null : "");
            c.setTags(tags("a:b", "oak", "c:d"));
            Box b = BenchAccess.chipBox(c, 1);
            s.move(b.x() + 6, b.y() + 6);
        });

        stage.state("invalid reason as a tooltip", W, 110, s -> {
            TagInputComponent c = s.add(new TagInputComponent(new Box("t", FX, 8, FW, 30), s.panel())
                    .validator(t -> t.contains(":") ? null : "Needs a namespace"));
            c.setTags(tags("a:b", "oak"));
            Box b = BenchAccess.chipBox(c, 1);
            s.move(b.x() + 6, b.y() + 6);
            s.settle(1500);
        });

        stage.state("rejectInvalid refuses", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().rejectInvalid(true).validator(t -> t.contains(":") ? null : "No");
            s.focus(c);
            s.type("oak");
            s.key(InputKey.ENTER);
            s.waitMs(32);
            s.settle(0);
        });

        stage.state("validator set later judges again", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(tags("a", "bb", "ccc"));
            c.validator(t -> t.length() < 2 ? "Too short" : null);
        });
    }

    private void removing(Stage stage) {
        stage.state("backspace removes the last", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            s.focus(c);
            r.pause();
            s.key(InputKey.BACKSPACE);
            s.settle(0);
        });

        stage.state("backspace then removes the selected", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(tags("a", "b", "c", "d"));
            r.clickChip(c, 1);
            r.pause();
            s.key(InputKey.BACKSPACE);
            s.key(InputKey.BACKSPACE);
            s.settle(0);
        });

        stage.state("confirmBackspace selects first", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().confirmBackspace(true);
            c.setTags(THREE);
            s.focus(c);
            r.pause();
            s.key(InputKey.BACKSPACE);
            s.key(InputKey.BACKSPACE);
            s.settle(0);
        });

        stage.state("backspace erases text before chips", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            s.focus(c);
            s.type("ab");
            s.key(InputKey.BACKSPACE);
            s.key(InputKey.BACKSPACE);
            for (int i = 0; i < 5; i++) {
                s.waitMs(HELD_REPEAT);
                s.key(InputKey.BACKSPACE);
            }
            s.waitMs(PAUSE);
            s.key(InputKey.BACKSPACE);
            s.settle(0);
        });

        stage.state("click x removes", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            r.clickClose(c, 1);
            s.settle(0);
        });

        stage.state("x released elsewhere keeps", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            Box b = BenchAccess.closeBox(c, 0);
            s.drag(b.x() + 5, b.y() + 6, b.x() + 60, b.y() + 30);
        });

        stage.state("x beside the selection keeps it right", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(tags("a", "b", "c"));
            r.clickChip(c, 2);
            r.clickClose(c, 0);
        });

        stage.state("delete removes and goes on", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(tags("a", "b", "c"));
            r.clickChip(c, 1);
            s.key(InputKey.DELETE);
            s.key(InputKey.DELETE);
            s.settle(0);
        });

        stage.state("all removed shows the placeholder", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(tags("a"));
            s.focus(c);
            r.pause();
            s.key(InputKey.BACKSPACE);
            s.key(InputKey.BACKSPACE);
            s.settle(0);
        });
    }

    private void keyboard(Stage stage) {
        stage.state("left walks the chips", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            s.focus(c);
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT);
            s.settle(0);
        });

        stage.state("left moves in the text first", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(tags("a"));
            s.focus(c);
            s.type("xy");
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT);
            s.key(InputKey.RIGHT);
            s.settle(0);
        });

        stage.state("right returns to the text", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            r.clickChip(c, 1);
            s.key(InputKey.RIGHT);
            s.key(InputKey.RIGHT);
            s.settle(0);
        });

        stage.state("home and end", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            s.focus(c);
            s.type("xy");
            s.key(InputKey.HOME);
            s.key(InputKey.HOME);
            s.key(InputKey.END);
            s.settle(0);
        });

        stage.state("up and down across lines", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(MANY.subList(0, 5));
            s.focus(c);
            s.key(InputKey.UP);
            s.key(InputKey.UP);
            s.key(InputKey.DOWN);
            s.settle(0);
        });

        stage.state("down past the last line returns to the text", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            r.clickChip(c, 0);
            s.key(InputKey.DOWN);
            s.settle(0);
        });

        stage.state("typing leaves the chip", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            r.clickChip(c, 0);
            s.type("x");
            s.settle(0);
        });

        stage.state("escape and enter leave the chip", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            r.clickChip(c, 0);
            s.key(InputKey.ESCAPE);
            r.clickChip(c, 0);
            s.key(InputKey.ENTER);
            s.key(InputKey.ESCAPE);
        });

        stage.state("text selection", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(tags("a"));
            s.focus(c);
            s.type("hello");
            s.key(InputKey.LEFT, true);
            s.key(InputKey.LEFT, true);
            s.settle(0);
        });

        stage.state("select all then type", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(tags("a"));
            s.focus(c);
            s.type("hello");
            s.key(InputKey.SELECT_ALL);
            s.type("z");
            s.settle(0);
        });

        stage.state("click places the caret and drag selects", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field();
            s.focus(c);
            s.type("abcdefghij");
            Box in = BenchAccess.inputBox(c);
            s.click(in.x() + 2, in.y() + 6);
            s.drag(in.x() + 1, in.y() + 6, in.x() + 25, in.y() + 6);
            s.settle(0);
        });

        stage.state("delete in the text", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field();
            s.focus(c);
            s.type("abc");
            s.key(InputKey.HOME);
            s.key(InputKey.DELETE);
            s.settle(0);
        });

        stage.state("tab order passes through", W, 106, s -> {
            Rig r = new Rig(s);
            ButtonComponent a = s.add(new ButtonComponent(new Box("a", FX, 56, 60, 20), "Before", () -> { }));
            TagInputComponent c = r.field();
            c.setTags(THREE);
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
            s.settle(0);
        });
    }

    private void clipboard(Stage stage) {
        stage.state("paste a comma list", W, H, s -> withClip(c0 -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field();
            s.focus(c);
            c0.value = "stone, oak planks ,glass";
            s.key(InputKey.PASTE);
            s.settle(0);
        }));

        stage.state("paste with duplicates shakes", W, H, s -> withClip(c0 -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(tags("stone"));
            s.focus(c);
            c0.value = "dirt,STONE,dirt,gravel,";
            s.key(InputKey.PASTE);
            s.waitMs(32);
            s.settle(0);
        }));

        stage.state("paste past the limit", W, H, s -> withClip(c0 -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().maxTags(3);
            s.focus(c);
            c0.value = "a,b,c,d,e";
            s.key(InputKey.PASTE);
        }));

        stage.state("paste lines", W, H, s -> withClip(c0 -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field();
            s.focus(c);
            c0.value = "one\ntwo\r\nthree\n";
            s.key(InputKey.PASTE);
        }));

        stage.state("paste one word is typing", W, H, s -> withClip(c0 -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(tags("a"));
            s.focus(c);
            s.type("pre-");
            c0.value = "stone";
            s.key(InputKey.PASTE);
            s.settle(0);
        }));

        stage.state("paste joins what was typed", W, H, s -> withClip(c0 -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field();
            s.focus(c);
            s.type("sto");
            c0.value = "ne,dirt";
            s.key(InputKey.PASTE);
        }));

        stage.state("copy and cut", W, H, s -> withClip(c0 -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            s.focus(c);
            s.key(InputKey.COPY);
            r.clickChip(c, 1);
            s.key(InputKey.COPY);
            s.key(InputKey.CUT);
            s.key(InputKey.ESCAPE);
            s.type("word");
            s.key(InputKey.SELECT_ALL);
            s.key(InputKey.CUT);
        }));

        stage.state("paste with no clipboard", W, H, s -> {
            TextFieldComponent.Clipboard saved = TextFieldComponent.clipboard;
            TextFieldComponent.clipboard = null;
            try {
                Rig r = new Rig(s);
                TagInputComponent c = r.with(tags("a"));
                s.focus(c);
                s.key(InputKey.PASTE);
                s.key(InputKey.COPY);
            } finally {
                TextFieldComponent.clipboard = saved;
            }
        });
    }

    private interface WithClip {
        void run(Fake clip);
    }

    private static void withClip(WithClip script) {
        TextFieldComponent.Clipboard saved = TextFieldComponent.clipboard;
        Fake clip = new Fake();
        TextFieldComponent.clipboard = clip;
        try {
            script.run(clip);
        } finally {
            TextFieldComponent.clipboard = saved;
        }
    }

    private void scrolling(Stage stage) {
        stage.state("overflow shows a bar", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(MANY);
        });

        stage.state("wheel scrolls whole lines", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(MANY);
            s.scroll(FX + 40, FY + 20, -1);
            s.scroll(FX + 40, FY + 20, 5);
            s.scroll(FX + 40, FY + 20, -50);
        });

        stage.state("wheel with room does nothing", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            s.scroll(FX + 40, FY + 20, -3);
        });

        stage.state("drag the bar", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(MANY);
            s.hold(FX + FW - 4, FY + 6, FX + FW - 4, FY + FH);
        });

        stage.state("typing reveals the input", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(MANY);
            s.focus(c);
            s.type("n");
            s.settle(0);
        });

        stage.state("keys reveal the chip", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(MANY);
            s.focus(c);
            s.key(InputKey.HOME);
            s.key(InputKey.END);
            s.key(InputKey.LEFT);
            s.settle(0);
        });

        stage.state("growTo grows then scrolls", W, 116, s -> {
            Rig r = new Rig(s);
            int[] heard = new int[1];
            TagInputComponent c = r.field(new Box("t", FX, FY, FW, 16)).growTo(58).onResize(v -> heard[0] = v);
            s.add(c.popup());
            s.focus(c);
            s.type("stone,cobblestone,sandstone,oak planks,");
            s.type("spruce planks,birch planks,glass,tinted glass,dirt,gravel,diamond ore,iron ore,gold ore,coal ore,");
            s.settle(0);
        });

        stage.state("growTo shrinks back", W, 80, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field(new Box("t", FX, FY, FW, 16)).growTo(58);
            c.setTags(MANY.subList(0, 7));
            c.setTags(tags("one"));
        });

        stage.state("a box between lines keeps whole lines", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field(new Box("t", FX, FY, FW, 40));
            c.setTags(MANY);
        });
    }

    private void suggestions(Stage stage) {
        stage.state("typing opens the list", W, 126, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().suggestions(KNOWN);
            s.add(c.popup());
            s.focus(c);
            s.type("st");
            s.waitMs(OPENED);
            s.settle(0);
        });

        stage.state("opening half way", W, 126, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().suggestions(KNOWN);
            s.add(c.popup());
            s.focus(c);
            s.type("st");
            s.waitMs(32);
            s.settle(0);
        });

        stage.state("list opens at once under reduced motion", W, 126, s -> {
            Rig r = new Rig(s);
            s.paint((d, t) -> BaseComponent.reducedMotion = true);
            TagInputComponent c = r.field().suggestions(KNOWN);
            s.add(c.popup());
            s.paint((d, t) -> BaseComponent.reducedMotion = false);
            s.focus(c);
            BaseComponent.reducedMotion = true;
            try {
                s.type("st");
                s.settle(0);
            } finally {
                BaseComponent.reducedMotion = false;
            }
        });

        stage.state("down lights a row, enter takes it", W, 126, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().suggestions(KNOWN);
            s.add(c.popup());
            s.focus(c);
            s.type("st");
            s.waitMs(OPENED);
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
            s.key(InputKey.ENTER);
            s.settle(0);
        });

        stage.state("lit row", W, 126, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().suggestions(KNOWN);
            s.add(c.popup());
            s.focus(c);
            s.type("st");
            s.waitMs(OPENED);
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
            Box b = BenchAccess.rowBox(c, 2);
            s.move(b.x() + 10, b.y() + 4);
            s.settle(200);
        });

        stage.state("enter with no lit row adds the text", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().suggestions(KNOWN);
            s.add(c.popup());
            s.focus(c);
            s.type("sto");
            s.waitMs(OPENED);
            s.key(InputKey.ENTER);
            s.settle(0);
        });

        stage.state("tab takes the lit row", W, 126, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().suggestions(KNOWN);
            s.add(c.popup());
            s.focus(c);
            s.type("oak");
            s.waitMs(OPENED);
            s.key(InputKey.DOWN);
            s.key(InputKey.TAB);
            s.settle(0);
        });

        stage.state("click takes a row", W, 126, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().suggestions(KNOWN);
            s.add(c.popup());
            s.focus(c);
            s.type("planks");
            s.waitMs(OPENED);
            r.clickRow(c, 1);
            s.settle(0);
        });

        stage.state("hover row is quieter", W, 126, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().suggestions(KNOWN);
            s.add(c.popup());
            s.focus(c);
            s.type("st");
            s.waitMs(OPENED);
            Box b = BenchAccess.rowBox(c, 1);
            s.move(b.x() + 10, b.y() + 4);
            s.settle(200);
        });

        stage.state("existing tags are left out", W, 126, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().suggestions(KNOWN);
            c.setTags(tags("stone", "Dirt"));
            s.add(c.popup());
            s.focus(c);
            s.key(InputKey.DOWN);
            s.settle(200);
        });

        stage.state("accents and case fold", W, 126, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().suggestions(KNOWN);
            s.add(c.popup());
            s.focus(c);
            s.type("EME");
            s.waitMs(OPENED);
            s.settle(0);
        });

        stage.state("no match closes the list", W, 126, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().suggestions(KNOWN);
            s.add(c.popup());
            s.focus(c);
            s.type("st");
            s.waitMs(OPENED);
            s.type("qq");
            s.key(InputKey.ENTER);
            s.settle(0);
        });

        stage.state("escape closes, keeps the text", W, 126, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().suggestions(KNOWN);
            s.add(c.popup());
            s.focus(c);
            s.type("st");
            s.waitMs(OPENED);
            s.key(InputKey.ESCAPE);
            s.settle(0);
        });

        stage.state("list covers what is beneath", W, 126, s -> {
            Rig r = new Rig(s);
            int[] pressed = new int[1];
            TagInputComponent c = r.field(new Box("t", FX, FY, FW, 16)).suggestions(KNOWN);
            s.add(new ButtonComponent(new Box("b", FX, 30, 110, 20), "Behind", () -> pressed[0]++));
            s.add(new ButtonComponent(new Box("b2", FX, 54, 110, 20), "Behind too", () -> pressed[0]++));
            s.add(c.popup());
            s.focus(c);
            s.type("s");
            s.waitMs(OPENED);
            Box b = BenchAccess.rowBox(c, 1);
            s.move(b.x() + 10, b.y() + 4);
            s.settle(200);
        });

        stage.state("first click outside only closes", W, 126, s -> {
            Rig r = new Rig(s);
            int[] pressed = new int[1];
            TagInputComponent c = r.field(new Box("t", FX, FY, 120, 16)).suggestions(KNOWN);
            s.add(new ButtonComponent(new Box("b", 134, 80, 60, 20), "Behind", () -> pressed[0]++));
            s.add(c.popup());
            s.focus(c);
            s.type("xx");
            s.key(InputKey.BACKSPACE);
            s.key(InputKey.BACKSPACE);
            s.key(InputKey.DOWN);
            s.waitMs(OPENED);
            s.click(160, 90);
            s.click(160, 90);
            s.settle(0);
        });

        stage.state("list wheel and bar", W, 126, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field(new Box("t", FX, FY, FW, 16)).suggestions(KNOWN).maxRows(4);
            s.add(c.popup());
            s.focus(c);
            s.key(InputKey.DOWN);
            s.waitMs(OPENED);
            Box p = BenchAccess.popupBox(c);
            s.scroll(p.x() + 10, p.y() + 10, -1);
            s.settle(200);
        });

        stage.state("list flips above", W, 156, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field(new Box("t", FX, 110, FW, 16)).suggestions(KNOWN);
            s.add(c.popup());
            s.focus(c);
            s.key(InputKey.DOWN);
            s.waitMs(OPENED);
            Box p = BenchAccess.popupBox(c);
            s.settle(200);
        });

        stage.state("list in a short area", W, 40, s -> {
            TagInputComponent c = s.add(new TagInputComponent(new Box("t", FX, 12, FW, 16), s.panel()).suggestions(KNOWN));
            s.add(c.popup());
            s.focus(c);
            s.key(InputKey.DOWN);
            s.waitMs(OPENED);
            Box p = BenchAccess.popupBox(c);
            s.settle(200);
        });

        stage.state("list stays inside on the right", 120, 120, s -> {
            TagInputComponent c = s.add(new TagInputComponent(new Box("t", 70, 8, 40, 16), s.panel()).suggestions(KNOWN));
            s.add(c.popup());
            s.focus(c);
            s.key(InputKey.DOWN);
            s.waitMs(OPENED);
            Box p = BenchAccess.popupBox(c);
            s.settle(200);
        });

        stage.state("long suggestion and its tooltip", W, 120, s -> {
            TagInputComponent c = s.add(new TagInputComponent(new Box("t", FX, 8, 100, 16), s.panel()).suggestions(List.of(LONG, "short")));
            s.add(c.popup());
            s.focus(c);
            s.key(InputKey.DOWN);
            s.waitMs(OPENED);
            Box b = BenchAccess.rowBox(c, 0);
            s.move(b.x() + 10, b.y() + 4);
            s.settle(1500);
        });

        stage.state("10000 suggestions draw only their rows", W, 126, s -> {
            Rig r = new Rig(s);
            List<String> many = numbered(10_000);
            TagInputComponent c = r.field(new Box("t", FX, FY, FW, 16)).suggestions(many);
            s.add(c.popup());
            s.focus(c);
            s.key(InputKey.DOWN);
            s.waitMs(OPENED);
            for (int i = 0; i < 50; i++) {
                s.key(InputKey.UP);
            }
            s.settle(200);
        });

        stage.state("disabling closes the list", W, 126, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().suggestions(KNOWN);
            s.add(c.popup());
            s.focus(c);
            s.type("st");
            s.waitMs(OPENED);
            c.active(false);
            s.waitMs(16);
        });

        stage.state("leaving with the list open commits", W, 126, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().suggestions(KNOWN);
            s.add(c.popup());
            s.focus(c);
            s.type("st");
            s.waitMs(OPENED);
            s.focus(null);
        });

        stage.state("without popup it draws the list itself", W, 126, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().suggestions(KNOWN);
            s.focus(c);
            s.type("st");
            s.waitMs(OPENED);
            r.clickRow(c, 0);
            s.settle(0);
        });
    }

    private void edges(Stage stage) {
        stage.state("hidden takes no input", W, 38, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field(new Box("t", FX, FY, FW, 16));
            c.setTags(THREE);
            c.visible = false;
            Box b = BenchAccess.closeBox(c, 0);
            s.click(b.x() + 5, b.y() + 6);
            s.focus(c);
            s.type("x");
            s.key(InputKey.BACKSPACE);
            s.scroll(FX + 10, FY + 8, -1);
        });

        stage.state("very long chip is cut with a tooltip", W, 110, s -> {
            TagInputComponent c = s.add(new TagInputComponent(new Box("t", FX, 8, FW, 30), s.panel()));
            c.setTags(tags("short", LONG));
            Box b = BenchAccess.chipBox(c, 1);
            s.move(b.x() + 20, b.y() + 6);
            s.settle(1500);
        });

        stage.state("very long typed text scrolls sideways", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().maxLength(200);
            c.setTags(tags("a"));
            s.focus(c);
            s.type(LONG);
            s.settle(0);
        });

        stage.state("long text caret at the start", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field().maxLength(200);
            s.focus(c);
            s.type(LONG);
            s.key(InputKey.HOME);
            s.settle(0);
        });

        stage.state("very long placeholder", W, 32, s -> {
            s.add(new TagInputComponent(new Box("t", FX, FY, 100, 16), s.panel()).placeholder(LONG));
        });

        stage.state("tiny box", W, 40, s -> {
            TagInputComponent small = s.add(new TagInputComponent(new Box("a", 8, 8, 40, 16), s.panel()));
            small.setTags(tags("stone", "oak planks"));
            TagInputComponent flat = s.add(new TagInputComponent(new Box("b", 56, 8, 60, 10), s.panel()));
            flat.setTags(tags("stone"));
            TagInputComponent speck = s.add(new TagInputComponent(new Box("c", 124, 8, 6, 6), s.panel()));
            speck.setTags(tags("stone"));
            TagInputComponent nil = s.add(new TagInputComponent(new Box("d", 140, 8, 0, 0), s.panel()));
            nil.setTags(tags("stone"));
            s.click(60, 12);
            s.type("x,");
        });

        stage.state("narrow chip loses its x", W, 40, s -> {
            TagInputComponent c = s.add(new TagInputComponent(new Box("t", 8, 8, 20, 30), s.panel()));
            c.setTags(tags("abcdefgh"));
        });

        stage.state("empty stays calm", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field();
            s.focus(c);
            s.key(InputKey.BACKSPACE);
            s.key(InputKey.DELETE);
            s.key(InputKey.LEFT);
            s.key(InputKey.RIGHT);
            s.key(InputKey.HOME);
            s.key(InputKey.END);
            s.key(InputKey.ENTER);
            s.key(InputKey.SELECT_ALL);
            s.key(InputKey.COPY);
            s.scroll(FX + 10, FY + 10, -1);
            s.settle(0);
        });

        stage.state("chip tooltip and base tooltip", W, 110, s -> {
            TagInputComponent c = s.add(new TagInputComponent(new Box("t", FX, 8, FW, 30), s.panel())
                    .chipTooltip(t -> t.equals("stone") ? List.of("Chip", t) : null)
                    .closeTooltip("Remove")
                    .tooltip("Base"));
            c.setTags(THREE);
            Box a = BenchAccess.chipBox(c, 0);
            Box b = BenchAccess.chipBox(c, 1);
            Box x = BenchAccess.closeBox(c, 1);
            s.move(a.x() + 5, a.y() + 6);
            s.settle(1500);
        });

        stage.state("setTags during editing", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            r.clickChip(c, 2);
            c.setTags(tags("x", "y", "X"));
            c.setTags(null);
            c.setText("typed§");
            s.settle(0);
        });

        stage.state("the list is a copy", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.with(THREE);
            List<String> got = c.tags();
            got.clear();
        });

        stage.state("emoji pair stays whole", W, H, s -> {
            Rig r = new Rig(s);
            TagInputComponent c = r.field();
            s.focus(c);
            s.type("a😀");
            s.key(InputKey.LEFT);
            s.key(InputKey.RIGHT);
            s.key(InputKey.BACKSPACE);
            s.settle(0);
        });
    }
}
