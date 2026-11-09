package dev.dryaides.minuit.client;

import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.EntityView;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.RichText;
import dev.dryaides.minuit.SpriteIcon;
import dev.dryaides.minuit.internal.GuiPart;
import dev.dryaides.minuit.internal.Maths;
import dev.dryaides.minuit.port.mc.EntityDraw;
import dev.dryaides.minuit.port.mc.GameIds;
import dev.dryaides.minuit.port.mc.GameSprites;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.config.GuiUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link Draw} over 1.12.2's fixed-function GL, and the only class of the kit that draws with it. Every
 * call reads the current GL matrix, so {@link #push} and a caller's own translate (the toast stack's)
 * apply alike; each call puts back the GL state it changed (blend, texture, alpha test, colour).
 * Everything lands in call order and in depth: the game draws a slot's item about 250 deep, so a later
 * fill under it stays hidden until {@link #nextLayer} lifts the matrix.
 *
 * <p>Client only. Never reachable from a class a dedicated server loads.
 */
public final class GuiDraw extends Draw {

    /** Over a container's slot items (about 250 deep with the screen's 100 offset) and their counts (300). */
    private static final float LAYER = 310F;

    /** FontRenderer draws a colour whose alpha is under 4 opaque: such text is skipped instead. */
    private static final int FONT_ALPHA_FLOOR = 0xFC000000;

    private static final Map<String, ItemStack> ITEMS = new ConcurrentHashMap<>();

    /** The open scissor boxes, GUI coordinates after the matrix, each cut to the one under it. */
    private static final Deque<int[]> SCISSORS = new ArrayDeque<>();

    private static final FloatBuffer MATRIX = GLAllocation.createDirectFloatBuffer(16);

    private final Minecraft mc;
    private final FontRenderer font;

    public GuiDraw() {
        this.mc = Minecraft.getMinecraft();
        this.font = this.mc.fontRenderer;
    }

    public int guiWidth() {
        return new ScaledResolution(this.mc).getScaledWidth();
    }

    public int guiHeight() {
        return new ScaledResolution(this.mc).getScaledHeight();
    }

    private RenderItem items() {
        return this.mc.getRenderItem();
    }

    /** {@code stack} at {@code x, y}, without its count or bar, under the GUI item light. */
    public void renderItem(ItemStack stack, int x, int y) {
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableRescaleNormal();
        items().renderItemAndEffectIntoGUI(stack, x, y);
        GlStateManager.disableRescaleNormal();
        RenderHelper.disableStandardItemLighting();
    }

    @Override
    public void item(String itemId, int x, int y) {
        ItemStack stack = byId(itemId);
        if (!stack.isEmpty()) {
            renderItem(stack, x, y);
        }
    }

    private static ItemStack byId(String itemId) {
        return ITEMS.computeIfAbsent(itemId, GameIds::stack);
    }

    /** The view's own stack, else one of its id with its count. */
    private static ItemStack stack(ItemView item) {
        if (item.stack() instanceof ItemStack) {
            return (ItemStack) item.stack();
        }
        ItemStack shared = byId(item.id());
        if (item.count() == 1 || shared.isEmpty()) {
            return shared;
        }
        ItemStack counted = shared.copy();
        counted.setCount(item.count());
        return counted;
    }

    @Override
    public void item(ItemView item, int x, int y) {
        ItemStack stack = stack(item);
        if (!stack.isEmpty()) {
            renderItem(stack, x, y);
        }
    }

    @Override
    public void itemDecorations(ItemView item, int x, int y) {
        ItemStack stack = stack(item);
        if (!stack.isEmpty()) {
            items().renderItemOverlayIntoGUI(this.font, stack, x, y, null);
        }
    }

    @Override
    public void itemDecorations(ItemView item, int x, int y, String count) {
        ItemStack stack = stack(item);
        if (!stack.isEmpty()) {
            // an empty string hides the count: the game draws its own for a null one
            items().renderItemOverlayIntoGUI(this.font, stack, x, y, count == null ? "" : count);
        }
    }

    /** Only over an open screen, as later versions draw item tooltips; none over the HUD. */
    @Override
    public boolean itemTooltip(ItemView item, int mx, int my) {
        ItemStack stack = stack(item);
        GuiScreen screen = this.mc.currentScreen;
        if (stack.isEmpty() || screen == null) {
            return false;
        }
        GuiUtils.preItemToolTip(stack);
        GuiUtils.drawHoveringText(stack, screen.getItemToolTip(stack), mx, my, screen.width, screen.height, -1,
                this.font);
        GuiUtils.postItemToolTip();
        afterTooltip();
        return true;
    }

    /**
     * The GUI atlas does not exist before 1.20.2: nothing is drawn, else the base would blit a missing
     * texture. Items and blocks share the one block atlas.
     */
    @Override
    public void sprite(String atlas, String sprite, int x, int y, int w, int h, float alpha) {
        if (SpriteIcon.GUI.equals(atlas) || GameSprites.atlasMarker(this, atlas, sprite, x, y, w, h, alpha)) {
            return;
        }
        textured(alpha, () -> GameSprites.atlas(atlas, sprite, x, y, w, h));
    }

    @Override
    public boolean guiPart(GuiPart part, int x, int y, int w, int h, float alpha) {
        return GameSprites.part(this, part, x, y, w, h, alpha);
    }

    @Override
    public boolean entity(EntityView view, int x0, int y0, int x1, int y1, float yaw, float pitch) {
        return EntityDraw.draw(this, view, x0, y0, x1, y1, yaw, pitch);
    }

    /** The section-sign codes 1.12.2's font reads for a style, in front of the text. */
    private static String codes(int style) {
        StringBuilder s = new StringBuilder();
        if ((style & RichText.BOLD) != 0) {
            s.append(TextFormatting.BOLD);
        }
        if ((style & RichText.ITALIC) != 0) {
            s.append(TextFormatting.ITALIC);
        }
        if ((style & RichText.UNDERLINE) != 0) {
            s.append(TextFormatting.UNDERLINE);
        }
        if ((style & RichText.STRIKE) != 0) {
            s.append(TextFormatting.STRIKETHROUGH);
        }
        if ((style & RichText.OBFUSCATED) != 0) {
            s.append(TextFormatting.OBFUSCATED);
        }
        return s.toString();
    }

    @Override
    protected void styledString(String text, int x, int y, int argb, int style) {
        string(codes(style) + text, x, y, argb);
    }

    @Override
    public int styledWidth(String text, int style) {
        return this.font.getStringWidth(codes(style) + text);
    }

    @Override
    protected void rect(int x0, int y0, int x1, int y1, int argb) {
        if (x0 == x1 || y0 == y1) {
            return;
        }
        float a = (argb >>> 24) / 255F;
        float r = (argb >> 16 & 0xFF) / 255F;
        float g = (argb >> 8 & 0xFF) / 255F;
        float b = (argb & 0xFF) / 255F;
        GlStateManager.disableTexture2D();
        translucent(() -> {
            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            buffer.pos(Math.min(x0, x1), Math.max(y0, y1), 0D).color(r, g, b, a).endVertex();
            buffer.pos(Math.max(x0, x1), Math.max(y0, y1), 0D).color(r, g, b, a).endVertex();
            buffer.pos(Math.max(x0, x1), Math.min(y0, y1), 0D).color(r, g, b, a).endVertex();
            buffer.pos(Math.min(x0, x1), Math.min(y0, y1), 0D).color(r, g, b, a).endVertex();
            Tessellator.getInstance().draw();
        });
        GlStateManager.enableTexture2D();
    }

    @Override
    protected void string(String text, int x, int y, int argb) {
        if ((argb & FONT_ALPHA_FLOOR) == 0) {
            return;
        }
        translucent(() -> this.font.drawString(text, x, y, argb));
    }

    @Override
    public int width(String text) {
        return this.font.getStringWidth(text);
    }

    @Override
    protected String cut(String text, int maxWidth) {
        return this.font.trimStringToWidth(text, maxWidth);
    }

    /**
     * Blending on and the alpha test off, as later versions draw the GUI: 1.12.2's alpha test drops what
     * is under 10% opaque, so fades and soft shadows would vanish early.
     */
    private static void translucent(Runnable draw) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableAlpha();
        draw.run();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
    }

    /** The texture blits take no colour: alpha goes through the GL colour. */
    private static void textured(float alpha, Runnable blit) {
        translucent(() -> {
            GlStateManager.color(1F, 1F, 1F, Maths.clamp(alpha, 0F, 1F));
            blit.run();
            GlStateManager.color(1F, 1F, 1F, 1F);
        });
    }

    private void textured(ResourceLocation texture, float alpha, Runnable blit) {
        textured(alpha, () -> {
            this.mc.getTextureManager().bindTexture(texture);
            blit.run();
        });
    }

    @Override
    public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH, float alpha) {
        ResourceLocation id = GameIds.id(texture);
        if (id != null) {
            textured(id, alpha, () -> Gui.drawModalRectWithCustomSizedTexture(x, y, u, v, w, h, texW, texH));
        }
    }

    @Override
    public void image(String texture, int x, int y, int w, int h, int texW, int texH, float alpha) {
        if (GameSprites.markerFile(this, texture, x, y, w, h, alpha)) {
            return;
        }
        ResourceLocation id = GameIds.id(texture);
        if (id != null) {
            textured(id, alpha, () -> Gui.drawScaledCustomSizeModalRect(x, y, 0F, 0F, texW, texH, w, h, texW, texH));
        }
    }

    @Override
    public void blitRegion(String texture, int x, int y, int w, int h, int u, int v, int regionW, int regionH,
                           int texW, int texH, float alpha) {
        ResourceLocation id = GameIds.id(texture);
        if (id != null) {
            textured(id, alpha,
                    () -> Gui.drawScaledCustomSizeModalRect(x, y, u, v, regionW, regionH, w, h, texW, texH));
        }
    }

    @Override
    public int guiScale() {
        return Math.max(1, new ScaledResolution(this.mc).getScaleFactor());
    }

    /** A layer up in depth: what comes next covers the items the game drew in between. The caller pops it. */
    @Override
    public void nextLayer() {
        GlStateManager.translate(0F, 0F, LAYER);
    }

    @Override
    public void push(float x, float y, float scale) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0F);
        GlStateManager.scale(scale, scale, 1F);
    }

    @Override
    public void pop() {
        GlStateManager.popMatrix();
    }

    @Override
    public void scissor(int x0, int y0, int x1, int y1) {
        MATRIX.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
        // column major: x' = m0 x + m4 y + m12, y' = m1 x + m5 y + m13
        float m0 = MATRIX.get(0), m1 = MATRIX.get(1), m4 = MATRIX.get(4), m5 = MATRIX.get(5);
        float m12 = MATRIX.get(12), m13 = MATRIX.get(13);
        float ax = m0 * x0 + m4 * y0 + m12, ay = m1 * x0 + m5 * y0 + m13;
        float bx = m0 * x1 + m4 * y1 + m12, by = m1 * x1 + m5 * y1 + m13;
        int[] box = {(int) Math.floor(Math.min(ax, bx)), (int) Math.floor(Math.min(ay, by)),
                (int) Math.floor(Math.max(ax, bx)), (int) Math.floor(Math.max(ay, by))};
        int[] under = SCISSORS.peek();
        if (under != null) {
            box = new int[] {Math.max(box[0], under[0]), Math.max(box[1], under[1]),
                    Math.min(box[2], under[2]), Math.min(box[3], under[3])};
        }
        SCISSORS.push(box);
        apply(box);
    }

    @Override
    public void endScissor() {
        SCISSORS.poll();
        int[] under = SCISSORS.peek();
        if (under == null) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } else {
            apply(under);
        }
    }

    /** In framebuffer pixels from the bottom left, the box already through the matrix. */
    private void apply(int[] box) {
        int scale = guiScale();
        int w = Math.max(0, box[2] - box[0]);
        int h = Math.max(0, box[3] - box[1]);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(box[0] * scale, this.mc.displayHeight - (box[1] + h) * scale, w * scale, h * scale);
    }

    /** Wrapped at the kit's tooltip width; the first line names the thing, the rest is said about it. */
    @Override
    public void tooltip(List<String> lines, int x, int y) {
        List<String> coloured = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            coloured.add((i == 0 ? TextFormatting.WHITE : TextFormatting.GRAY) + lines.get(i));
        }
        ScaledResolution screen = new ScaledResolution(this.mc);
        GuiUtils.drawHoveringText(coloured, x, y, screen.getScaledWidth(), screen.getScaledHeight(), TOOLTIP_WIDTH,
                this.font);
        afterTooltip();
    }

    /** Forge's tooltip turns the item light back on as it ends; later fills would come out shaded. */
    private static void afterTooltip() {
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
    }
}
