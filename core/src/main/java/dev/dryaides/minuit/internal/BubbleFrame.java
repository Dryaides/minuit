package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.Bubble;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.client.WorldBubbles;
import dev.dryaides.minuit.internal.port.CameraPose;
import dev.dryaides.minuit.internal.port.Speaker;
import dev.dryaides.minuit.internal.port.WorldView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One frame of {@link WorldBubbles}: drops the expired and the gone, places each bubble over its
 * speaker's mouth and draws them far to near. On the HUD rather than in the world so the words are
 * the crisp GUI font at any distance; the price is that a bubble shows through a wall, which the
 * range of whoever says it bounds. Render thread only. Not API.
 */
public final class BubbleFrame {

    /**
     * Over the feet plus the collision box: the top of a name tag, which vanilla draws 0.5 over the
     * box and about 0.25 tall, so the tail stops above the name instead of on it.
     */
    private static final double NAME_TAG_TOP = 0.85;

    private record Shown(WorldBubbles.Said said, double x, double y, double z, double distance, float age) {
    }

    private BubbleFrame() {
    }

    /**
     * Where the tail points, in blocks over the feet: 30 % above the eyes. The collision box is no
     * guide (a parrot sitting on a post is 0.9 tall but its crest is at 0.6, and the bubble floated a
     * parrot-height over it); the eyes follow the head, sitting or sneaking.
     */
    static double mouthHeight(Speaker speaker) {
        double mouth = speaker.eyeHeight() * 1.3;
        if (!speaker.camera() && (speaker.player() || speaker.showsName())) {
            return Math.max(mouth, speaker.bbHeight() + NAME_TAG_TOP);
        }
        return mouth;
    }

    /** Twice the GUI size up to 4 blocks away, then shrinking with distance down to 60 %: a far speaker keeps a small bubble. */
    static float bubbleScale(double distance) {
        return (float) Maths.clamp(8.0 / Math.max(distance, 1.0E-3), 0.6, 2.0);
    }

    public static void draw(Draw d, WorldView view, float partial) {
        if (WorldBubbles.said().isEmpty()) {
            return;
        }
        if (!view.inWorld()) {
            WorldBubbles.clearAll();
            return;
        }
        CameraPose camera = view.camera(partial);
        long now = System.currentTimeMillis();
        List<Shown> shown = new ArrayList<>();
        for (WorldBubbles.Said said : List.copyOf(WorldBubbles.said())) {
            float age = (now - said.startMillis()) / 50F;
            Speaker speaker = view.speaker(said.entityId(), partial);
            if (age >= said.lifeTicks() || speaker == null || !speaker.alive()) {
                WorldBubbles.drop(said);
                continue;
            }
            if (speaker.camera() && view.firstPerson()) {
                continue;
            }
            double headY = speaker.y() + mouthHeight(speaker);
            double dx = speaker.x() - camera.x();
            double dy = headY - camera.y();
            double dz = speaker.z() - camera.z();
            shown.add(new Shown(said, speaker.x(), headY, speaker.z(), Math.sqrt(dx * dx + dy * dy + dz * dz), age));
        }
        // Far ones first, so the nearest speaker's words are drawn on top.
        shown.sort(Comparator.comparingDouble((Shown entry) -> -entry.distance()));
        int guiScale = Math.max(1, view.guiScale());
        for (Shown entry : shown) {
            WorldBubbles.Said said = entry.said();
            double[] ndc = view.project(entry.x(), entry.y(), entry.z());
            if (ndc[2] > 1.0 || Math.abs(ndc[0]) > 1.1 || Math.abs(ndc[1]) > 1.1) {
                continue;
            }
            // A whole number of screen pixels per bubble pixel, so the pixel-art bubble stays crisp.
            float scale = Math.max(1, Math.round(bubbleScale(entry.distance()) * guiScale)) / (float) guiScale;
            int x = (int) Math.round((ndc[0] + 1.0) / 2.0 * camera.guiWidth() / scale);
            int y = (int) Math.round((1.0 - ndc[1]) / 2.0 * camera.guiHeight() / scale);
            List<String> lines = Bubble.wrap(d, said.text(), Bubble.MAX_LINE_WIDTH, Bubble.MAX_LINES);
            d.push(0, 0, scale);
            Box box = Bubble.box(d, lines, x, y, (int) (camera.guiWidth() / scale), (int) (camera.guiHeight() / scale));
            Bubble.draw(d, said.style(), lines, box, x, Bubble.alpha(entry.age(), said.lifeTicks()));
            d.pop();
        }
    }
}
