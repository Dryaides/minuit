package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.AsyncImage;
import dev.dryaides.minuit.AvatarGroupComponent;
import dev.dryaides.minuit.AvatarGroupComponent.Member;
import dev.dryaides.minuit.BenchAccess;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.InputKey;
import java.util.ArrayList;
import java.util.List;

/** Overlapping heads with a +N. */
public final class AvatarGroupDemo implements Demo {
    private static final int W = 190;
    private static final int H = 22;

    private static List<Member> people(int count) {
        String[] names = {"Alex Stone", "Steve", "Notch", "Jeb Bergensten", "Marie Kaya", "Dinnerbone", "Grumm", "Herobrine", "Sam", "Noor Haddad", "Ivo", "Tess"};
        List<Member> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(new Member(names[i % names.length], null));
        }
        return out;
    }

    private static AvatarGroupComponent group(Scene s, int count) {
        return s.add(new AvatarGroupComponent(new Box("g", 8, 8, 170, H), people(count)));
    }

    @Override
    public void states(Stage stage) {
        stage.state("all fit", W, 38, s -> {
            AvatarGroupComponent g = group(s, 4);
        });
        stage.state("max shows plus n", W, 38, s -> {
            AvatarGroupComponent g = group(s, 9).max(4);
        });
        stage.state("width shows plus n", W, 38, s -> {
            AvatarGroupComponent g = s.add(new AvatarGroupComponent(new Box("g", 8, 8, 70, H), people(12)));
        });
        stage.state("wider plus for 3 digits", W, 38, s -> {
            List<Member> many = new ArrayList<>();
            for (int i = 0; i < 123; i++) {
                many.add(new Member("Player " + i, null));
            }
            AvatarGroupComponent g = s.add(new AvatarGroupComponent(new Box("g", 8, 8, 170, H), many)).max(3);
        });
        stage.state("hover brings a head forward", W, 38, s -> {
            AvatarGroupComponent g = group(s, 5);
            Box b = BenchAccess.headBox(g, 2);
            s.move(b.x() + 3, b.y() + 10);
        });
        stage.state("head name as a tooltip", W, 64, s -> {
            AvatarGroupComponent g = group(s, 3);
            Box b = BenchAccess.headBox(g, 1);
            s.move(b.x() + 3, b.y() + 10);
            s.settle(1500);
        });
        stage.state("plus n tooltip names the hidden", W, 80, s -> {
            AvatarGroupComponent g = group(s, 6).max(3);
            Box b = BenchAccess.moreBox(g);
            s.move(b.x() + 6, b.y() + 8);
            s.settle(1500);
        });
        stage.state("click a head and the plus", W, 38, s -> {
            List<Integer> heard = new ArrayList<>();
            int[] more = new int[1];
            AvatarGroupComponent g = group(s, 6).max(3).onClick(heard::add).onMore(n -> more[0] = n);
            Box b = BenchAccess.headBox(g, 1);
            s.click(b.x() + 3, b.y() + 10);
            Box m = BenchAccess.moreBox(g);
            s.click(m.x() + 8, m.y() + 8);
        });
        stage.state("keys walk the heads", W, 38, s -> {
            List<Integer> heard = new ArrayList<>();
            AvatarGroupComponent g = group(s, 4).onClick(heard::add);
            s.key(InputKey.TAB);
            s.key(InputKey.RIGHT);
            s.key(InputKey.RIGHT);
            s.key(InputKey.ENTER);
        });
        stage.state("no click means no stop", W, 38, s -> {
            AvatarGroupComponent g = group(s, 4);
            s.key(InputKey.TAB);
        });
        stage.state("with heads", W, 38, s -> {
            AsyncImage skin = AsyncImage.of(AvatarDemo.STEVE, 64, 64);
            List<Member> list = new ArrayList<>(List.of(new Member("Steve", skin), new Member("Alex Stone", null), new Member("Steve Two", skin), new Member("Grumm", null), new Member("Ivo", null)));
            s.add(new AvatarGroupComponent(new Box("g", 8, 8, 170, H), list)).max(4);
        });
        stage.state("disabled", W, 38, s -> {
            int[] calls = new int[1];
            AvatarGroupComponent g = group(s, 6).max(3).onClick(i -> calls[0]++).active(false);
            Box b = BenchAccess.headBox(g, 0);
            s.click(b.x() + 3, b.y() + 3);
        });
        stage.state("empty", W, 38, s -> {
            AvatarGroupComponent g = group(s, 0);
            g.setMembers(null);
        });
        stage.state("too narrow for any head", W, 38, s -> {
            AvatarGroupComponent g = s.add(new AvatarGroupComponent(new Box("g", 8, 8, 26, H), people(8)));
        });
        stage.state("sizes", W, 62, s -> {
            s.add(new AvatarGroupComponent(new Box("a", 8, 4, 170, 14), people(7))).max(5);
            s.add(new AvatarGroupComponent(new Box("b", 8, 20, 170, 18), people(7))).max(5);
            s.add(new AvatarGroupComponent(new Box("c", 8, 40, 170, 18), people(2)));
        });
    }
}
