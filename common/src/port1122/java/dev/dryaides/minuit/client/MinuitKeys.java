package dev.dryaides.minuit.client;

import dev.dryaides.minuit.KeyBinding;
import dev.dryaides.minuit.KeyPress;
import dev.dryaides.minuit.port.mc.GameInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * A game key binding as the kit's {@link KeyBinding}, for a {@code KeybindFieldComponent}. A change is
 * saved to {@code options.txt} at once, with Forge's Ctrl, Shift or Alt modifier. Core
 * hands GLFW codes; 1.12.2 stores LWJGL 2 key codes, and mouse buttons as {@code button - 100}. Client only.
 */
public final class MinuitKeys {

    private static final int MOUSE_OFFSET = -100;

    private MinuitKeys() {
    }

    public static KeyBinding binding(net.minecraft.client.settings.KeyBinding mapping) {
        return new KeyBinding() {
            @Override
            public String keyName() {
                return mapping.getDisplayName();
            }

            @Override
            public boolean isDefault() {
                return mapping.isSetToDefaultValue();
            }

            @Override
            public List<String> conflicts() {
                List<String> names = new ArrayList<>();
                if (mapping.getKeyCode() == Keyboard.KEY_NONE) {
                    return names;
                }
                for (net.minecraft.client.settings.KeyBinding other : Minecraft.getMinecraft().gameSettings.keyBindings) {
                    if (other != mapping && mapping.conflicts(other)) {
                        names.add(I18n.format(other.getKeyDescription()));
                    }
                }
                return names;
            }

            @Override
            public boolean supportsModifiers() {
                return true;
            }

            @Override
            public void bind(KeyPress key) {
                int code = key.kind() == KeyPress.Kind.MOUSE ? key.code() + MOUSE_OFFSET : GameInput.lwjgl(key.code());
                mapping.setKeyModifierAndCode(KeyModifier.valueOf(key.modifier().name()), code);
                saved();
            }

            @Override
            public void unbind() {
                mapping.setKeyModifierAndCode(KeyModifier.NONE, Keyboard.KEY_NONE);
                saved();
            }

            @Override
            public void reset() {
                mapping.setToDefault();
                saved();
            }

            private void saved() {
                net.minecraft.client.settings.KeyBinding.resetKeyBindingArrayAndHash();
                Minecraft.getMinecraft().gameSettings.saveOptions();
            }
        };
    }
}
