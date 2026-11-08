package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.EntityView;
import dev.dryaides.minuit.internal.EntityPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws an {@link EntityView} in a box, from the vanilla calls {@code InventoryScreen} uses, since the
 * angle variant of that helper is NeoForge's only. Client only, not API.
 */
public final class EntityDraw {

    /** Entities made from an id, once per level, never added to it. */
    private static final Map<String, Entity> MADE = new HashMap<>();
    private static Level madeIn;

    private EntityDraw() {
    }

    /** False with no level (the title screen) or an unknown id, so the caller draws its stand-in. */
    public static boolean draw(GuiGraphicsExtractor g, EntityView view, int x0, int y0, int x1, int y1, float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (view.isBlock()) {
            return block(g, view.type(), x0, y0, x1, y1);
        }
        Entity entity = view.entity() instanceof Entity live ? live : view.isPlayer() ? mc.player : made(mc.level, view.type());
        if (entity == null) {
            return false;
        }
        EntityRenderState state = mc.getEntityRenderDispatcher().getRenderer(entity).createRenderState(entity, 1F);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = yaw;
            living.yRot = 0F;
            living.xRot = 0F;
            living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
            living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
            living.scale = 1F;
        }
        EntityPose pose = EntityPose.of(y0, y1, state.boundingBoxHeight, pitch);
        Quaternionf tilt = new Quaternionf().rotateX(pose.tilt());
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).mul(tilt);
        g.entity(state, pose.size(), new Vector3f(0F, pose.lift(), 0F), rotation, tilt, x0, y0, x1, y1);
        return true;
    }

    private static Entity made(Level level, String type) {
        if (level == null) {
            return null;
        }
        if (level != madeIn) {
            MADE.clear();
            madeIn = level;
        }
        return MADE.computeIfAbsent(type, id -> {
            Identifier parsed = GameIds.id(id);
            EntityType<?> entityType = parsed == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(parsed).orElse(null);
            return entityType == null ? null : entityType.create(level, EntitySpawnReason.LOAD);
        });
    }

    /** The block's item model, scaled to the box: the block display path is not spiked yet. */
    private static boolean block(GuiGraphicsExtractor g, String id, int x0, int y0, int x1, int y1) {
        Identifier parsed = GameIds.id(id);
        ItemStack stack = parsed == null ? ItemStack.EMPTY
                : GameIds.ITEMS.getOptional(parsed).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) {
            return false;
        }
        float scale = Math.min(x1 - x0, y1 - y0) / 16F;
        g.pose().pushMatrix();
        g.pose().translate(x0 + (x1 - x0 - 16 * scale) / 2F, y0 + (y1 - y0 - 16 * scale) / 2F);
        g.pose().scale(scale, scale);
        g.item(stack, 0, 0);
        g.pose().popMatrix();
        return true;
    }
}
