package dev.dryaides.minuit.port.mc;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import dev.dryaides.minuit.EntityView;
import dev.dryaides.minuit.client.GuiDraw;
import dev.dryaides.minuit.internal.EntityPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws an {@link EntityView} in a box, from the calls 1.20.1's {@code InventoryScreen.renderEntityInInventory}
 * makes, on the frame's pose: the helper of these versions takes a {@code LivingEntity} only and draws through
 * the model-view stack. The entity's angles are set for the draw and put back after, as the inventory does.
 * Client only, not API.
 */
public final class EntityDraw {

    /** Full brightness, the light the inventory draws its player with. */
    private static final int LIGHT = 15728880;

    /** Entities made from an id, once per level, never added to it. */
    private static final Map<String, Entity> MADE = new HashMap<>();
    private static Level madeIn;

    private EntityDraw() {
    }

    /** False with no level (the title screen) or an unknown id, so the caller draws its stand-in. */
    public static boolean draw(GuiDraw d, PoseStack pose, EntityView view, int x0, int y0, int x1, int y1,
                               float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (view.isBlock()) {
            return block(d, view.type(), x0, y0, x1, y1);
        }
        Entity entity = view.entity() instanceof Entity live ? live : view.isPlayer() ? mc.player : made(mc.level, view.type());
        if (entity == null) {
            return false;
        }
        EntityPose at = EntityPose.of(y0, y1, entity.getBbHeight(), pitch);
        Quaternion tilt = Vector3f.XP.rotation(at.tilt());
        Quaternion rotation = Vector3f.ZP.rotation((float) Math.PI);
        rotation.mul(tilt);
        d.scissor(x0, y0, x1, y1);
        if (entity instanceof LivingEntity living) {
            float body = living.yBodyRot;
            float head = living.yHeadRot;
            float headO = living.yHeadRotO;
            float yRot = living.getYRot();
            float xRot = living.getXRot();
            living.yBodyRot = yaw;
            living.yHeadRot = yaw;
            living.yHeadRotO = yaw;
            living.setYRot(yaw);
            living.setXRot(0F);
            render(pose, entity, (x0 + x1) / 2F, (y0 + y1) / 2F, at, rotation, tilt, 0F);
            living.yBodyRot = body;
            living.yHeadRot = head;
            living.yHeadRotO = headO;
            living.setYRot(yRot);
            living.setXRot(xRot);
        } else {
            render(pose, entity, (x0 + x1) / 2F, (y0 + y1) / 2F, at, rotation, tilt, yaw);
        }
        d.endScissor();
        return true;
    }

    /** 1.20.1's {@code InventoryScreen.renderEntityInInventory} for any entity, its yaw passed to the renderer. */
    private static void render(PoseStack pose, Entity entity, float x, float y, EntityPose at, Quaternion rotation,
                               Quaternion tilt, float yaw) {
        pose.pushPose();
        pose.translate(x, y, 50F);
        pose.scale(at.size(), at.size(), -at.size());
        pose.translate(0F, at.lift(), 0F);
        pose.mulPose(rotation);
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        tilt.conj();
        dispatcher.overrideCameraOrientation(tilt);
        dispatcher.setRenderShadow(false);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0D, 0D, 0D, yaw, 1F, pose, buffers, LIGHT));
        buffers.endBatch();
        dispatcher.setRenderShadow(true);
        pose.popPose();
        Lighting.setupFor3DItems();
    }

    private static Entity made(Level level, String type) {
        if (level == null) {
            return null;
        }
        if (level != madeIn) {
            MADE.clear();
            madeIn = level;
        }
        return MADE.computeIfAbsent(type, id -> EntityType.byString(id)
                .map(entityType -> entityType.create(level)).orElse(null));
    }

    /** The block's item model, scaled to the box: the block display path is not spiked yet. */
    private static boolean block(GuiDraw d, String id, int x0, int y0, int x1, int y1) {
        ResourceLocation parsed = GameIds.id(id);
        ItemStack stack = parsed == null ? ItemStack.EMPTY
                : GameIds.ITEMS.getOptional(parsed).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) {
            return false;
        }
        float scale = Math.min(x1 - x0, y1 - y0) / 16F;
        // depth unscaled, so the block keeps the depth an item has and later layers still cover it
        d.push(x0 + (x1 - x0 - 16 * scale) / 2F, y0 + (y1 - y0 - 16 * scale) / 2F, scale);
        d.renderItem(stack, 0, 0);
        d.pop();
        return true;
    }
}
