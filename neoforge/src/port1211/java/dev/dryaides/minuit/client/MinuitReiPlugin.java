package dev.dryaides.minuit.client;

import dev.dryaides.minuit.Box;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.api.client.registry.screen.OverlayDecider;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps REI's overlay off a kit container screen whose {@code showsContainerAddons} is false. The decider
 * sits above REI's container decider (priority -10), and REI asks it per screen on each use, so the hook
 * can change while the screen is open. The panel's parts outside the container box are REI's exclusion zones. Found by REI only ({@code @REIPluginClient}).
 */
@REIPluginClient
public final class MinuitReiPlugin implements REIClientPlugin {

    @Override
    public void registerScreens(ScreenRegistry registry) {
        registry.registerDecider(new OverlayDecider() {
            @Override
            public <R extends Screen> boolean isHandingScreen(Class<R> screen) {
                return MinuitContainerScreen.class.isAssignableFrom(screen);
            }

            @Override
            public <R extends Screen> InteractionResult shouldScreenBeOverlaid(R screen) {
                return screen instanceof MinuitContainerScreen<?> kit && !kit.host.viewers().showsAddons()
                        ? InteractionResult.FAIL : InteractionResult.PASS;
            }

            @Override
            public double getPriority() {
                return 10.0;
            }
        });
    }

    @Override
    public void registerExclusionZones(ExclusionZones zones) {
        zones.register(MinuitContainerScreen.class, screen -> {
            List<Rectangle> areas = new ArrayList<>();
            for (Box box : ((MinuitContainerScreen<?>) screen).extraAreas()) {
                areas.add(new Rectangle(box.x(), box.y(), box.w(), box.h()));
            }
            return areas;
        });
    }
}
