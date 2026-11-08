package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemPickerComponentTest {

    /** 30 vanilla ids then 10 of a mod; "spore", "core" and "forest" hide an "ore", "diorite" does not. */
    static final List<String> IDS = List.of(
            "minecraft:stone", "minecraft:granite", "minecraft:diorite", "minecraft:coal_ore", "minecraft:iron_ore",
            "minecraft:gold_ore", "minecraft:diamond_ore", "minecraft:redstone_ore", "minecraft:oak_log",
            "minecraft:birch_log", "minecraft:spruce_log", "minecraft:dirt", "minecraft:grass_block", "minecraft:sand",
            "minecraft:gravel", "minecraft:glass", "minecraft:torch", "minecraft:stick", "minecraft:apple",
            "minecraft:bread", "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:diamond", "minecraft:emerald",
            "minecraft:bow", "minecraft:arrow", "minecraft:shield", "minecraft:bucket", "minecraft:compass",
            "minecraft:clock",
            "demo:tin_ore", "demo:wrench", "demo:gear", "demo:copper_wire", "demo:core", "demo:spore_pod",
            "demo:battery", "demo:forest_lamp", "demo:fan", "demo:bolt");

    static final ItemLookup LOOKUP = new ItemLookup() {
        @Override
        public List<String> items() {
            return IDS;
        }

        @Override
        public List<String> blocks() {
            return List.of();
        }

        @Override
        public String name(String id) {
            return id.substring(id.indexOf(':') + 1).replace('_', ' ');
        }

        @Override
        public boolean tagged(String id, String tag) {
            return tag.equals("minecraft:logs") && id.endsWith("_log");
        }
    };

    static ItemPickerComponent picker(String name, List<String> picks) {
        return new ItemPickerComponent(new Box(name, 0, 0, 200, 120), (key, args) -> key, LOOKUP, picks::add);
    }

    @Test
    void searchWordsModAndTagKeepTheCountedIds() {
        ItemPickerComponent p = picker("count", new ArrayList<>());
        p.search("ore");
        assertEquals(List.of("minecraft:coal_ore", "minecraft:iron_ore", "minecraft:gold_ore", "minecraft:diamond_ore",
                "minecraft:redstone_ore", "demo:tin_ore", "demo:core", "demo:spore_pod", "demo:forest_lamp"), p.shown());
        p.search("@demo");
        assertEquals(10, p.shown().size());
        p.search("@demo ore");
        assertEquals(List.of("demo:tin_ore", "demo:core", "demo:spore_pod", "demo:forest_lamp"), p.shown());
        p.search("#logs");
        assertEquals(List.of("minecraft:oak_log", "minecraft:birch_log", "minecraft:spruce_log"), p.shown());
    }

    @Test
    void downRightEnterPicksTheSecondMatch() {
        List<String> picks = new ArrayList<>();
        ItemPickerComponent p = picker("keys", picks);
        p.focused = true;
        p.search("ore");
        p.keyPressed(InputKey.DOWN, false);
        p.keyPressed(InputKey.RIGHT, false);
        p.keyPressed(InputKey.ENTER, false);
        assertEquals(List.of("minecraft:iron_ore"), picks);
    }

    @Test
    void nameSortFlipsAndIsRememberedByPickerName() {
        ItemPickerComponent p = picker("sorted", new ArrayList<>());
        p.search("@demo");
        p.mouseClicked(200 - 34 + 4, 4, 0);
        assertEquals(List.of("demo:battery", "demo:bolt", "demo:copper_wire", "demo:core", "demo:fan",
                "demo:forest_lamp", "demo:gear", "demo:spore_pod", "demo:tin_ore", "demo:wrench"), p.shown());
        p.mouseClicked(200 - 16 + 4, 4, 0);
        assertEquals("demo:wrench", p.shown().get(0));
        ItemPickerComponent again = picker("sorted", new ArrayList<>());
        again.search("@demo");
        assertEquals("demo:wrench", again.shown().get(0));
    }
}
