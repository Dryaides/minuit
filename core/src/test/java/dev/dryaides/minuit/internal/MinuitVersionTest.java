package dev.dryaides.minuit.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinuitVersionTest {
    @Test
    void theShownVersionIsTheBuiltOne() throws IOException {
        Path props = Path.of("..", "gradle.properties");
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(props)) {
            p.load(r);
        }
        assertEquals(p.getProperty("version"), MinuitVersion.VERSION);
    }
}
