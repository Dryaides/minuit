package dev.dryaides.minuit.dev;

import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.PlayerInventoryComponent;
import dev.dryaides.minuit.SlotGridComponent;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.client.MinuitContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dev only: the {@link DevSlotMenu} drawn by the kit, wells under the game's items. */
public class DevSlotScreen extends MinuitContainerScreen<DevSlotMenu> {

    public DevSlotScreen(DevSlotMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, DevSlotMenu.WIDTH, DevSlotMenu.HEIGHT);
    }

    @Override
    protected PanelComponent build(int leftPos, int topPos) {
        return new PanelComponent(Themes.byId("minuit"), I18n::get, leftPos, topPos) {
            {
                add(new SlotGridComponent(at(DevSlotMenu.CHEST), DevSlotMenu.ROWS, 9));
                add(new PlayerInventoryComponent(at(DevSlotMenu.INV)));
            }

            @Override
            public int width() {
                return DevSlotMenu.WIDTH;
            }

            @Override
            public int height() {
                return DevSlotMenu.HEIGHT;
            }

            @Override
            protected void drawBackground(Draw d, int mx, int my) {
                this.theme.frame().panel(d, this.theme.palette(), this.left, this.top, width(), height());
            }
        };
    }
}
