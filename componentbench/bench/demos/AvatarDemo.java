package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.AvatarGroupComponent;
import dev.dryaides.minuit.Picture;
import dev.dryaides.minuit.AsyncImage;
import dev.dryaides.minuit.AvatarComponent;
import dev.dryaides.minuit.AvatarComponent.Status;
import dev.dryaides.minuit.Box;
import java.util.List;

/** One face: head, initials, status. */
public final class AvatarDemo implements Demo {
    private static final int W = 190;
    static final String STEVE = "minecraft:textures/entity/player/wide/steve.png";

    @Override
    public void states(Stage stage) {
        stage.state("picture: photo, item, texture", 140, 44, s -> {
            s.add(new AvatarComponent(new Box("a", 8, 8, 28, 28), "Alex Stone").picture(Shots.day()));
            s.add(new AvatarComponent(new Box("b", 44, 8, 28, 28), "Sword").picture(Picture.item("minecraft:diamond_sword")));
            s.add(new AvatarComponent(new Box("c", 80, 8, 28, 28), "Face").picture(Shots.face()).status(AvatarComponent.Status.ONLINE));
            s.add(new AvatarComponent(new Box("d", 116, 14, 16, 16), "Small").picture(Shots.face()));
        });
        stage.state("picture: pending shows initials", 60, 44, s -> s.add(new AvatarComponent(new Box("a", 8, 8, 28, 28), "Alex Stone").picture(Picture.of(AsyncImage.pending()))));
        stage.state("group with pictures", 140, 40, s -> s.add(new AvatarGroupComponent(new Box("g", 8, 8, 124, 24), java.util.List.of(
                new AvatarGroupComponent.Member("Alex Stone", null), new AvatarGroupComponent.Member("Sam Reed", null), new AvatarGroupComponent.Member("Kim Lee", null)))
                .pictures(java.util.Arrays.asList(Shots.face(), null, Picture.item("minecraft:apple")))));
        stage.state("initials", W, 40, s -> {
            String[] names = {"Alex Stone", "Steve", "Notch", "Jeb Bergensten", "Marie-Luise Kaya", "dinnerbone"};
            for (int i = 0; i < names.length; i++) {
                s.add(new AvatarComponent(new Box("a" + i, 8 + i * 28, 8, 24, 24), names[i]));
            }
        });
        stage.state("how initials are taken", W, 30, s -> {
            s.add(new AvatarComponent(new Box("a", 8, 4, 20, 20), "Émile Zola"));
        });
        stage.state("sizes", W, 44, s -> {
            int[] sizes = {8, 10, 12, 16, 20, 24, 32};
            int at = 6;
            for (int size : sizes) {
                s.add(new AvatarComponent(new Box("a" + size, at, 6, size, size), "Alex Stone").status(Status.ONLINE));
                at += size + 6;
            }
        });
        stage.state("player head when ready", W, 44, s -> {
            AsyncImage skin = AsyncImage.of(STEVE, 64, 64);
            AvatarComponent a = s.add(new AvatarComponent(new Box("a", 8, 6, 32, 32), "Steve").skin(skin));
            s.add(new AvatarComponent(new Box("b", 48, 6, 16, 16), "Steve").skin(skin));
            s.add(new AvatarComponent(new Box("c", 72, 6, 24, 24), "Steve").skin(skin).status(Status.BUSY));
            s.add(new AvatarComponent(new Box("d", 104, 6, 24, 24), "Steve").skin(skin).status(Status.ONLINE).active(false));
        });
        stage.state("skin pending shows initials", W, 40, s -> {
            AsyncImage skin = AsyncImage.pending();
            s.add(new AvatarComponent(new Box("a", 8, 8, 24, 24), "Alex Stone").skin(skin));
        });
        stage.state("skin failed shows initials", W, 40, s -> {
            AsyncImage skin = AsyncImage.pending();
            skin.failed();
            s.add(new AvatarComponent(new Box("a", 8, 8, 24, 24), "Alex Stone").skin(skin));
        });
        stage.state("skin arrives later", W, 40, s -> {
            AsyncImage skin = AsyncImage.pending();
            s.add(new AvatarComponent(new Box("a", 8, 8, 24, 24), "Alex Stone").skin(skin));
            s.waitMs(100);
            skin.ready(STEVE, 64, 64);
        });
        stage.state("status", W, 48, s -> {
            Status[] all = {Status.ONLINE, Status.AWAY, Status.BUSY, Status.OFFLINE, Status.NONE};
            for (int i = 0; i < all.length; i++) {
                s.add(new AvatarComponent(new Box("a" + i, 8 + i * 36, 8, 32, 32), "Alex Stone").status(all[i]));
            }
        });
        stage.state("disabled", W, 40, s -> s.add(new AvatarComponent(new Box("a", 8, 8, 24, 24), "Alex Stone").status(Status.ONLINE).active(false)));
        stage.state("empty name", W, 40, s -> {
            AvatarComponent a = s.add(new AvatarComponent(new Box("a", 8, 8, 24, 24), null));
            s.move(12, 12);
        });
        stage.state("name as a tooltip", W, 60, s -> {
            s.add(new AvatarComponent(new Box("a", 8, 8, 24, 24), "Alex Stone"));
            s.move(16, 16);
            s.settle(1500);
        });
        stage.state("box not square stays centred", W, 40, s -> s.add(new AvatarComponent(new Box("a", 8, 8, 60, 24), "Alex Stone")));
        stage.state("colour stays with the name", W, 40, s -> {
            s.add(new AvatarComponent(new Box("a", 8, 8, 24, 24), "Same Name"));
            s.add(new AvatarComponent(new Box("b", 36, 8, 24, 24), "Same Name"));
            s.add(new AvatarComponent(new Box("c", 64, 8, 24, 24), "Other Name"));
        });
        stage.state("tiny and hidden", W, 30, s -> {
            s.add(new AvatarComponent(new Box("a", 8, 8, 0, 0), "Alex"));
            s.add(new AvatarComponent(new Box("b", 16, 8, 4, 4), "Alex"));
            AvatarComponent h = s.add(new AvatarComponent(new Box("c", 30, 8, 16, 16), "Alex"));
            h.visible = false;
            s.move(34, 12);
        });
        stage.state("long name in a small head", W, 40, s -> s.add(new AvatarComponent(new Box("a", 8, 8, 24, 24), "Wolfeschlegelsteinhausenbergerdorff the Very Long Named")));
    }
}
