package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.AsyncImage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.SpriteIcon;
import dev.dryaides.minuit.WheelComponent;
import dev.dryaides.minuit.WheelComponent.Segment;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The prize wheel. Only the checks a state needs to show what it claims; the pictures are the point. */
public final class WheelDemo implements Demo {
    private static final int W = 150;
    private static final int H = 150;
    private static final String GEM = "minecraft:textures/item/emerald.png";
    private static final String FACE = "minecraft:textures/mob_effect/luck.png";

    private static WheelComponent labels(Box box) {
        WheelComponent w = new WheelComponent(box);
        for (String s : new String[] {"Iron", "Gold", "Coal", "Emerald", "Copper", "Lapis", "Quartz", "Diamond"}) {
            w.add(s);
        }
        return w;
    }

    private static final String[][] LOOT = {
        {"Diamond", "minecraft:diamond"}, {"Emerald", "minecraft:emerald"}, {"Gold", "minecraft:gold_ingot"},
        {"Iron", "minecraft:iron_ingot"}, {"Apple", "minecraft:golden_apple"}, {"Pearl", "minecraft:ender_pearl"},
        {"Totem", "minecraft:totem_of_undying"}, {"Bread", "minecraft:bread"},
    };

    private static WheelComponent items(Box box, int count) {
        WheelComponent w = new WheelComponent(box);
        for (int i = 0; i < count; i++) {
            String[] l = LOOT[i % LOOT.length];
            w.add(new Segment(l[0]).item(ItemView.of(l[1])));
        }
        return w;
    }

    private static Box box() {
        return new Box("w", 5, 5, W - 10, H - 10);
    }

    private static void note(Scene s, int y, Supplier<String> text) {
        s.paint((d, t) -> d.text(text.get(), 4, y, t.palette().textSoft()));
    }

    @Override
    public void states(Stage stage) {
        stage.state("labels", W, H, s -> s.add(labels(box())));

        stage.state("items_with_labels", W, H, s -> s.add(items(box(), 8)));

        stage.state("items_big_crisp", 260, 260, s -> {
            s.add(items(new Box("w", 5, 5, 250, 250), 6));
        });

        stage.state("items_only_no_labels", W, H, s -> {
            WheelComponent w = new WheelComponent(box());
            for (String[] l : LOOT) {
                w.add(new Segment("").item(l[1]).data(l[0]));
            }
            s.add(w);
        });

        stage.state("sprites_and_images", W, H, s -> {
            AsyncImage gem = AsyncImage.of(GEM, 16, 16);
            AsyncImage face = AsyncImage.of(FACE, 18, 18);
            s.add(new WheelComponent(box())
                    .add(new Segment("Emerald").image(gem))
                    .add(new Segment("Luck").image(face))
                    .add(new Segment("Loading").image(AsyncImage.pending()))
                    .add(new Segment("Sprite").icon(SpriteIcon.item("item/diamond")))
                    .add(new Segment("Broken").image(AsyncImage.of("minecraft:textures/none.png", 16, 16))));
        });

        stage.state("weights", W, H, s -> s.add(new WheelComponent(box())
                .add(new Segment("Common").item("minecraft:bread").weight(50))
                .add(new Segment("Rare").item("minecraft:gold_ingot").weight(25))
                .add(new Segment("Epic").item("minecraft:emerald").weight(15))
                .add(new Segment("Legend").item("minecraft:diamond").weight(8))
                .add(new Segment("Mythic").item("minecraft:nether_star").weight(2))));

        stage.state("hover_tooltip", W, H, s -> {
            WheelComponent w = s.add(items(box(), 8));
            // Segment 2 of 8 at rest: its middle is 2.5 eighths of a turn clockwise from the top.
            double a = 2D * Math.PI * 2.5D / 8D;
            int r = Math.min(w.w, w.h) * 3 / 10;
            s.move(w.x + w.w / 2 + (int) Math.round(Math.sin(a) * r), w.y + w.h / 2 - (int) Math.round(Math.cos(a) * r));
            s.settle(700);
        });

        stage.state("hub_hover", W, H, s -> {
            WheelComponent w = s.add(items(box(), 8));
            s.move(w.x + w.w / 2, w.y + w.h / 2);
        });

        stage.state("spinning_fast", W, H, s -> {
            WheelComponent w = s.add(items(box(), 8)).muted(true);
            w.spin(3, 42L);
            s.waitMs(300);
            s.settle(0);
        });

        stage.state("spinning_slow", W, H, s -> {
            WheelComponent w = s.add(items(box(), 8)).muted(true);
            w.spin(3, 42L);
            s.waitMs(3300);
            s.settle(0);
        });

        // Four frames 16 ms apart near the end of a spin: pixels away from the moving edges must not change.
        for (int f = 0; f < 4; f++) {
            int frame = f;
            stage.state("spin_frame_" + f, W, H, s -> {
                WheelComponent w = s.add(labels(box())).muted(true);
                w.spin(3, 42L);
                s.waitMs(3500 + 16 * frame);
                s.settle(0);
            });
        }

        stage.state("landed", W, H, s -> {
            List<String> got = new ArrayList<>();
            WheelComponent w = s.add(items(box(), 8)).muted(true).onLand(seg -> got.add(seg.label()));
            w.spin(3, 42L);
            s.waitMs(4400);
        });

        stage.state("same_seed_same_spin", 300, H, s -> {
            WheelComponent a = s.add(labels(new Box("a", 5, 5, 140, 140))).muted(true);
            WheelComponent b = s.add(labels(new Box("b", 155, 5, 140, 140))).muted(true);
            a.spin(6, 7L);
            b.spin(6, 7L);
            s.waitMs(2000);
            s.settle(0);
        });

        stage.state("restored_mid_spin", 300, H, s -> {
            WheelComponent a = s.add(labels(new Box("a", 5, 5, 140, 140))).muted(true);
            a.spin(1, 99L);
            s.waitMs(1500);
            WheelComponent b = s.add(labels(new Box("b", 155, 5, 140, 140)).muted(true).restore(a.state()));
            s.waitMs(500);
            s.settle(0);
        });

        stage.state("reduced_motion_jumps", W, H, s -> {
            BaseComponent.reducedMotion = true;
            try {
                WheelComponent w = s.add(items(box(), 8)).muted(true);
                w.spin(5, 3L);
                s.waitMs(20);
            } finally {
                BaseComponent.reducedMotion = false;
            }
        });

        stage.state("keyboard_space_spins", W, H, s -> {
            WheelComponent w = s.add(items(box(), 8)).muted(true);
            s.focus(w);
            s.key(InputKey.SPACE);
            s.waitMs(600);
            s.settle(0);
        });

        stage.state("disabled", W, H, s -> s.add(items(box(), 8).active(false)));

        stage.state("empty", W, H, s -> s.add(new WheelComponent(box())));

        stage.state("many_24", W, H, s -> {
            WheelComponent w = new WheelComponent(box());
            for (int i = 1; i <= 24; i++) {
                w.add(i % 3 == 0 ? new Segment("Prize " + i).item(LOOT[i % LOOT.length][1]) : new Segment(String.valueOf(i)));
            }
            s.add(w);
        });

        stage.state("many_60", W, H, s -> {
            WheelComponent w = new WheelComponent(box());
            for (int i = 1; i <= 60; i++) {
                w.add("Player" + i);
            }
            s.add(w);
        });

        stage.state("tiny_boxes", 150, 50, s -> {
            s.add(items(new Box("a", 4, 4, 16, 16), 6));
            s.add(items(new Box("b", 24, 4, 24, 24), 6));
            s.add(items(new Box("c", 52, 4, 42, 42), 6));
            s.add(labels(new Box("d", 98, 2, 48, 46)));
        });

        stage.state("wide_and_tall_boxes", 220, 100, s -> {
            s.add(items(new Box("a", 4, 20, 140, 60), 4));
            s.add(items(new Box("b", 150, 4, 60, 92), 4));
        });

        stage.state("custom_colours_and_renderer", W, H, s -> s.add(new WheelComponent(box()).hubText("GO")
                .add(new Segment("Red").color(0xC0392B))
                .add(new Segment("Black").color(0x202020))
                .add(new Segment("Red").color(0xC0392B))
                .add(new Segment("Black").color(0x202020))
                .add(new Segment("Zero").color(0x1E8449).content((d, t, seg, left, top, side, active) -> {
                    d.fill(left, top, left + side, top + side, 0x80FFFFFF);
                    d.fill(left + side / 4, top + side / 4, left + side * 3 / 4, top + side * 3 / 4, 0xFF1E8449);
                }))));

        stage.state("fullscreen_open", 427, 240, s -> {
            s.add(labels(new Box("behind", 10, 10, 80, 80)));
            WheelComponent w = s.add(items(new Box("screen", 0, 0, 427, 240), 8).fullscreen(true).title("Daily reward"));
            w.open(s.panel());
        });

        stage.state("fullscreen_spinning", 427, 240, s -> {
            WheelComponent w = s.add(items(new Box("screen", 0, 0, 427, 240), 8).fullscreen(true).title("Daily reward").muted(true));
            w.open(s.panel());
            w.spin(0, 5L);
            s.waitMs(400);
            s.settle(0);
        });

        stage.state("fullscreen_landed", 427, 240, s -> {
            WheelComponent w = s.add(items(new Box("screen", 0, 0, 427, 240), 8).fullscreen(true).title("Daily reward").muted(true));
            w.open(s.panel());
            w.spin(0, 5L);
            s.waitMs(4500);
        });

        stage.state("fullscreen_closes_on_esc_and_land", 200, 80, s -> {
            WheelComponent a = s.add(items(new Box("screen", 0, 0, 200, 80), 8).fullscreen(true).muted(true));
            a.open(s.panel());
            s.key(InputKey.ESCAPE);
            WheelComponent b = s.add(items(new Box("screen2", 0, 0, 200, 80), 8).fullscreen(true).muted(true).closeOnLand(500));
            b.open(s.panel());
            b.spin(1, 1L);
            s.waitMs(5000);
            note(s, 30, () -> "both closed: nothing drawn");
        });
    }
}
