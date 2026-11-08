package dev.dryaides.minuit.dev;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Dev only: binds the dev menu type to its screen and puts the dev kit over merchant menus. */
@Mod(value = "minuit", dist = Dist.CLIENT)
public final class NeoForgeDevClient {

    public NeoForgeDevClient(IEventBus modBus) {
        modBus.addListener(RegisterMenuScreensEvent.class, event -> event.register(DevMenus.TYPE, DevSlotScreen::new));
        DevTradeScreen.register();
    }
}
