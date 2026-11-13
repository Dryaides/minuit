package bench.demos;

import dev.dryaides.minuit.AsyncImage;
import dev.dryaides.minuit.Picture;
import java.util.List;

/**
 * The bench's test pictures (bench/images, made by MakeImages): a test chart, fake screenshots of a blocky
 * world, a portrait picture and two 16 x 16 pixel-art pictures. Not a demo: shared by the picture demos.
 */
public final class Shots {
    public static final String DIR = "minuit_components_bench:textures/bench/";

    public static AsyncImage image(String file, int w, int h) {
        return AsyncImage.of(DIR + file, w, h);
    }

    public static Picture pattern() {
        return Picture.of(image("pattern.png", 1920, 1080)).alt("Test chart, 1920 x 1080");
    }

    /** The same chart, shrunk to 384 x 216 by PictureBytes before it became a texture. */
    public static Picture patternShrunk() {
        return Picture.of(image("pattern-384.png", 384, 216)).alt("Test chart, shrunk first");
    }

    public static Picture day() {
        return Picture.of(image("shot-day.png", 1920, 1080)).caption("Spawn, day 1").alt("A grassy coast with two trees");
    }

    public static Picture dayShrunk() {
        return Picture.of(image("shot-day-384.png", 384, 216)).caption("Spawn, day 1").alt("A grassy coast with two trees");
    }

    public static Picture sunset() {
        return Picture.of(image("shot-sunset.png", 2560, 1080)).caption("Sunset over the bay, ultra-wide").alt("Hills at sunset");
    }

    public static Picture night() {
        return Picture.of(image("shot-night.png", 1920, 1080)).caption("First night").alt("Dark hills under stars");
    }

    public static Picture snow() {
        return Picture.of(image("shot-snow.png", 1920, 1080)).caption("Snowy peaks").alt("White hills");
    }

    public static Picture desert() {
        return Picture.of(image("shot-desert.png", 1920, 1080)).caption("Desert with cactus").alt("Sand dunes");
    }

    public static Picture portrait() {
        return Picture.of(image("portrait.png", 720, 1280)).caption("Tower, portrait").alt("A stone tower");
    }

    public static Picture emerald() {
        return Picture.texture("minecraft:textures/item/emerald.png", 16, 16).pixelArt().alt("Emerald, 16 x 16");
    }

    public static Picture face() {
        return Picture.texture(DIR + "face.png", 16, 16).pixelArt().alt("A face, 16 x 16");
    }

    /** The game's painting Sunset, 32 x 16. */
    public static Picture painting() {
        return Picture.texture("minecraft:textures/painting/sunset.png", 32, 16).pixelArt().caption("Sunset").alt("The painting Sunset");
    }

    public static List<Picture> gallery() {
        return List.of(day(), sunset(), night(), snow(), desert(), portrait(), painting());
    }
}
