package dev.dryaides.minuit.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import dev.dryaides.minuit.KeyPress;
import dev.dryaides.minuit.client.MinuitKeys;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyModifier;

/** NeoForge's key modifier for the kit's key binding fields. Client only. */
final class NeoForgeKeys implements MinuitKeys.Modifiers {

    @Override
    public void set(KeyMapping mapping, KeyPress.Modifier modifier, InputConstants.Key key) {
        mapping.setKeyModifierAndCode(KeyModifier.valueOf(modifier.name()), key);
    }

    @Override
    public void reset(KeyMapping mapping) {
        mapping.setToDefault();
    }
}
