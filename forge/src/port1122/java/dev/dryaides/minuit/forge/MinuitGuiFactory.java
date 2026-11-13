package dev.dryaides.minuit.forge;

import dev.dryaides.minuit.client.ThemeScreen;
import dev.dryaides.minuit.internal.ContainerTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;

import java.util.Set;

/** The Config button of the mods list: minuit's theme page. Client only; FML loads it by name. */
public final class MinuitGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraft) {
    }

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parent) {
        return new ThemeScreen(parent, ContainerTab.THEME_ID);
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }
}
