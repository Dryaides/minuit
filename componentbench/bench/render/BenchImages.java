package bench.render;

import dev.dryaides.minuit.internal.ImageSource;
import java.awt.image.BufferedImage;

/** The bench's {@link ImageSource}: PNGs through {@link Textures}, baked images served back by it. */
final class BenchImages implements ImageSource {
    private final Textures textures;

    BenchImages(Textures textures) {
        this.textures = textures;
    }

    @Override
    public Pixels read(String id) {
        BufferedImage img = textures.peek(id);
        if (img == null) {
            return null;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        return new Pixels(w, h, img.getRGB(0, 0, w, h, null, 0, w));
    }

    @Override
    public String bake(String key, Pixels image) {
        BufferedImage img = new BufferedImage(image.w(), image.h(), BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, image.w(), image.h(), image.argb(), 0, image.w());
        String id = "minuit:baked/" + key + ".png";
        textures.put(id, img);
        return id;
    }
}
