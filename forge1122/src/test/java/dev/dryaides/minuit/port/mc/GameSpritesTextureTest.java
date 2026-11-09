package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.internal.GuiPart;
import dev.dryaides.minuit.internal.PartTable;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every texture a GUI part is drawn from on 1.12.2 is a file of the vanilla 1.12.2 client jar (the one
 * RetroFuturaGradle downloaded), so no part blits the missing-texture checker.
 */
class GameSpritesTextureTest {

    @Test
    void everyPartTextureIsInThe1122ClientJar() throws IOException {
        File jar = new File(System.getProperty("minuit.clientJar", ""));
        assertTrue(jar.isFile(), "no 1.12.2 client jar at " + jar);
        List<String> missing = new ArrayList<>();
        int drawn = 0;
        try (ZipFile client = new ZipFile(jar)) {
            for (GuiPart part : GuiPart.values()) {
                PartTable.Art art = GameSprites.art(part);
                if (art == null) {
                    continue;
                }
                drawn++;
                for (String texture : textures(art)) {
                    String entry = "assets/minecraft/" + texture.substring("minecraft:".length());
                    if (!texture.startsWith("minecraft:") || client.getEntry(entry) == null) {
                        missing.add(part + " -> " + texture);
                    }
                }
            }
        }
        assertTrue(drawn > 0, "no part resolves");
        if (!missing.isEmpty()) {
            fail("textures absent from the 1.12.2 client jar: " + missing);
        }
    }

    private static List<String> textures(PartTable.Art art) {
        List<String> out = new ArrayList<>();
        if (art instanceof PartTable.Region) {
            out.add(((PartTable.Region) art).texture());
        } else if (art instanceof PartTable.Framed) {
            out.add(((PartTable.Framed) art).frame().texture());
            out.add(((PartTable.Framed) art).fill().texture());
        } else {
            fail("1.12.2 has no GUI atlas, yet a part resolves to " + art);
        }
        return out;
    }
}
