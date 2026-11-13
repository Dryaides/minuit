package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.ImageSource;
import dev.dryaides.minuit.internal.GuiPart;
import java.util.List;

/**
 * Dev bench only, never in the mod jar: passes every call to the game's Draw and counts them per kind.
 * It sits in minuit's package because rect, string and styledString are protected: from here it calls them
 * on the game's Draw directly, so what reaches the screen is exactly what the panel drew.
 */
public final class BenchCountingDraw extends Draw {
    private Draw out;
    public long rects;
    public long strings;
    public long styled;
    public long blits;
    public long images;
    public long guiParts;
    public long items;
    public long scissors;
    public long pushes;

    public BenchCountingDraw on(Draw target) {
        out = target;
        return this;
    }

    public void reset() {
        rects = strings = styled = blits = images = guiParts = items = scissors = pushes = 0;
    }

    @Override protected void rect(int x0, int y0, int x1, int y1, int argb) { rects++; out.rect(x0, y0, x1, y1, argb); }
    @Override protected void string(String text, int x, int y, int argb) { strings++; out.string(text, x, y, argb); }
    @Override protected void styledString(String text, int x, int y, int argb, int style) { styled++; out.styledString(text, x, y, argb, style); }
    @Override public int width(String text) { return out.width(text); }
    @Override public int styledWidth(String text, int style) { return out.styledWidth(text, style); }
    @Override public String cut(String text, int maxWidth) { return out.cut(text, maxWidth); }
    @Override public void item(String itemId, int x, int y) { items++; out.item(itemId, x, y); }
    @Override public void item(ItemView item, int x, int y) { items++; out.item(item, x, y); }
    @Override public void itemDecorations(ItemView item, int x, int y) { out.itemDecorations(item, x, y); }
    @Override public void itemDecorations(ItemView item, int x, int y, String count) { out.itemDecorations(item, x, y, count); }
    @Override public boolean itemTooltip(ItemView item, int mx, int my) { return out.itemTooltip(item, mx, my); }
    @Override public void sprite(String atlas, String sprite, int x, int y, int w, int h, float alpha) { images++; out.sprite(atlas, sprite, x, y, w, h, alpha); }
    @Override public boolean entity(EntityView view, int x0, int y0, int x1, int y1, float yaw, float pitch) { return out.entity(view, x0, y0, x1, y1, yaw, pitch); }
    @Override public ImageSource.Pixels texels(String texture) { return out.texels(texture); }
    @Override public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH, float alpha) { blits++; out.blit(texture, x, y, u, v, w, h, texW, texH, alpha); }
    @Override public void image(String texture, int x, int y, int w, int h, int texW, int texH, float alpha) { images++; out.image(texture, x, y, w, h, texW, texH, alpha); }
    @Override public void blitRegion(String texture, int x, int y, int w, int h, int u, int v, int regionW, int regionH, int texW, int texH, float alpha) {
        images++;
        out.blitRegion(texture, x, y, w, h, u, v, regionW, regionH, texW, texH, alpha);
    }
    @Override public int guiScale() { return out.guiScale(); }
    @Override public boolean guiPart(GuiPart part, int x, int y, int w, int h, float alpha) {
        boolean drawn = out.guiPart(part, x, y, w, h, alpha);
        if (drawn) {
            guiParts++;
        }
        return drawn;
    }
    @Override public void nextLayer() { out.nextLayer(); }
    @Override protected void pushPose(float x, float y, float scale) { pushes++; out.push(x, y, scale); }
    @Override protected void popPose() { out.pop(); }
    @Override public void scissor(int x0, int y0, int x1, int y1) { scissors++; out.scissor(x0, y0, x1, y1); }
    @Override public void endScissor() { out.endScissor(); }
    @Override public void tooltip(List<String> lines, int x, int y) { out.tooltip(lines, x, y); }
}
