package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.HostState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Placement against the 26.1.2 HUD: hotbar at H-22, hearts from H-39, harmful effects down to y 51. */
class HudLayerTest {

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
        public String cut(String text, int maxWidth) {
            return text;
        }

        @Override
        public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH, float alpha) {
        }

        @Override
        protected void pushPose(float x, float y, float scale) {
        }

        @Override
        protected void popPose() {
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

    private static final class Plain extends HudPart {
        Plain(String id, HudAnchor anchor, int w, int h) {
            super("test", id, anchor, w, h);
        }

        @Override
        protected void draw(Draw d, Theme theme, int x, int y, float partial) {
        }
    }

    private static VanillaHud survival(int w, int h) {
        VanillaHud hud = new VanillaHud();
        hud.guiWidth = w;
        hud.guiHeight = h;
        return hud;
    }

    @Test
    void bottomCentreClimbsAboveTheHearts() {
        VanillaHud hud = survival(427, 240);
        HudLayer layer = new HudLayer();
        Plain bar = layer.add(new Plain("bar", HudAnchor.BOTTOM_CENTER, 81, 12));
        layer.render(new Blank(), hud, 0F);
        assertEquals(240 - 39 - 2 - 12, bar.y());
    }

    @Test
    void aPartAsWideAsTheHotbarLinesUpWithItAtAnOddWidth() {
        VanillaHud hud = survival(427, 240);
        hud.statusBars = false;
        hud.contextualBar = false;
        HudLayer layer = new HudLayer();
        Plain row = layer.add(new Plain("row", HudAnchor.BOTTOM_CENTER, 182, 22));
        layer.render(new Blank(), hud, 0F);
        assertEquals(427 / 2 - 91, row.x());
        assertEquals(240 - 22 - 2 - 22, row.y());
    }

    @Test
    void topRightStacksBelowHarmfulEffectsWithoutOverlap() {
        VanillaHud hud = survival(640, 360);
        hud.goodEffects = 2;
        hud.badEffects = 1;
        HudLayer layer = new HudLayer();
        Plain first = layer.add(new Plain("a", HudAnchor.TOP_RIGHT, 40, 20));
        Plain second = layer.add(new Plain("b", HudAnchor.TOP_RIGHT, 40, 20));
        layer.render(new Blank(), hud, 0F);
        assertEquals(51 + HudLayer.GAP, first.y());
        assertEquals(first.y() + 20 + HudLayer.GAP, second.y());
        assertEquals(640 - HudAnchor.MARGIN - 40, second.x());
    }

    @Test
    void hidingAPartLetsTheNextOneTakeItsPlace() {
        VanillaHud hud = survival(640, 360);
        HudLayer layer = new HudLayer();
        boolean[] shown = {true};
        Plain first = layer.add(new Plain("a", HudAnchor.TOP_LEFT, 40, 20));
        first.visibleWhen(() -> shown[0]);
        Plain second = layer.add(new Plain("b", HudAnchor.TOP_LEFT, 40, 20));
        layer.render(new Blank(), hud, 0F);
        assertTrue(second.y() > first.y());
        shown[0] = false;
        layer.render(new Blank(), hud, 0F);
        assertEquals(HudAnchor.MARGIN, second.y());
    }

    private static final class Ticks extends BaseComponent {
        float seen;

        Ticks() {
            super(0, 0, 10, 10);
        }

        @Override
        protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
            this.seen = this.delta;
        }
    }

    @Test
    void aComponentPartAfterAHiddenSpellGetsOneTickNotTheTimeAway() {
        long[] now = {1000};
        java.util.function.LongSupplier was = BaseComponent.clock();
        HostState.clock(() -> now[0]);
        try {
            HudLayer layer = new HudLayer();
            boolean[] on = {true};
            Ticks ticks = new Ticks();
            layer.add(new HudWidget<>("test", "t", HudAnchor.TOP_LEFT, ticks).visibleWhen(() -> on[0]));
            layer.render(new Blank(), survival(640, 360), 0F);
            now[0] += 100;
            layer.render(new Blank(), survival(640, 360), 0F);
            assertEquals(2F, ticks.seen);
            on[0] = false;
            now[0] += 30_000;
            layer.render(new Blank(), survival(640, 360), 0F);
            on[0] = true;
            now[0] += 30_000;
            layer.render(new Blank(), survival(640, 360), 0F);
            assertEquals(1F, ticks.seen);
            VanillaHud hidden = survival(640, 360);
            hidden.hidden = true;
            now[0] += 30_000;
            layer.render(new Blank(), hidden, 0F);
            now[0] += 30_000;
            layer.render(new Blank(), survival(640, 360), 0F);
            assertEquals(1F, ticks.seen);
        } finally {
            HostState.clock(was);
        }
    }
}
