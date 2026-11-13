package dev.dryaides.minuit.internal.showcase;

import dev.dryaides.minuit.EntityPreviewComponent;
import dev.dryaides.minuit.EntityView;
import dev.dryaides.minuit.ItemLookup;
import dev.dryaides.minuit.ItemPickerComponent;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.KeyBinding;
import dev.dryaides.minuit.KeyPress;
import dev.dryaides.minuit.KeybindFieldComponent;
import dev.dryaides.minuit.RichText;
import dev.dryaides.minuit.RichTextComponent;
import dev.dryaides.minuit.TextMeasure;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.Words;
import dev.dryaides.minuit.ToastComponent;
import dev.dryaides.minuit.ToastSpec;
import dev.dryaides.minuit.SpriteIcon;
import dev.dryaides.minuit.internal.showcase.SiteScenes.Scene;
import dev.dryaides.minuit.internal.showcase.SiteScenes.Stage;

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

    /**
     * 40 vanilla ids, each with a flat texture under data/tex (tools/extract_items.py), then 9 of Mekanism's
     * (MIT, data/tex/mekanism/textures/item/LICENSE.txt) as the modded items.
     */
    public static final List<String> ITEMS = List.of(
            "minecraft:stone", "minecraft:granite", "minecraft:diorite", "minecraft:coal_ore", "minecraft:iron_ore",
            "minecraft:gold_ore", "minecraft:diamond_ore", "minecraft:redstone_ore", "minecraft:oak_log",
            "minecraft:birch_log", "minecraft:spruce_log", "minecraft:dirt", "minecraft:cobblestone", "minecraft:sand",
            "minecraft:gravel", "minecraft:glass", "minecraft:torch", "minecraft:stick", "minecraft:apple",
            "minecraft:bread", "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:diamond", "minecraft:emerald",
            "minecraft:bow", "minecraft:arrow", "minecraft:shears", "minecraft:bucket", "minecraft:compass",
            "minecraft:clock",
            "minecraft:copper_ingot", "minecraft:lapis_lazuli", "minecraft:amethyst_shard", "minecraft:quartz",
            "minecraft:ender_pearl", "minecraft:blaze_rod", "minecraft:slime_ball", "minecraft:glowstone_dust",
            "minecraft:gunpowder", "minecraft:feather",
            "mekanism:ingot_osmium", "mekanism:ingot_steel", "mekanism:ingot_refined_obsidian",
            "mekanism:alloy_infused", "mekanism:alloy_reinforced", "mekanism:basic_control_circuit",
            "mekanism:enriched_diamond", "mekanism:configurator", "mekanism:energy_tablet");

    /** Mekanism's names, from its en_us.json. */
    private static final java.util.Map<String, String> MODDED_NAMES = java.util.Map.of(
            "mekanism:ingot_osmium", "Osmium Ingot", "mekanism:ingot_steel", "Steel Ingot",
            "mekanism:ingot_refined_obsidian", "Refined Obsidian Ingot", "mekanism:alloy_infused", "Infused Alloy",
            "mekanism:alloy_reinforced", "Reinforced Alloy", "mekanism:basic_control_circuit", "Basic Control Circuit",
            "mekanism:enriched_diamond", "Enriched Diamond", "mekanism:configurator", "Configurator",
            "mekanism:energy_tablet", "Energy Tablet");

    /** The game's English names of the vanilla items the scenes and the bench demos show, from the client jar's en_us.json. */
    private static final java.util.Map<String, String> VANILLA_NAMES = java.util.Map.ofEntries(
            java.util.Map.entry("minecraft:amethyst_shard", "Amethyst Shard"),
            java.util.Map.entry("minecraft:apple", "Apple"),
            java.util.Map.entry("minecraft:arrow", "Arrow"),
            java.util.Map.entry("minecraft:birch_log", "Birch Log"),
            java.util.Map.entry("minecraft:blaze_rod", "Blaze Rod"),
            java.util.Map.entry("minecraft:book", "Book"),
            java.util.Map.entry("minecraft:bow", "Bow"),
            java.util.Map.entry("minecraft:bread", "Bread"),
            java.util.Map.entry("minecraft:bucket", "Bucket"),
            java.util.Map.entry("minecraft:bundle", "Bundle"),
            java.util.Map.entry("minecraft:clock", "Clock"),
            java.util.Map.entry("minecraft:coal_ore", "Coal Ore"),
            java.util.Map.entry("minecraft:cobblestone", "Cobblestone"),
            java.util.Map.entry("minecraft:compass", "Compass"),
            java.util.Map.entry("minecraft:copper_ingot", "Copper Ingot"),
            java.util.Map.entry("minecraft:diamond", "Diamond"),
            java.util.Map.entry("minecraft:diamond_ore", "Diamond Ore"),
            java.util.Map.entry("minecraft:diamond_sword", "Diamond Sword"),
            java.util.Map.entry("minecraft:diorite", "Diorite"),
            java.util.Map.entry("minecraft:dirt", "Dirt"),
            java.util.Map.entry("minecraft:emerald", "Emerald"),
            java.util.Map.entry("minecraft:ender_pearl", "Ender Pearl"),
            java.util.Map.entry("minecraft:feather", "Feather"),
            java.util.Map.entry("minecraft:glass", "Glass"),
            java.util.Map.entry("minecraft:glowstone_dust", "Glowstone Dust"),
            java.util.Map.entry("minecraft:gold_ingot", "Gold Ingot"),
            java.util.Map.entry("minecraft:gold_ore", "Gold Ore"),
            java.util.Map.entry("minecraft:golden_apple", "Golden Apple"),
            java.util.Map.entry("minecraft:granite", "Granite"),
            java.util.Map.entry("minecraft:gravel", "Gravel"),
            java.util.Map.entry("minecraft:gunpowder", "Gunpowder"),
            java.util.Map.entry("minecraft:iron_ingot", "Iron Ingot"),
            java.util.Map.entry("minecraft:iron_ore", "Iron Ore"),
            java.util.Map.entry("minecraft:iron_pickaxe", "Iron Pickaxe"),
            java.util.Map.entry("minecraft:iron_sword", "Iron Sword"),
            java.util.Map.entry("minecraft:lapis_lazuli", "Lapis Lazuli"),
            java.util.Map.entry("minecraft:nether_star", "Nether Star"),
            java.util.Map.entry("minecraft:oak_log", "Oak Log"),
            java.util.Map.entry("minecraft:oak_sapling", "Oak Sapling"),
            java.util.Map.entry("minecraft:quartz", "Nether Quartz"),
            java.util.Map.entry("minecraft:redstone", "Redstone Dust"),
            java.util.Map.entry("minecraft:redstone_ore", "Redstone Ore"),
            java.util.Map.entry("minecraft:sand", "Sand"),
            java.util.Map.entry("minecraft:shears", "Shears"),
            java.util.Map.entry("minecraft:slime_ball", "Slimeball"),
            java.util.Map.entry("minecraft:spruce_log", "Spruce Log"),
            java.util.Map.entry("minecraft:stick", "Stick"),
            java.util.Map.entry("minecraft:stone", "Stone"),
            java.util.Map.entry("minecraft:torch", "Torch"),
            java.util.Map.entry("minecraft:totem_of_undying", "Totem of Undying"));

    /**
     * {@link #ITEMS} with the game's names, blocks for the first 16, the logs tagged: the item lookup the site and the
     * bench plug in ({@link ItemLookup#plugIn}), where no game holds the registry. The game keeps its own.
     */
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
            String modded = MODDED_NAMES.get(id);
            if (modded != null) {
                return modded;
            }
            String vanilla = VANILLA_NAMES.get(id);
            if (vanilla != null) {
                return vanilla;
            }
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

    /** A key binding that lives in memory, its key named as the game names letters, Ctrl, Shift or Alt before it as NeoForge shows them. */
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
            return KeyPress.prefix(this.key.mods()) + baseName();
        }

        @Override
        public boolean supportsModifiers() {
            return true;
        }

        @Override
        public boolean supportsMultipleModifiers() {
            return true;
        }

        private String baseName() {
            if (this.key.kind() == KeyPress.Kind.MOUSE) {
                return switch (this.key.code()) {
                    case 0 -> "Left Button";
                    case 1 -> "Right Button";
                    case 2 -> "Middle Button";
                    default -> "Button " + (this.key.code() + 1);
                };
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
                case 346 -> "Right Alt";
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
            this.key = new KeyPress(key.kind(), key.code(), 0, key.mods());
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
        ItemPickerComponent picker = s.put(new ItemPickerComponent(s.place(12, 26, 276, 142), words, ItemLookup.game(),
                id -> { }));
        picker.selected("minecraft:iron_ore");
        picker.search(query);
        return s;
    }

    public static Scene richScene(Theme t) {
        Scene s = new Scene(t, "Formatted text");
        int richH = 10 * RichText.parse(RICH).wrap(TextMeasure.draw(), 276).size();
        s.put(new RichTextComponent(s.place(12, 26, 276, richH), RICH));
        s.put(new RichTextComponent(s.place(12, 34 + richH, 276, 10), "§9Centred, §lthree§r lines at most").centered().lines(3));
        s.h = 54 + richH;
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
        turned.yaw = 150F;
        s.put(new EntityPreviewComponent(s.place(208, 26, 80, 120), words, EntityView.block("minecraft:oak_log")))
                .pitch = 20F;
        return s;
    }

    /** Four fields: one at rest, one listening, two on the same combo, Ctrl + M (red, with the tooltip). */
    public static Scene keybindScene(Theme t) {
        Scene s = new Scene(t, "Key bindings");
        List<DemoBinding> all = new ArrayList<>();
        DemoBinding jump = new DemoBinding("Jump", KeyPress.key(32, 0), all);
        KeyPress ctrlM = KeyPress.key(77, 0).with(KeyPress.Modifier.CONTROL);
        DemoBinding map = new DemoBinding("Open map", ctrlM, all);
        DemoBinding zoom = new DemoBinding("Zoom", KeyPress.key(67, 0), all);
        zoom.key = ctrlM;
        s.put(new KeybindFieldComponent(s.place(12, 30, 200, 20), words, jump));
        KeybindFieldComponent listening = s.put(new KeybindFieldComponent(s.place(12, 56, 200, 20), words,
                new DemoBinding("Sneak", KeyPress.key(340, 0), all)));
        listening.listening = true;
        s.focus(listening);
        s.put(new KeybindFieldComponent(s.place(12, 82, 200, 20), words, map));
        s.put(new KeybindFieldComponent(s.place(12, 108, 200, 20), words, zoom));
        return s;
    }

    /** A 300 by 180 scene with its canvas cut to it, as {@code SiteScenes.bench}. */
    private static Stage bench(String id, Theme t, Scene scene, int mxRel, int myRel) {
        int left = (SiteScenes.W - 300) / 2 - SiteScenes.PAD;
        int top = (SiteScenes.H - 180) / 2 - SiteScenes.PAD;
        int pad = SiteScenes.PAD;
        return new Stage(id, t, 300 + 2 * pad, scene.h + 2 * pad, -left, -top, mxRel < 0 ? -100 - left : pad + mxRel,
                myRel < 0 ? -100 - top : pad + myRel, 6).panel(scene);
    }

    /** The scene {@code id} of {@link #ALL} in {@code t}; null if unknown. */
    public static Stage stage(String id, Theme t) {
        return switch (id) {
            case "itempicker/search" -> bench(id, t, pickerScene(t, ""), -1, -1);
            case "itempicker/empty" -> bench(id, t, pickerScene(t, "zzz"), -1, -1);
            case "richtext/codes" -> bench(id, t, richScene(t), -1, -1);
            case "toast/stack" -> bench(id, t, toastScene(t), -1, -1);
            case "entity/turn" -> bench(id, t, entityScene(t), 50, 80);
            case "keybind/states" -> bench(id, t, keybindScene(t), -1, -1);
            default -> null;
        };
    }
}
