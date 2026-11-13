package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.BingoBoardComponent;
import dev.dryaides.minuit.BingoBoardComponent.Team;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.ItemView;
import java.util.ArrayList;
import java.util.List;

/** A 5 by 5 bingo card with done cells and team claims. */
public final class BingoBoardDemo implements Demo {
    private static final String[] ITEMS = {
        "minecraft:diamond", "minecraft:emerald", "minecraft:gold_ingot", "minecraft:iron_ingot", "minecraft:redstone",
        "minecraft:apple", "minecraft:bread", "minecraft:cake", "minecraft:cookie", "minecraft:carrot",
        "minecraft:torch", "minecraft:bow", "minecraft:arrow", "minecraft:shield", "minecraft:compass",
        "minecraft:clock", "minecraft:book", "minecraft:paper", "minecraft:feather", "minecraft:string",
        "minecraft:bone", "minecraft:slime_ball", "minecraft:ender_pearl", "minecraft:blaze_rod", "minecraft:egg"};
    // Team colours come from the mod, as in a real bingo mod's team setup.
    private static final Team RED = new Team("Red team", 0xFFE04040);
    private static final Team BLUE = new Team("Blue team", 0xFF4070E0);
    private static final Team GOLD = new Team("Gold team", 0xFFE0B020);
    private static final int S = 5 * 18 + 16;

    private static BingoBoardComponent card() {
        List<ItemView> items = new ArrayList<>();
        for (String id : ITEMS) {
            items.add(ItemView.of(id));
        }
        BingoBoardComponent b = new BingoBoardComponent(8, 8, 5);
        b.items(items);
        b.onClick((i, button) -> b.done(i, !b.done(i)));
        b.done(0, true).done(6, true).done(12, true);
        b.claim(1, RED).claim(2, BLUE).claim(6, RED).claim(7, RED).claim(7, BLUE)
                .claim(12, RED).claim(12, BLUE).claim(12, GOLD).claim(18, GOLD);
        return b;
    }

    @Override
    public void states(Stage stage) {
        stage.state("card", S, S, s -> s.add(card()));

        stage.state("hover a cell three teams claimed", S + 60, S + 30, s -> {
            s.add(card());
            s.move(8 + 2 * 18 + 9, 8 + 2 * 18 + 9);
        });

        stage.state("keyboard cursor, click marks done", S, S, s -> {
            BingoBoardComponent b = s.add(card());
            s.key(InputKey.TAB);
            s.key(InputKey.RIGHT);
            s.key(InputKey.RIGHT);
            s.key(InputKey.RIGHT);
            s.key(InputKey.DOWN);
            s.key(InputKey.ENTER);
        });
    }
}
