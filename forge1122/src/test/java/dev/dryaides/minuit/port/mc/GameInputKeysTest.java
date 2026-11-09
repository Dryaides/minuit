package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.internal.port.KeyCodes;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The LWJGL 2 to GLFW key table against both libraries: each row's GLFW code is the one LWJGL 3's
 * {@code GLFW_KEY_<name>} holds, every key core names in {@link KeyCodes#GLFW} has a row, and every
 * LWJGL 2 key that GLFW also names is mapped.
 */
class GameInputKeysTest {

    /** LWJGL 2 keys with no GLFW counterpart (Japanese layout keys, NEC PC-98 keys, power keys). */
    private static final Set<String> NO_GLFW = new HashSet<>(Arrays.asList("KEY_NONE", "KEY_KANA", "KEY_CONVERT",
            "KEY_NOCONVERT", "KEY_YEN", "KEY_CIRCUMFLEX", "KEY_AT", "KEY_COLON", "KEY_UNDERLINE", "KEY_KANJI",
            "KEY_STOP", "KEY_AX", "KEY_UNLABELED", "KEY_SECTION", "KEY_NUMPADCOMMA", "KEY_FUNCTION", "KEY_CLEAR",
            "KEY_POWER", "KEY_SLEEP", "KEY_LWIN", "KEY_RWIN"));

    @Test
    void everyRowMatchesTheGlfwConstantOfItsName() throws IOException {
        Map<String, Integer> glfw = glfwKeys();
        for (GameInput.Key key : GameInput.KEYS) {
            Integer real = glfw.get(key.glfwName);
            assertTrue(real != null, "GLFW has no GLFW_KEY_" + key.glfwName);
            assertEquals(real.intValue(), key.glfw, "GLFW_KEY_" + key.glfwName);
            assertEquals(key.glfw, GameInput.glfw(key.lwjgl), key.glfwName + " in");
            assertEquals(key.lwjgl, GameInput.lwjgl(key.glfw), key.glfwName + " back");
        }
    }

    @Test
    void everyKeyCoreNamesHasARow() {
        KeyCodes c = KeyCodes.GLFW;
        List<Integer> core = Arrays.asList(c.escape(), c.tab(), c.enter(), c.numpadEnter(), c.backspace(),
                c.delete(), c.home(), c.end(), c.left(), c.right(), c.up(), c.down(), c.space(), c.a(), c.c(), c.x(),
                c.v());
        for (int code : core) {
            assertTrue(GameInput.lwjgl(code) != Keyboard.KEY_NONE, "no LWJGL 2 key for GLFW " + code);
        }
    }

    @Test
    void everyLwjgl2KeyGlfwNamesIsMapped() throws IOException {
        Set<Integer> seen = new HashSet<>();
        for (Map.Entry<String, Integer> key : constants("/org/lwjgl/input/Keyboard.class", "KEY_").entrySet()) {
            if (NO_GLFW.contains("KEY_" + key.getKey())) {
                continue;
            }
            assertTrue(GameInput.glfw(key.getValue()) > 0, "KEY_" + key.getKey() + " has no GLFW code");
            seen.add(key.getValue());
        }
        assertEquals(seen.size(), GameInput.KEYS.size(), "rows for keys LWJGL 2 does not name");
    }

    private static Map<String, Integer> glfwKeys() throws IOException {
        return constants("/org/lwjgl/glfw/GLFW.class", "GLFW_KEY_");
    }

    /**
     * The int constants of a class whose names start with {@code prefix}, read from its class file:
     * neither library's static initialiser runs, so no native library loads.
     */
    private static Map<String, Integer> constants(String classFile, String prefix) throws IOException {
        Map<String, Integer> out = new HashMap<>();
        try (InputStream in = GameInputKeysTest.class.getResourceAsStream(classFile)) {
            assertTrue(in != null, classFile + " not on the test classpath");
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM5) {
                @Override
                public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                    if (name.startsWith(prefix) && value instanceof Integer) {
                        out.put(name.substring(prefix.length()), (Integer) value);
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE);
        }
        return out;
    }
}
