package dev.dryaides.minuit.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.dryaides.minuit.client.ThemeScreen;
import dev.dryaides.minuit.internal.ContainerTab;

/**
 * Mod Menu's Config button for minuit: the theme page. Mod Menu alone reads the {@code modmenu}
 * entrypoint, so without it this class is never loaded and minuit needs no Mod Menu.
 */
public final class MinuitModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ThemeScreen(parent, ContainerTab.THEME_ID);
    }
}
