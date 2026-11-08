package dev.dryaides.minuit.port.mc;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Ids from text with the generation's id type ({@code ResourceLocation} on 1.19.2 and 1.18.2). Not API. */
public final class GameIds {

    /** The item registry, typed as the {@code Registry} every generation has, so shared code can iterate and key it. */
    public static final Registry<Item> ITEMS = Registry.ITEM;

    private GameIds() {
    }

    /** Null when the text is no valid id. */
    public static ResourceLocation id(String text) {
        return ResourceLocation.tryParse(text);
    }

    /** {@code namespace:path}; tryParse, as the two-string constructor throws on a bad path. */
    public static ResourceLocation of(String namespace, String path) {
        return ResourceLocation.tryParse(namespace + ":" + path);
    }

    /** Null when the id is malformed or names no registered item. */
    public static Item item(String id) {
        ResourceLocation parsed = id(id);
        return parsed == null ? null : ITEMS.getOptional(parsed).orElse(null);
    }

    /** Null when the id is malformed or names no registered sound. */
    public static SoundEvent sound(String id) {
        ResourceLocation parsed = id(id);
        return parsed == null ? null : Registry.SOUND_EVENT.getOptional(parsed).orElse(null);
    }

    /** Null when the tag id is malformed. */
    public static TagKey<Item> itemTag(String tag) {
        ResourceLocation parsed = id(tag);
        return parsed == null ? null : TagKey.create(Registry.ITEM_REGISTRY, parsed);
    }
}
