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
 * is saved to {@code options.txt} at once. On NeoForge and Forge, whose entry points install
 * {@link #modifiers}, a binding takes Ctrl, Shift or Alt with its key; on Fabric the key alone. Client only.
 */
public final class MinuitKeys {

    /** How a loader with key modifiers sets one on a mapping; the loader's name and conflicts already count it. Set by minuit's loader entry points only. */
    public interface Modifiers {
        void set(KeyMapping mapping, KeyPress.Modifier modifier, InputConstants.Key key);

        /** Back to the default key and modifier. */
        void reset(KeyMapping mapping);
    }

    private static Modifiers modifiers;

    private MinuitKeys() {
    }

    /** Installed once by the loader's client entry point. */
    public static void modifiers(Modifiers loader) {
        modifiers = loader;
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
            public boolean supportsModifiers() {
                return modifiers != null;
            }

            @Override
            public void bind(KeyPress key) {
                InputConstants.Type type = key.kind() == KeyPress.Kind.MOUSE ? InputConstants.Type.MOUSE
                        : dev.dryaides.minuit.port.mc.GameScreens.keyboard();
                set(key.modifier(), type.getOrCreate(key.code()));
            }

            @Override
            public void unbind() {
                set(KeyPress.Modifier.NONE, InputConstants.UNKNOWN);
            }

            @Override
            public void reset() {
                if (modifiers != null) {
                    modifiers.reset(mapping);
                } else {
                    mapping.setKey(mapping.getDefaultKey());
                }
                saved();
            }

            private void set(KeyPress.Modifier modifier, InputConstants.Key key) {
                if (modifiers != null) {
                    modifiers.set(mapping, modifier, key);
                } else {
                    mapping.setKey(key);
                }
                saved();
            }

            private void saved() {
                KeyMapping.resetMapping();
                Minecraft.getInstance().options.save();
            }
        };
    }
}
