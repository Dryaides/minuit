package site;

import dev.dryaides.minuit.EntityPreviewComponent;
import dev.dryaides.minuit.EntityView;
import dev.dryaides.minuit.ItemLookup;
import dev.dryaides.minuit.ItemPickerComponent;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.KeyBinding;
import dev.dryaides.minuit.KeyPress;
import dev.dryaides.minuit.KeybindFieldComponent;
import dev.dryaides.minuit.RichTextComponent;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.Words;
import dev.dryaides.minuit.ToastComponent;
import dev.dryaides.minuit.ToastSpec;
import dev.dryaides.minuit.SpriteIcon;
import site.SiteScenes.Scene;
import site.SiteScenes.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * The scenes of the stand-alone Minecraft components: item picker, formatted text, toasts, entity
 * preview, key binding field. Shared by the bench ({@code testbench/McBench}) and the site, as
 * {@link SiteScenes} is; {@link #stage} answers the ids of {@link #ALL}.
 */
public final class McScenes {

    public static final List<String> ALL = List.of("itempicker/search", "itempicker/empty", "richtext/codes",
            "toast/stack", "entity/turn", "keybind/states");

    /** 30 vanilla ids and 10 of a demo mod, in registry order. */
    public static final List<String> ITEMS = List.of(
            "minecraft:stone", "minecraft:granite", "minecraft:diorite", "minecraft:coal_ore", "minecraft:iron_ore",
            "minecraft:gold_ore", "minecraft:diamond_ore", "minecraft:redstone_ore", "minecraft:oak_log",
            "minecraft:birch_log", "minecraft:spruce_log", "minecraft:dirt", "minecraft:grass_block", "minecraft:sand",
            "minecraft:gravel", "minecraft:glass", "minecraft:torch", "minecraft:stick", "minecraft:apple",
            "minecraft:bread", "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:diamond", "minecraft:emerald",
            "minecraft:bow", "minecraft:arrow", "minecraft:shield", "minecraft:bucket", "minecraft:compass",
            "minecraft:clock",
            "demo:tin_ore", "demo:wrench", "demo:gear", "demo:copper_wire", "demo:core", "demo:spore_pod",
            "demo:battery", "demo:forest_lamp", "demo:fan", "demo:bolt");

    /** {@link #ITEMS} with names from the path, blocks for the first 16, the logs tagged. */
    public static final ItemLookup LOOKUP = new ItemLookup() {
        @Override
        public List<String> items() {
            return ITEMS;
        }

        @Override
        public List<String> blocks() {
            return ITEMS.subList(0, 16);
        }

        @Override
        public String name(String id) {
            String path = id.substring(id.indexOf(':') + 1).replace('_', ' ');
            return Character.toUpperCase(path.charAt(0)) + path.substring(1);
        }

        @Override
        public boolean tagged(String id, String tag) {
            return tag.equals("minecraft:logs") && id.endsWith("_log");
        }
    };

    public static final String RICH = "§lBold§r, §oitalic§r, §nunder§r, §mstruck§r, §kmagic§r. "
            + "§cRed §6gold §eyellow §aGreen §bAqua §9blue §dPink §7grey §8dark. &2Ampersand &lworks&r too, \\& kept. "
            + "Read the [wiki](https://minecraft.wiki) for more.";

    /** The words the scenes read; the bench plugs the kit's en_us.json, the default shows the keys. */
    public static volatile Words words = SiteScenes.WORDS;

    private McScenes() {
    }

    /** A key binding that lives in memory, its key named as the game names letters. */
    public static final class DemoBinding implements KeyBinding {
        public final String name;
        final KeyPress defaultKey;
        public KeyPress key;
        final List<DemoBinding> all;

        public DemoBinding(String name, KeyPress key, List<DemoBinding> all) {
            this.name = name;
            this.defaultKey = key;
            this.key = key;
            this.all = all;
            all.add(this);
        }

        @Override
        public String keyName() {
            if (this.key == null) {
                return "Not bound";
            }
            if (this.key.kind() == KeyPress.Kind.MOUSE) {
                return "Mouse " + (this.key.code() + 1);
            }
            int c = this.key.code();
            if (c >= 65 && c <= 90 || c >= 48 && c <= 57) {
                return String.valueOf((char) c);
            }
            if (c >= 290 && c <= 301) {
                return "F" + (c - 289);
            }
            return switch (c) {
                case 32 -> "Space";
                case 256 -> "Escape";
                case 257 -> "Enter";
                case 258 -> "Tab";
                case 262 -> "Right Arrow";
                case 263 -> "Left Arrow";
                case 264 -> "Down Arrow";
                case 265 -> "Up Arrow";
                case 340 -> "Left Shift";
                case 341 -> "Left Control";
                case 342 -> "Left Alt";
                case 344 -> "Right Shift";
                case 345 -> "Right Control";
                default -> "Key " + c;
            };
        }

        @Override
        public boolean isDefault() {
            return java.util.Objects.equals(this.key, this.defaultKey);
        }

        @Override
        public List<String> conflicts() {
            List<String> names = new ArrayList<>();
            for (DemoBinding other : this.all) {
                if (other != this && this.key != null && this.key.equals(other.key)) {
                    names.add(other.name);
                }
            }
            return names;
        }

        @Override
        public void bind(KeyPress key) {
            this.key = new KeyPress(key.kind(), key.code(), 0);
        }

        @Override
        public void unbind() {
            this.key = null;
        }

        @Override
        public void reset() {
            this.key = this.defaultKey;
        }
    }

    public static Scene pickerScene(Theme t, String query) {
        Scene s = new Scene(t, "Items");
        ItemPickerComponent picker = s.put(new ItemPickerComponent(s.place(12, 26, 276, 142), words, LOOKUP,
                id -> { }));
        picker.selected("minecraft:iron_ore");
        picker.search(query);
        return s;
    }

    public static Scene richScene(Theme t) {
        Scene s = new Scene(t, "Formatted text");
        s.put(new RichTextComponent(s.place(12, 26, 276, 100), RICH));
        s.put(new RichTextComponent(s.place(12, 140, 276, 10), "§9Centred, §lthree§r lines at most").centered().lines(3));
        return s;
    }

    public static Scene toastScene(Theme t) {
        Scene s = new Scene(t, "Toasts");
        s.put(new ToastComponent(s.place(12, 24, 160, 32), ToastSpec.of("World saved", "Backup 3 of 5")
                .icon(ItemView.of("minecraft:diamond"))));
        String longBody = "Your shop sold twelve diamonds while you were away";
        s.put(new ToastComponent(s.place(12, 60, 160, 32), ToastSpec.of("Shop", longBody)
                .icon(SpriteIcon.item("minecraft:item/emerald")))).shownFor(500);
        s.put(new ToastComponent(s.place(12, 96, 160, 32), ToastSpec.of("Shop", longBody)
                .icon(SpriteIcon.item("minecraft:item/emerald")))).shownFor(2000);
        s.put(new ToastComponent(s.place(12, 132, 160, 32), ToastSpec.of("No icon", "Text starts at x = 10")));
        return s;
    }

    public static Scene entityScene(Theme t) {
        Scene s = new Scene(t, "Entity preview");
        s.put(new EntityPreviewComponent(s.place(12, 26, 80, 120), words, EntityView.of("minecraft:pig")));
        EntityPreviewComponent turned = s.put(new EntityPreviewComponent(s.place(110, 26, 80, 120), words,
                EntityView.player()));
        turned.yaw = 300F;
        s.put(new EntityPreviewComponent(s.place(208, 26, 80, 120), words, EntityView.block("minecraft:oak_log")))
                .pitch = 20F;
        return s;
    }

    /** Three fields: one at rest, one listening, two on the same key (red, with the tooltip). */
    public static Scene keybindScene(Theme t) {
        Scene s = new Scene(t, "Key bindings");
        List<DemoBinding> all = new ArrayList<>();
        DemoBinding jump = new DemoBinding("Jump", KeyPress.key(32, 0), all);
        DemoBinding map = new DemoBinding("Open map", KeyPress.key(77, 0), all);
        DemoBinding zoom = new DemoBinding("Zoom", KeyPress.key(67, 0), all);
        zoom.key = KeyPress.key(77, 0);
        s.put(new KeybindFieldComponent(s.place(12, 30, 200, 20), words, jump));
        KeybindFieldComponent listening = s.put(new KeybindFieldComponent(s.place(12, 56, 200, 20), words,
                new DemoBinding("Sneak", KeyPress.key(340, 0), all)));
        listening.listening = true;
        listening.focused = true;
        s.put(new KeybindFieldComponent(s.place(12, 82, 200, 20), words, map));
        s.put(new KeybindFieldComponent(s.place(12, 108, 200, 20), words, zoom));
        return s;
    }

    /** A 300 by 180 scene with its canvas cut to it, as {@code SiteScenes.bench}. */
    private static Stage bench(String id, Theme t, Scene scene, int mxRel, int myRel) {
        int left = (SiteScenes.W - 300) / 2 - SiteScenes.PAD;
        int top = (SiteScenes.H - 180) / 2 - SiteScenes.PAD;
        int pad = SiteScenes.PAD;
        return new Stage(id, t, 300 + 2 * pad, 180 + 2 * pad, -left, -top, mxRel < 0 ? -100 - left : pad + mxRel,
                myRel < 0 ? -100 - top : pad + myRel, 6).panel(scene);
    }

    /** The scene {@code id} of {@link #ALL} in {@code t}; null if unknown. */
    public static Stage stage(String id, Theme t) {
        return switch (id) {
            case "itempicker/search" -> bench(id, t, pickerScene(t, ""), 60, 70);
            case "itempicker/empty" -> bench(id, t, pickerScene(t, "zzz"), -1, -1);
            case "richtext/codes" -> bench(id, t, richScene(t), -1, -1);
            case "toast/stack" -> bench(id, t, toastScene(t), -1, -1);
            case "entity/turn" -> bench(id, t, entityScene(t), 50, 80);
            case "keybind/states" -> bench(id, t, keybindScene(t), 60, 115);
            default -> null;
        };
    }
}
