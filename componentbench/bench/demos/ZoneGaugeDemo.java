package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.ZoneGaugeComponent;
import dev.dryaides.minuit.ZoneGaugeComponent.Role;
import dev.dryaides.minuit.ZoneGaugeComponent.Sweep;
import dev.dryaides.minuit.ZoneGaugeComponent.Zone;
import java.util.List;

/** The forging bar of Overgeared and other timing minigames: fill gauge, zoned strip, sweeping cursor. */
public final class ZoneGaugeDemo implements Demo {
    private static final int W = 196;
    private static final int H = 42;

    private static ZoneGaugeComponent forge(float fill) {
        ZoneGaugeComponent bar = new ZoneGaugeComponent(new Box("bar", 7, 7, 182, 28))
                .zones(ZoneGaugeComponent.centred(0.30F, 0.10F)).speed(1000, Sweep.PING_PONG);
        bar.fill = fill;
        return bar;
    }

    @Override
    public void states(Stage stage) {
        stage.state("cursor in perfect", W, H, s -> {
            s.noSurface();
            ZoneGaugeComponent bar = s.add(forge(0.4F));
            s.settle(0);
            bar.start();
            s.waitMs(500);
        });

        stage.state("cursor in good", W, H, s -> {
            s.noSurface();
            ZoneGaugeComponent bar = s.add(forge(0.4F));
            s.settle(0);
            bar.start();
            s.waitMs(380);
        });

        stage.state("cursor in miss, coming back", W, H, s -> {
            s.noSurface();
            ZoneGaugeComponent bar = s.add(forge(0.75F));
            s.settle(0);
            bar.start();
            s.waitMs(1850);
        });

        stage.state("struck with space says the zone", W, H + 16, s -> {
            s.noSurface();
            ZoneGaugeComponent bar = s.add(forge(0.6F));
            String[] said = {"-"};
            bar.onHit(z -> said[0] = z == null ? "gap" : z.role().name());
            s.caption(() -> "hit: " + said[0]);
            s.settle(0);
            bar.start();
            s.key(InputKey.TAB);
            s.waitMs(470);
            s.key(InputKey.SPACE);
            bar.stop();
        });

        stage.state("four zones, own widths, no fill", W, H, s -> {
            s.noSurface();
            ZoneGaugeComponent bar = s.add(new ZoneGaugeComponent(new Box("bar", 7, 7, 182, 24))
                    .zones(List.of(new Zone(0.10F, 0.30F, Role.GOOD), new Zone(0.30F, 0.36F, Role.PERFECT),
                            new Zone(0.70F, 0.90F, Role.GOOD), new Zone(0.80F, 0.84F, Role.PERFECT)))
                    .speed(800, Sweep.ONE_WAY));
            bar.fill = -1;
            s.settle(0);
            bar.start();
            s.waitMs(660);
        });

        stage.state("tall strip, full gauge", W, 48, s -> {
            s.noSurface();
            ZoneGaugeComponent bar = s.add(new ZoneGaugeComponent(new Box("bar", 7, 7, 182, 34))
                    .zones(ZoneGaugeComponent.centred(0.5F, 0.16F)).speed(1000, Sweep.PING_PONG));
            bar.fill = 1F;
            s.settle(0);
            bar.start();
            s.waitMs(300);
        });
    }
}
