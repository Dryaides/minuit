package dev.dryaides.minuit.dev;

import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Dev only: registers the dev menu type and {@code /minuit_devslots}. Compiled by the -PminuitDev build only. */
@Mod("minuit")
public final class NeoForgeDev {

    public NeoForgeDev(IEventBus modBus) {
        modBus.addListener(RegisterEvent.class, event ->
                event.register(Registries.MENU, helper -> helper.register(DevMenus.ID, DevMenus.TYPE)));
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class,
                event -> event.getDispatcher().register(DevMenus.command()));
    }
}
