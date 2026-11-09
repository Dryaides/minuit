package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.client.GuiDraw;
import dev.dryaides.minuit.client.WorldBubbles;
import dev.dryaides.minuit.internal.BubbleFrame;
import dev.dryaides.minuit.internal.Projection;
import dev.dryaides.minuit.internal.port.CameraPose;
import dev.dryaides.minuit.internal.port.Speaker;
import dev.dryaides.minuit.internal.port.WorldView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;

/**
 * The HUD layer of {@link WorldBubbles}: a {@link WorldView} over the 1.12.2 client, whose points go
 * through core's {@link Projection}. The camera is the view entity's interpolated feet plus
 * {@code ActiveRenderInfo}'s offset, which holds the eye height and the third-person distance as the
 * world was drawn this frame. Client only, render thread only.
 */
public final class BubbleLayer {

    private BubbleLayer() {
    }

    public static void render(float partial) {
        if (WorldBubbles.said().isEmpty()) {
            return;
        }
        GuiDraw d = new GuiDraw();
        BubbleFrame.draw(d, new Game(Minecraft.getMinecraft(), d, partial), partial);
    }

    private static double lerp(float partial, double from, double to) {
        return from + (to - from) * partial;
    }

    private static final class Game implements WorldView {

        private final Minecraft client;
        private final GuiDraw d;
        private final float partial;

        Game(Minecraft client, GuiDraw d, float partial) {
            this.client = client;
            this.d = d;
            this.partial = partial;
        }

        @Override
        public boolean inWorld() {
            return client.world != null && client.player != null && client.getRenderViewEntity() != null;
        }

        @Override
        public Speaker speaker(int entityId, float partial) {
            Entity speaker = client.world.getEntityByID(entityId);
            if (speaker == null) {
                return null;
            }
            return new Speaker(lerp(partial, speaker.lastTickPosX, speaker.posX),
                    lerp(partial, speaker.lastTickPosY, speaker.posY), lerp(partial, speaker.lastTickPosZ, speaker.posZ),
                    speaker.getEyeHeight(), speaker.height, speaker.isEntityAlive(),
                    speaker.getAlwaysRenderNameTagForRender(), speaker instanceof EntityPlayer,
                    speaker == client.getRenderViewEntity());
        }

        /** The camera itself, not the view entity's eye: in third person they differ. FOV is the player's setting. */
        @Override
        public CameraPose camera(float partial) {
            Entity view = client.getRenderViewEntity();
            Vec3d offset = ActiveRenderInfo.getCameraPosition();
            float yaw = (float) lerp(partial, view.prevRotationYaw, view.rotationYaw);
            float pitch = (float) lerp(partial, view.prevRotationPitch, view.rotationPitch);
            if (client.gameSettings.thirdPersonView == 2) {
                yaw += 180F;
                pitch = -pitch;
            }
            return new CameraPose(lerp(partial, view.lastTickPosX, view.posX) + offset.x,
                    lerp(partial, view.lastTickPosY, view.posY) + offset.y,
                    lerp(partial, view.lastTickPosZ, view.posZ) + offset.z,
                    yaw, pitch, GameScreens.fov(), d.guiWidth(), d.guiHeight());
        }

        @Override
        public boolean firstPerson() {
            return client.gameSettings.thirdPersonView == 0;
        }

        @Override
        public int guiScale() {
            return new ScaledResolution(client).getScaleFactor();
        }

        @Override
        public double[] project(double x, double y, double z) {
            return Projection.ndc(camera(this.partial), x, y, z);
        }
    }
}
