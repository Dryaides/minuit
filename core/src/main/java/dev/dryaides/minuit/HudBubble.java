package dev.dryaides.minuit;

import java.util.List;

/**
 * One speech bubble for a mod's HUD element, where no panel and no entity can host {@link BubbleComponent} or
 * {@code WorldBubbles}. It keeps the phrase and its time, and draws through {@link Bubble}, so it looks like every
 * other bubble. Pure Java: the game side only hands it a {@code Draw}.
 *
 * <pre>{@code
 * HudBubble bubble = new HudBubble();
 * bubble.say("Only 10% left!");
 * ClientTickEvents.END_CLIENT_TICK.register(mc -> bubble.tick());
 * HudElementRegistry.addLast(id, (g, delta) ->
 *         bubble.draw(new GuiDraw(g), x, y, g.guiWidth(), g.guiHeight(), delta.getGameTimeDeltaPartialTick(false)));
 * }</pre>
 *
 * The tail points at (anchorX, anchorY) from above, and the bubble stays on screen.
 */
public final class HudBubble {
    private BubbleStyle style = BubbleStyle.CLASSIC;
    private boolean reducedMotion;
    private String text;
    private int life;
    private int age;
    private List<String> lines;
    private String linesFor;

    /** Shows a phrase for as long as it takes to read. */
    public void say(String phrase) {
        say(phrase, Bubble.lifeTicks(phrase == null ? 0 : phrase.length(), 0));
    }

    /**
     * Shows a phrase for this many ticks. The same phrase again only extends the time, so an alert that keeps
     * firing does not blink; a new one replaces the old at once.
     */
    public void say(String phrase, int lifeTicks) {
        if (phrase == null || phrase.isBlank() || lifeTicks <= 0) {
            clear();
            return;
        }
        if (phrase.equals(text) && showing()) {
            // Past the fade in, and not back into it. The fade out starts again from the new end.
            int shown = Math.min(age, FADE_IN_TICKS);
            age = shown;
            life = Math.max(lifeTicks, shown + 1);
            return;
        }
        text = phrase;
        life = lifeTicks;
        age = 0;
    }

    public void clear() {
        text = null;
        lines = null;
        linesFor = null;
        life = 0;
        age = 0;
    }

    /** Once per game tick. */
    public void tick() {
        if (text != null && ++age >= life) {
            clear();
        }
    }

    public boolean showing() {
        return text != null;
    }

    public String text() {
        return text;
    }

    public HudBubble style(BubbleStyle s) {
        style = s == null ? BubbleStyle.CLASSIC : s;
        return this;
    }

    /** With reduced motion the bubble neither fades in nor out. */
    public HudBubble reducedMotion(boolean reduced) {
        reducedMotion = reduced;
        return this;
    }

    /** How visible it is on this frame, 0 to 1. */
    public float alpha(float partial) {
        if (text == null) {
            return 0;
        }
        return reducedMotion ? 1 : Bubble.alpha(age + partial, life);
    }

    /** Where it would be drawn, or null when nothing shows. */
    public Box box(Draw d, int anchorX, int anchorY, int screenWidth, int screenHeight) {
        if (text == null) {
            return null;
        }
        return Bubble.box(d, lines(d), anchorX, anchorY, screenWidth, screenHeight);
    }

    public void draw(Draw d, int anchorX, int anchorY, int screenWidth, int screenHeight, float partial) {
        if (text == null) {
            return;
        }
        float a = alpha(partial);
        if (a <= 0) {
            return;
        }
        Bubble.draw(d, style, lines(d), Bubble.box(d, lines(d), anchorX, anchorY, screenWidth, screenHeight), anchorX, a);
    }

    private List<String> lines(Draw d) {
        if (lines == null || !text.equals(linesFor)) {
            lines = Bubble.wrap(d, text, Bubble.MAX_LINE_WIDTH, Bubble.MAX_LINES);
            linesFor = text;
        }
        return lines;
    }

    /** Bubble.alpha rises over 4 ticks. */
    private static final int FADE_IN_TICKS = 4;
}
