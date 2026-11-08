package dev.dryaides.minuit.port.mc;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Ids from text with the generation's id type ({@code Identifier} on 26.x). Not API. */
public final class GameIds {

    /** The item registry, typed as the {@code Registry} every generation has, so shared code can iterate and key it. */
    public static final Registry<Item> ITEMS = BuiltInRegistries.ITEM;

    private GameIds() {
    }

    /** Null when the text is no valid id. */
    public static Identifier id(String text) {
        return Identifier.tryParse(text);
    }

    /** Null when the id is malformed or names no registered item. */
    public static Item item(String id) {
        Identifier parsed = id(id);
        return parsed == null ? null : ITEMS.getOptional(parsed).orElse(null);
    }

    /** Null when the id is malformed or names no registered sound. */
    public static SoundEvent sound(String id) {
        Identifier parsed = id(id);
        return parsed == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(parsed).orElse(null);
    }

    /** Null when the tag id is malformed. */
    public static TagKey<Item> itemTag(String tag) {
        Identifier parsed = id(tag);
        return parsed == null ? null : TagKey.create(Registries.ITEM, parsed);
    }
}
