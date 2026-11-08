package dev.dryaides.minuit.port.mc;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.dryaides.minuit.client.GuiDraw;
import dev.dryaides.minuit.client.WorldBubbles;
import dev.dryaides.minuit.internal.BubbleFrame;
import dev.dryaides.minuit.internal.Projection;
import dev.dryaides.minuit.internal.port.CameraPose;
import dev.dryaides.minuit.internal.port.Speaker;
import dev.dryaides.minuit.internal.port.WorldView;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The HUD layer of {@link WorldBubbles}: a {@link WorldView} over the 1.19.2 or 1.18.2 client. These have no
 * {@code projectPointToScreen}, so the points go through core's {@link Projection} from the main camera
 * as the world was drawn this frame (third person included). Client only, render thread only.
 */
public final class BubbleLayer {

    private BubbleLayer() {
    }

    public static void render(PoseStack pose, float partial) {
        if (WorldBubbles.said().isEmpty()) {
            return;
        }
        GuiDraw d = new GuiDraw(pose);
        BubbleFrame.draw(d, new Game(Minecraft.getInstance(), d, partial), partial);
    }

    private record Game(Minecraft client, GuiDraw d, float partial) implements WorldView {

        @Override
        public boolean inWorld() {
            return client.level != null && client.player != null;
        }

        @Override
        public Speaker speaker(int entityId, float partial) {
            Entity speaker = client.level.getEntity(entityId);
            if (speaker == null) {
                return null;
            }
            Vec3 at = speaker.getPosition(partial);
            return new Speaker(at.x, at.y, at.z, speaker.getEyeHeight(), speaker.getBbHeight(), speaker.isAlive(),
                    speaker.shouldShowName(), speaker instanceof Player, speaker == client.getCameraEntity());
        }

        /** The camera itself, not the camera entity's eye: in third person they differ. FOV is the player's setting. */
        @Override
        public CameraPose camera(float partial) {
            Camera camera = client.gameRenderer.getMainCamera();
            Vec3 eye = camera.getPosition();
            return new CameraPose(eye.x, eye.y, eye.z, camera.getYRot(), camera.getXRot(), GameScreens.fov(),
                    d.guiWidth(), d.guiHeight());
        }

        @Override
        public boolean firstPerson() {
            return client.options.getCameraType().isFirstPerson();
        }

        @Override
        public int guiScale() {
            return (int) client.getWindow().getGuiScale();
        }

        @Override
        public double[] project(double x, double y, double z) {
            return Projection.ndc(camera(this.partial), x, y, z);
        }
    }
}
