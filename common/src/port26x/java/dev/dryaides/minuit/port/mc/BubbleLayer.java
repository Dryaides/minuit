package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.client.GuiDraw;
import dev.dryaides.minuit.client.WorldBubbles;
import dev.dryaides.minuit.internal.BubbleFrame;
import dev.dryaides.minuit.internal.port.CameraPose;
import dev.dryaides.minuit.internal.port.Speaker;
import dev.dryaides.minuit.internal.port.WorldView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** The HUD layer of {@link WorldBubbles}, under the chat: a {@link WorldView} over the 26.x client. Client only, render thread only. */
public final class BubbleLayer {

    private BubbleLayer() {
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (WorldBubbles.said().isEmpty()) {
            return;
        }
        GuiDraw d = new GuiDraw(graphics);
        BubbleFrame.draw(d, new Game(Minecraft.getInstance(), d), delta.getGameTimeDeltaPartialTick(false));
    }

    private record Game(Minecraft client, GuiDraw d) implements WorldView {

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

        @Override
        public CameraPose camera(float partial) {
            Entity camera = client.getCameraEntity();
            Vec3 eye = camera == null ? Vec3.ZERO : camera.getEyePosition(partial);
            float yaw = camera == null ? 0F : camera.getViewYRot(partial);
            float pitch = camera == null ? 0F : camera.getViewXRot(partial);
            return new CameraPose(eye.x, eye.y, eye.z, yaw, pitch, client.options.fov().get(), d.guiWidth(), d.guiHeight());
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
            Vec3 ndc = client.gameRenderer.projectPointToScreen(new Vec3(x, y, z));
            return new double[] {ndc.x, ndc.y, ndc.z};
        }
    }
}
