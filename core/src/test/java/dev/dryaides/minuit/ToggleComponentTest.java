package dev.dryaides.minuit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToggleComponentTest {

    private static final class Blank extends Draw {
        @Override
        protected void rect(int x0, int y0, int x1, int y1, int argb) {
        }

        @Override
        protected void string(String text, int x, int y, int argb) {
        }

        @Override
        public int width(String text) {
            return 6 * text.length();
        }

        @Override
        protected String cut(String text, int maxWidth) {
            return text;
        }

        @Override
        public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH, float alpha) {
        }

        @Override
        public void push(float x, float y, float scale) {
        }

        @Override
        public void pop() {
        }

        @Override
        public void scissor(int x0, int y0, int x1, int y1) {
        }

        @Override
        public void endScissor() {
        }

        @Override
        public void tooltip(List<String> lines, int x, int y) {
        }
    }

    private long now = 1_000_000L;

    @AfterEach
    void reset() {
        BaseComponent.clock = System::currentTimeMillis;
        BaseComponent.screenMotion = MotionStyle.SUBTLE;
    }

    /** The x the theme's frame style is asked to draw the knob at, the style itself drawing as usual. */
    private int knobLeft(ToggleComponent t, Theme theme) {
        int[] at = {Integer.MIN_VALUE};
        FrameStyle real = theme.frame();
        FrameStyle spy = (FrameStyle) Proxy.newProxyInstance(FrameStyle.class.getClassLoader(), new Class<?>[]{FrameStyle.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("toggleKnob") && args.length == 7) {
                        at[0] = (Integer) args[2];
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        t.draw(new Blank(), new Theme(theme.id(), theme.palette(), spy, theme.icons()), -100, -100, 1F);
        return at[0];
    }

    /**
     * The site's Settings card: "Show hints" is never clicked while the visitor switches theme and motion and the
     * page loops for several seconds; its knob must stay on the off side. Then a kept "on" from an earlier visit is
     * written into the field, as the site restores it: the knob rests on the on side from the first frame.
     */
    @Test
    void unflippedKnobStaysOnItsSide() {
        BaseComponent.clock = () -> this.now;
        Theme[] themes = {Themes.MINIMALIST, Themes.SLATE, Themes.LEATHER};
        ToggleComponent hints = new ToggleComponent(new Box("hints", 10, 42, 140, 12), "Show hints", false, v -> { });
        int off = 10 + 1;
        for (int loop = 0; loop < 6; loop++) {
            for (MotionStyle m : MotionStyle.values()) {
                BaseComponent.screenMotion = m;
                for (Theme theme : themes) {
                    for (int frame = 0; frame < 40; frame++) {
                        this.now += 16;
                        assertEquals(off, knobLeft(hints, theme), m + " " + theme.id() + " loop " + loop);
                    }
                }
            }
            this.now += 3000;
        }
        hints.value = true;
        for (MotionStyle m : new MotionStyle[]{MotionStyle.SUBTLE, MotionStyle.BOUNCY, MotionStyle.NONE}) {
            BaseComponent.screenMotion = m;
            this.now += 16;
            assertEquals(off + ToggleComponent.SWITCH - 10, knobLeft(hints, Themes.MINIMALIST), m.toString());
        }
    }
}
