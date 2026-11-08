package dev.dryaides.minuit.dev;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

/** Dev only: binds the dev menu type to its screen. */
public final class FabricDevClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MenuScreens.register(DevMenus.TYPE, DevSlotScreen::new);
    }
}
