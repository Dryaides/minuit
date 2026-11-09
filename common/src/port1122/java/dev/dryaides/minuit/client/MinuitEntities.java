package dev.dryaides.minuit.client;

import dev.dryaides.minuit.EntityView;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;

/** Game entities as the kit's {@link EntityView}; {@code GuiDraw} draws them. Client only. */
public final class MinuitEntities {

    private MinuitEntities() {
    }

    public static EntityView view(Entity entity) {
        // Players have no entity list entry in 1.12.2.
        ResourceLocation key = EntityList.getKey(entity);
        return new EntityView(key != null ? key.toString() : "minecraft:player", entity, false, false);
    }
}
