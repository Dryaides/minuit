package dev.dryaides.minuit.internal.game;

import dev.dryaides.minuit.ItemLookup;
import dev.dryaides.minuit.port.mc.GameIds;
import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The item registry as the kit's {@link ItemLookup}, one id per sub-item the creative search tab shows
 * ({@code namespace:path@meta}, see {@link GameIds}). Lists are built once, on first use, after every mod
 * has registered. Tags are the ore dictionary's: a {@code c:ingots/iron} style tag reads as
 * {@code ingotIron}. Client only, not API.
 */
public final class GameItems implements ItemLookup {

    private List<String> items;
    private List<String> blocks;
    /** Names by id, for {@link #namesLanguage}; a language change empties it. */
    private final Map<String, String> names = new HashMap<>();
    private String namesLanguage;

    @Override
    public List<String> items() {
        load();
        return this.items;
    }

    @Override
    public List<String> blocks() {
        load();
        return this.blocks;
    }

    @Override
    public synchronized String name(String id) {
        String language = Minecraft.getMinecraft().gameSettings.language;
        if (!language.equals(this.namesLanguage)) {
            this.names.clear();
            this.namesLanguage = language;
        }
        return this.names.computeIfAbsent(id, key -> {
            ItemStack stack = GameIds.stack(key);
            return stack.isEmpty() ? key : stack.getDisplayName();
        });
    }

    @Override
    public boolean tagged(String id, String tag) {
        ItemStack stack = GameIds.stack(id);
        String ore = oreName(tag);
        if (stack.isEmpty() || ore == null || !OreDictionary.doesOreNameExist(ore)) {
            return false;
        }
        int wanted = OreDictionary.getOreID(ore);
        for (int held : OreDictionary.getOreIDs(stack)) {
            if (held == wanted) {
                return true;
            }
        }
        return false;
    }

    /**
     * The ore dictionary name of a common tag: {@code c:ingots/iron} to {@code ingotIron},
     * {@code c:storage_blocks/iron} to {@code blockIron}, {@code c:ores/iron} to {@code oreIron}. Null for a
     * tag of another shape.
     */
    static String oreName(String tag) {
        int colon = tag.indexOf(':');
        int slash = tag.indexOf('/', colon + 1);
        if (colon < 0 || slash < 0 || slash == tag.length() - 1 || tag.indexOf('/', slash + 1) >= 0) {
            return null;
        }
        String kind = tag.substring(colon + 1, slash);
        String prefix = kind.equals("storage_blocks") ? "block"
                : kind.endsWith("s") ? kind.substring(0, kind.length() - 1) : kind;
        StringBuilder out = new StringBuilder(prefix);
        boolean upper = true;
        for (char c : tag.substring(slash + 1).toCharArray()) {
            if (c == '_') {
                upper = true;
            } else {
                out.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return out.toString();
    }

    private synchronized void load() {
        if (this.items != null) {
            return;
        }
        Set<String> all = new LinkedHashSet<>();
        Set<String> placed = new LinkedHashSet<>();
        NonNullList<ItemStack> subItems = NonNullList.create();
        for (Item item : Item.REGISTRY) {
            subItems.clear();
            try {
                item.getSubItems(CreativeTabs.SEARCH, subItems);
            } catch (RuntimeException e) {
                continue;
            }
            for (ItemStack stack : subItems) {
                if (stack.isEmpty()) {
                    continue;
                }
                String id = GameIds.itemId(stack);
                all.add(id);
                if (item instanceof ItemBlock) {
                    placed.add(id);
                }
            }
        }
        this.blocks = Collections.unmodifiableList(new ArrayList<>(placed));
        this.items = Collections.unmodifiableList(new ArrayList<>(all));
    }
}
