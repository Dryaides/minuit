package dev.dryaides.minuit.port.mc;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;

/**
 * Ids from text on 1.12.2, where an item is a registry name plus a metadata value: core's item ids are
 * {@code namespace:path} for meta 0 and {@code namespace:path@meta} otherwise ({@code @} is illegal in
 * 1.13+ ids, so the two never collide). Not API.
 */
public final class GameIds {

    private GameIds() {
    }

    /** Null when the text is no valid id. */
    public static ResourceLocation id(String text) {
        if (text == null || text.isEmpty() || text.indexOf('@') >= 0) {
            return null;
        }
        try {
            return new ResourceLocation(text);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static ResourceLocation of(String namespace, String path) {
        return id(namespace + ":" + path);
    }

    /** Null when the id is malformed or names no registered item; the {@code @meta} suffix is ignored. */
    public static Item item(String id) {
        ResourceLocation parsed = id(name(id));
        return parsed == null ? null : Item.REGISTRY.getObject(parsed);
    }

    /** A one-item stack of {@code id}, with its {@code @meta}; empty for an unknown or malformed id. */
    public static ItemStack stack(String id) {
        Item item = item(id);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        int at = id.indexOf('@');
        int meta = 0;
        if (at >= 0) {
            try {
                meta = Integer.parseInt(id.substring(at + 1));
            } catch (NumberFormatException e) {
                return ItemStack.EMPTY;
            }
        }
        return new ItemStack(item, 1, meta);
    }

    /** The id core uses for {@code stack}'s item and metadata. */
    public static String itemId(ItemStack stack) {
        ResourceLocation name = stack.getItem().getRegistryName();
        String base = name == null ? "minecraft:air" : name.toString();
        int meta = stack.getHasSubtypes() ? stack.getMetadata() : 0;
        return meta == 0 ? base : base + "@" + meta;
    }

    /**
     * Null when the id is malformed or names no registered sound. Core names the note block sounds as
     * 1.13 renamed them ({@code block.note_block.*}); 1.12.2 calls them {@code block.note.*}.
     */
    public static SoundEvent sound(String id) {
        ResourceLocation parsed = id(id == null ? null : id.replace("block.note_block.", "block.note."));
        return parsed == null ? null : SoundEvent.REGISTRY.getObject(parsed);
    }

    private static String name(String id) {
        int at = id == null ? -1 : id.indexOf('@');
        return at < 0 ? id : id.substring(0, at);
    }
}
