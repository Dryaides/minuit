package dev.dryaides.minuit.dev;

import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.PlayerInventoryComponent;
import dev.dryaides.minuit.SlotGridComponent;
import dev.dryaides.minuit.SlotLayout;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.client.MinuitContainerScreen;
import dev.dryaides.minuit.client.MinuitMenuScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dev only: a kit screen over the vanilla merchant menu, world in view, closed by walking away, for
 * the in-game check of {@link MinuitMenuScreens}. Every check reads a {@code [devtrade]} log line.
 */
public class DevTradeScreen extends MinuitContainerScreen<MerchantMenu> {

    private static final Logger LOG = LoggerFactory.getLogger("minuit-dev");
    /** MerchantMenu's own slot positions (payment 136/162, result 220, row 37; inventory 108, 84). */
    private static final Box INV = SlotLayout.playerInventory("inv", 108, 84);
    private static final Box PAY_A = SlotLayout.grid("payA", 136, 37, 1, 1);
    private static final Box PAY_B = SlotLayout.grid("payB", 162, 37, 1, 1);
    private static final Box RESULT = SlotLayout.grid("result", 220, 37, 1, 1);
    private boolean loggedBackground;

    public static void register() {
        MinuitMenuScreens.over((menu, vanilla) -> menu instanceof MerchantMenu m ? new DevTradeScreen(m, vanilla) : null);
    }

    private DevTradeScreen(MerchantMenu menu, Screen vanilla) {
        super(menu, Minecraft.getInstance().player.getInventory(), vanilla.getTitle(), 276, 166, vanilla);
        LOG.info("[devtrade] open menu={} over={}", menu.containerId, vanilla.getClass().getSimpleName());
    }

    @Override
    protected boolean drawsBackground() {
        return false;
    }

    @Override
    protected boolean closesOnMovement() {
        return true;
    }

    @Override
    protected PanelComponent build(int leftPos, int topPos) {
        return new PanelComponent(Themes.byId("minuit"), I18n::get, leftPos, topPos) {
            {
                add(new SlotGridComponent(at(PAY_A), 1, 1));
                add(new SlotGridComponent(at(PAY_B), 1, 1));
                add(new SlotGridComponent(at(RESULT), 1, 1));
                add(new PlayerInventoryComponent(at(INV)));
            }

            @Override
            public int width() {
                return 276;
            }

            @Override
            public int height() {
                return 166;
            }

            @Override
            protected void drawBackground(Draw d, int mx, int my) {
            }
        };
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        if (!this.loggedBackground) {
            this.loggedBackground = true;
            LOG.info("[devtrade] frame1 screen={} menuOpen={}", this.minecraft.screen == this,
                    this.minecraft.player.containerMenu == this.menu);
        }
        super.extractBackground(graphics, mouseX, mouseY, partial);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int button, ContainerInput input) {
        LOG.info("[devtrade] slot id={} button={} input={}", slotId, button, input);
        super.slotClicked(slot, slotId, button, input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        LOG.info("[devtrade] scroll x={} y={} shift={}", scrollX, scrollY, this.minecraft.hasShiftDown());
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        LOG.info("[devtrade] key {}", event.key());
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        super.removed();
        LOG.info("[devtrade] removed menuStillOpen={}", this.minecraft.player.containerMenu == this.menu);
    }
}
