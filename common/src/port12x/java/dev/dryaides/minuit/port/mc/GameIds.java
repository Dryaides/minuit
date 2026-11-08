package dev.dryaides.minuit.port.mc;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Ids from text with the generation's id type ({@code ResourceLocation} on 1.20.1 and 1.21.1). Not API. */
public final class GameIds {

    /** The item registry, typed as the {@code Registry} every generation has, so shared code can iterate and key it. */
    public static final Registry<Item> ITEMS = BuiltInRegistries.ITEM;

    private GameIds() {
    }

    /** Null when the text is no valid id. */
    public static ResourceLocation id(String text) {
        return ResourceLocation.tryParse(text);
    }

    /** {@code namespace:path}; tryParse, as the two-string factory is 1.21 only. */
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
        return parsed == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(parsed).orElse(null);
    }

    /** Null when the tag id is malformed. */
    public static TagKey<Item> itemTag(String tag) {
        ResourceLocation parsed = id(tag);
        return parsed == null ? null : TagKey.create(Registries.ITEM, parsed);
    }
}
