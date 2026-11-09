package dev.dryaides.minuit.internal.game;

import dev.dryaides.minuit.ItemLookup;
import dev.dryaides.minuit.port.mc.GameIds;
import net.minecraft.locale.Language;
import net.minecraft.tags.Tag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The item registry as the kit's {@link ItemLookup}. Read from the registry alone, so it works on the
 * title screen; the lists are built once, on first use, after every mod has registered. Not API.
 */
public final class GameItems implements ItemLookup {

    private List<String> items;
    private List<String> blocks;
    /** Names by id, for {@link #namesLanguage}; a language change or resource reload empties it. */
    private final Map<String, String> names = new HashMap<>();
    private Language namesLanguage;

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
        // The game swaps in a new Language on each language change or resource reload.
        Language language = Language.getInstance();
        if (language != this.namesLanguage) {
            this.names.clear();
            this.namesLanguage = language;
        }
        return this.names.computeIfAbsent(id, key -> {
            Item item = GameIds.item(key);
            return item == null ? key : new ItemStack(item).getHoverName().getString();
        });
    }

    @Override
    public boolean tagged(String id, String tag) {
        Item item = GameIds.item(id);
        Tag<Item> key = GameIds.itemTag(tag);
        return item != null && key != null && item.is(key);
    }

    private synchronized void load() {
        if (this.items != null) {
            return;
        }
        List<String> all = new ArrayList<>();
        List<String> placed = new ArrayList<>();
        for (Item item : GameIds.ITEMS) {
            if (item == Items.AIR) {
                continue;
            }
            String id = GameIds.ITEMS.getKey(item).toString();
            all.add(id);
            if (item instanceof BlockItem) {
                placed.add(id);
            }
        }
        this.blocks = Collections.unmodifiableList(placed);
        this.items = Collections.unmodifiableList(all);
    }
}
