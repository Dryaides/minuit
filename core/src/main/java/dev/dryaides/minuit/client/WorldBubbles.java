package dev.dryaides.minuit.client;

import dev.dryaides.minuit.Bubble;
import dev.dryaides.minuit.BubbleStyle;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Speech bubbles over entities, drawn by minuit's own HUD layer at the point of the screen where
 * the speaker's head is: a mod says something over an entity and never wires a layer.
 *
 * <pre>
 *   WorldBubbles.say(entity.getId(), "Sit!");
 * </pre>
 *
 * A new phrase over the same entity replaces its bubble, whichever mod said it. Client only, any
 * thread.
 */
public final class WorldBubbles {

    /** What an entity says: from {@code startMillis}, for {@code lifeTicks}, fading in and out. */
    public record Said(int entityId, String text, int lifeTicks, long startMillis, BubbleStyle style) {
    }

    private static final Map<Integer, Said> SAID = new ConcurrentHashMap<>();

    private WorldBubbles() {
    }

    /** Over the entity with this network id, as long as it takes to read. */
    public static void say(int entityId, String text) {
        say(entityId, text, Bubble.lifeTicks(text.length(), 0));
    }

    public static void say(int entityId, String text, int lifeTicks) {
        say(entityId, text, lifeTicks, BubbleStyle.CLASSIC);
    }

    public static void say(int entityId, String text, int lifeTicks, BubbleStyle style) {
        SAID.put(entityId, new Said(entityId, text, Math.max(1, lifeTicks), System.currentTimeMillis(), style));
    }

    public static void clear(int entityId) {
        SAID.remove(entityId);
    }

    public static void clearAll() {
        SAID.clear();
    }

    /** The bubbles said and not yet dropped, for the layer that draws them. */
    public static Collection<Said> said() {
        return Collections.unmodifiableCollection(SAID.values());
    }

    /** Drops this bubble, unless a newer phrase replaced it meanwhile. */
    public static void drop(Said said) {
        SAID.remove(said.entityId(), said);
    }
}
