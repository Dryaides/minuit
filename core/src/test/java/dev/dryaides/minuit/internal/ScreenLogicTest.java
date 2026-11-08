package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.Slide;
import dev.dryaides.minuit.Themes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScreenLogicTest {

    /** Draws nothing. */
    static final class Blank extends Draw {
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

    static PanelComponent panel(BaseComponent... parts) {
        return new PanelComponent(Themes.MINIMALIST, null, 0, 0) {
            {
                for (BaseComponent widget : parts) {
                    add(widget);
                }
            }

            @Override
            public int width() {
                return 200;
            }

            @Override
            public int height() {
                return 100;
            }

            @Override
            protected void drawBackground(Draw d, int mx, int my) {
            }
        };
    }

    /** The oracle is {@link Slide#toPanel}: on its first frame a panel from the right sits 50 to the right. */
    @Test
    void aClickMidGlideLandsOnTheWidgetDrawnUnderIt() {
        int[] pressed = {0};
        PanelComponent panel = panel(new ButtonComponent(new Box("b", 40, 30, 20, 20), "", () -> pressed[0]++));
        LongSupplier clock = BaseComponent.clock;
        BaseComponent.clock = () -> 1000L;
        try {
            ScreenLogic logic = new ScreenLogic(new HostCore(null));
            logic.slideFrom(Slide.RIGHT);
            logic.draw(new Blank(), panel, 0, 0);
            double[] drawnAt = {45, 35};
            double screenX = drawnAt[0] + 50;
            assertEquals(drawnAt[0], Slide.RIGHT.toPanel(screenX, drawnAt[1], 0, 200, 100)[0]);
            logic.mouseClicked(panel, screenX, drawnAt[1], 0);
            logic.mouseReleased(panel, screenX, drawnAt[1], 0);
        } finally {
            BaseComponent.clock = clock;
        }
        assertEquals(1, pressed[0]);
    }
}
