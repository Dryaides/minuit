package bench.ingame.mixin;

import bench.ingame.BenchScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Opens the bench once, the first time the title screen shows. No world or account is needed.
 * For shots it first makes a flat world: item icons throw at the title screen, and the game's real backdrop is a world.
 */
@Mixin(Minecraft.class)
abstract class OpenAtTitle {
    @Unique private static final boolean IN_WORLD = Boolean.getBoolean("bench.shots");
    @Unique private static boolean benchOpened;
    @Unique private static boolean worldAsked;
    @Unique private static int titleTicks;

    @Inject(method = "tick", at = @At("TAIL"))
    private void bench$open(CallbackInfo info) {
        Minecraft mc = (Minecraft) (Object) this;
        if (benchOpened) {
            return;
        }
        if (!IN_WORLD) {
            if (mc.gui.screen() instanceof TitleScreen) {
                benchOpened = true;
                mc.setScreenAndShow(new BenchScreen());
            }
            return;
        }
        if (!worldAsked && mc.gui.screen() instanceof TitleScreen && mc.gui.overlay() == null && ++titleTicks >= 40) {
            worldAsked = true;
            net.minecraft.client.gui.screens.Screen parent = mc.gui.screen();
            // Scheduled: createFreshLevel blocks and renders, which must not happen inside a tick.
            mc.schedule(() -> {
                String id = "bench-" + System.currentTimeMillis();
                LevelSettings settings = new LevelSettings(id, GameType.CREATIVE,
                        new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(id, settings, new WorldOptions(WorldOptions.randomSeed(), false, false),
                        registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                        parent);
            });
        }
        if (worldAsked && mc.level != null && mc.player != null && mc.gui.screen() == null && mc.gui.overlay() == null) {
            benchOpened = true;
            mc.setScreenAndShow(new BenchScreen());
        }
    }
}
