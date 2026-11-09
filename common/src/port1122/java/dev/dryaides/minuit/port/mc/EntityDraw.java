package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.EntityView;
import dev.dryaides.minuit.client.GuiDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import dev.dryaides.minuit.internal.EntityPose;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws an {@link EntityView} in a box with the transform of 1.20.1's {@code renderEntityInInventory}, on
 * the GL matrix: 1.12.2's {@code GuiInventory.drawEntityOnScreen} takes a living entity only and turns it
 * toward the mouse. The yaw is core's (180 faces the viewer). The entity's angles are set for the draw and
 * put back after. Client only, not API.
 */
public final class EntityDraw {

    /** Entities made from an id, once per world, never spawned in it. */
    private static final Map<String, Entity> MADE = new HashMap<>();
    private static World madeIn;

    private EntityDraw() {
    }

    /** False with no world (the title screen) or an unknown id, so the caller draws its stand-in. */
    public static boolean draw(GuiDraw d, EntityView view, int x0, int y0, int x1, int y1, float yaw, float pitch) {
        Minecraft mc = Minecraft.getMinecraft();
        if (view.isBlock()) {
            return block(d, view.type(), x0, y0, x1, y1);
        }
        Entity entity = view.entity() instanceof Entity ? (Entity) view.entity()
                : view.isPlayer() ? mc.player : made(mc.world, view.type());
        if (entity == null) {
            return false;
        }
        EntityPose at = EntityPose.of(y0, y1, entity.height, pitch);
        d.scissor(x0, y0, x1, y1);
        if (entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            float body = living.renderYawOffset;
            float bodyO = living.prevRenderYawOffset;
            float head = living.rotationYawHead;
            float headO = living.prevRotationYawHead;
            float yRot = living.rotationYaw;
            float yRotO = living.prevRotationYaw;
            float xRot = living.rotationPitch;
            float xRotO = living.prevRotationPitch;
            living.renderYawOffset = living.prevRenderYawOffset = yaw;
            living.rotationYawHead = living.prevRotationYawHead = yaw;
            living.rotationYaw = living.prevRotationYaw = yaw;
            living.rotationPitch = living.prevRotationPitch = 0F;
            render(entity, (x0 + x1) / 2F, (y0 + y1) / 2F, at, 0F);
            living.renderYawOffset = body;
            living.prevRenderYawOffset = bodyO;
            living.rotationYawHead = head;
            living.prevRotationYawHead = headO;
            living.rotationYaw = yRot;
            living.prevRotationYaw = yRotO;
            living.rotationPitch = xRot;
            living.prevRotationPitch = xRotO;
        } else {
            render(entity, (x0 + x1) / 2F, (y0 + y1) / 2F, at, yaw);
        }
        d.endScissor();
        return true;
    }

    /** The 1.20.1 transform, in GL calls; the lights are set as drawEntityOnScreen sets them. */
    private static void render(Entity entity, float x, float y, EntityPose at, float yaw) {
        GlStateManager.enableColorMaterial();
        GlStateManager.enableDepth();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 50F);
        GlStateManager.scale(at.size(), at.size(), -at.size());
        GlStateManager.translate(0F, at.lift(), 0F);
        GlStateManager.rotate(180F, 0F, 0F, 1F);
        GlStateManager.rotate((float) Math.toDegrees(at.tilt()), 1F, 0F, 0F);
        GlStateManager.rotate(135F, 0F, 1F, 0F);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.rotate(-135F, 0F, 1F, 0F);
        RenderManager manager = Minecraft.getMinecraft().getRenderManager();
        manager.setPlayerViewY(180F);
        manager.setRenderShadow(false);
        manager.renderEntity(entity, 0D, 0D, 0D, yaw, 1F, false);
        manager.setRenderShadow(true);
        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    private static Entity made(World world, String type) {
        if (world == null) {
            return null;
        }
        if (world != madeIn) {
            MADE.clear();
            madeIn = world;
        }
        return MADE.computeIfAbsent(type, id -> {
            ResourceLocation parsed = GameIds.id(id);
            return parsed == null ? null : EntityList.createEntityByIDFromName(parsed, world);
        });
    }

    /** The block's item model, scaled to the box. */
    private static boolean block(GuiDraw d, String id, int x0, int y0, int x1, int y1) {
        ItemStack stack = GameIds.stack(id);
        if (stack.isEmpty()) {
            return false;
        }
        float scale = Math.min(x1 - x0, y1 - y0) / 16F;
        d.push(x0 + (x1 - x0 - 16 * scale) / 2F, y0 + (y1 - y0 - 16 * scale) / 2F, scale);
        d.renderItem(stack, 0, 0);
        d.pop();
        return true;
    }
}
