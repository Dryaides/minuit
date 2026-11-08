package dev.dryaides.minuit.dev;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Dev only: registers the dev menu type and {@code /minuit_devslots}. Listed in fabric.mod.json by the -PminuitDev build only. */
public final class FabricDev implements ModInitializer {

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.MENU, DevMenus.ID, DevMenus.TYPE);
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) ->
                dispatcher.register(DevMenus.command()));
    }
}
