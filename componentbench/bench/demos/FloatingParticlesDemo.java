package bench.demos;

import dev.dryaides.minuit.internal.HostState;
import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.AtlasIcon;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.FloatingParticlesComponent;

/** Hearts rising over a panel after a burst. */
public final class FloatingParticlesDemo implements Demo {

    private static final int W = 160;
    private static final int H = 100;

    private static FloatingParticlesComponent hearts() {
        return new FloatingParticlesComponent(new Box("hearts", 20, 10, 120, 80),
                AtlasIcon.whole("minecraft:textures/particle/heart.png", 8)).count(6).lifeMs(1400).seed(7L);
    }

    @Override
    public void states(Stage stage) {
        stage.state("rising", W, H, s -> {
            FloatingParticlesComponent p = s.add(hearts());
            p.burst();
            s.waitMs(500);
            s.settle(0);
        });
        stage.state("reduced motion shows none", W, H, s -> {
            FloatingParticlesComponent p = s.add(hearts());
            HostState.reducedMotion(true);
            try {
                p.burst();
            } finally {
                HostState.reducedMotion(false);
            }
            s.paint((d, t) -> d.text(p.alive() == 0 ? "no particle" : "FAIL: " + p.alive(), 4, 4, t.palette().text()));
        });
    }
}
