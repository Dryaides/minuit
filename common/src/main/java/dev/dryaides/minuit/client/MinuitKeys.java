package dev.dryaides.minuit.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.dryaides.minuit.KeyBinding;
import dev.dryaides.minuit.KeyPress;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;

/**
 * A game {@code KeyMapping} as the kit's {@link KeyBinding}, for a {@code KeybindFieldComponent}. A change
 * is saved to {@code options.txt} at once. One key or mouse button: NeoForge's key modifiers are left
 * as they are. Client only.
 */
public final class MinuitKeys {

    private MinuitKeys() {
    }

    public static KeyBinding binding(KeyMapping mapping) {
        return new KeyBinding() {
            @Override
            public String keyName() {
                return mapping.getTranslatedKeyMessage().getString();
            }

            @Override
            public boolean isDefault() {
                return mapping.isDefault();
            }

            @Override
            public List<String> conflicts() {
                List<String> names = new ArrayList<>();
                if (mapping.isUnbound()) {
                    return names;
                }
                for (KeyMapping other : Minecraft.getInstance().options.keyMappings) {
                    if (other != mapping && mapping.same(other)) {
                        names.add(I18n.get(other.getName()));
                    }
                }
                return names;
            }

            @Override
            public void bind(KeyPress key) {
                InputConstants.Type type = key.kind() == KeyPress.Kind.MOUSE ? InputConstants.Type.MOUSE
                        : dev.dryaides.minuit.port.mc.GameScreens.keyboard();
                set(type.getOrCreate(key.code()));
            }

            @Override
            public void unbind() {
                set(InputConstants.UNKNOWN);
            }

            @Override
            public void reset() {
                set(mapping.getDefaultKey());
            }

            private void set(InputConstants.Key key) {
                mapping.setKey(key);
                KeyMapping.resetMapping();
                Minecraft.getInstance().options.save();
            }
        };
    }
}
