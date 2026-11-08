package dev.dryaides.minuit.client;

import dev.dryaides.minuit.EntityView;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Game entities as the kit's {@link EntityView}; {@code GuiDraw} draws them. Client only. */
public final class MinuitEntities {

    private MinuitEntities() {
    }

    public static EntityView view(Entity entity) {
        return new EntityView(EntityType.getKey(entity.getType()).toString(), entity, false, false);
    }
}
