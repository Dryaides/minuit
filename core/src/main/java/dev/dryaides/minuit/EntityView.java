package dev.dryaides.minuit;

/**
 * An entity to show in a preview: by type id from core, the local player, a block shown as its block
 * entity, or a live entity from {@code client.MinuitEntities.view}.
 *
 * @param type   the entity type id, or the block id when {@link #isBlock}; empty for the local player
 * @param entity null, or the game's {@code Entity}, opaque to core
 */
public record EntityView(String type, Object entity, boolean isPlayer, boolean isBlock) {

    public static EntityView of(String type) {
        return new EntityView(type, null, false, false);
    }

    /** The local player as the game draws it, skin and armour included. */
    public static EntityView player() {
        return new EntityView("", null, true, false);
    }

    public static EntityView block(String blockId) {
        return new EntityView(blockId, null, false, true);
    }
}
