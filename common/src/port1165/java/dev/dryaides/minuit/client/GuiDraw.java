package dev.dryaides.minuit.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector4f;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.EntityView;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.RichText;
import dev.dryaides.minuit.SpriteIcon;
import dev.dryaides.minuit.internal.GuiPart;
import dev.dryaides.minuit.internal.Maths;
import dev.dryaides.minuit.port.mc.EntityDraw;
import dev.dryaides.minuit.port.mc.GameIds;
import dev.dryaides.minuit.port.mc.GameScreens;
import dev.dryaides.minuit.port.mc.GameSprites;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link Draw} over the game's {@code GuiComponent} statics and the frame's {@code PoseStack} (1.16.5), and
 * the only class of the kit that calls them. Fills, text and textures follow the pose; the item renderer
 * reads the fixed-function GL matrix instead, so each item draw carries the pose over to it.
 * Everything lands in call order and in depth: the game draws a slot's item about 200 deep, so a later
 * fill under it stays hidden until {@link #nextLayer} lifts the pose.
 *
 * <p>Client only. Never reachable from a class a dedicated server loads.
 */
public final class GuiDraw extends Draw {

    /**
     * What {@link #nextLayer} lifts: over a container's slot items (drawn about 200 deep) and their counts
     * (300). The carried item is drawn 300 deep as well, so an open overlay covers it where the two meet.
     */
    private static final float LAYER = 310F;

    private static final Map<String, ItemStack> ITEMS = new ConcurrentHashMap<>();

    /** The open scissor boxes, GUI coordinates after the pose, each cut to the one under it. */
    private static final Deque<int[]> SCISSORS = new ArrayDeque<>();

    private final PoseStack pose;
    private final Font font;

    public GuiDraw(PoseStack pose) {
        this.pose = pose;
        this.font = Minecraft.getInstance().font;
    }

    public int guiWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    public int guiHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    private static ItemRenderer items() {
        return Minecraft.getInstance().getItemRenderer();
    }

    /** Runs an item renderer call with the pose on the GL matrix, where that renderer reads it. */
    @SuppressWarnings("deprecation")
    private void posed(Runnable draw) {
        RenderSystem.pushMatrix();
        RenderSystem.multMatrix(this.pose.last().pose());
        draw.run();
        RenderSystem.popMatrix();
    }

    /** {@code stack} at {@code x, y} under the pose, without its count or bar. */
    public void renderItem(ItemStack stack, int x, int y) {
        posed(() -> items().renderAndDecorateItem(stack, x, y));
    }

    @Override
    public void item(String itemId, int x, int y) {
        ItemStack stack = byId(itemId);
        if (!stack.isEmpty()) {
            renderItem(stack, x, y);
        }
    }

    private static ItemStack byId(String itemId) {
        return ITEMS.computeIfAbsent(itemId, id -> {
            ResourceLocation parsed = GameIds.id(id);
            return parsed == null ? ItemStack.EMPTY
                    : GameIds.ITEMS.getOptional(parsed).map(ItemStack::new).orElse(ItemStack.EMPTY);
        });
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
            posed(() -> items().renderGuiItemDecorations(this.font, stack, x, y));
        }
    }

    @Override
    public void itemDecorations(ItemView item, int x, int y, String count) {
        ItemStack stack = stack(item);
        if (!stack.isEmpty()) {
            // an empty string hides the count: the game draws its own for a null one
            posed(() -> items().renderGuiItemDecorations(this.font, stack, x, y, count == null ? "" : count));
        }
    }

    /** Tooltips go through the open screen, which these versions draw them with; none over the HUD. */
    @Override
    public boolean itemTooltip(ItemView item, int mx, int my) {
        ItemStack stack = stack(item);
        Screen screen = Minecraft.getInstance().screen;
        if (stack.isEmpty() || screen == null) {
            return false;
        }
        screen.renderComponentTooltip(this.pose, screen.getTooltipFromItem(stack), mx, my);
        return true;
    }

    @Override
    public void sprite(String atlas, String sprite, int x, int y, int w, int h, float alpha) {
        ResourceLocation id = GameIds.id(sprite);
        ResourceLocation atlasId = GameIds.id(atlas);
        if (id == null || atlasId == null || SpriteIcon.GUI.equals(atlas)
                || GameSprites.atlasMarker(this, atlasId, id, x, y, w, h, alpha)) {
            return;
        }
        textured(alpha, () -> GameSprites.atlas(this.pose, atlasId, id, x, y, w, h));
    }

    @Override
    public boolean guiPart(GuiPart part, int x, int y, int w, int h, float alpha) {
        return GameSprites.part(this, part, x, y, w, h, alpha);
    }

    @Override
    public boolean entity(EntityView view, int x0, int y0, int x1, int y1, float yaw, float pitch) {
        return EntityDraw.draw(this, this.pose, view, x0, y0, x1, y1, yaw, pitch);
    }

    private static Style style(int color, int style) {
        Style s = Style.EMPTY.withColor(TextColor.fromRgb(color & 0xFFFFFF))
                .withBold((style & RichText.BOLD) != 0)
                .withItalic((style & RichText.ITALIC) != 0)
                .withUnderlined((style & RichText.UNDERLINE) != 0);
        // 1.16.5 has no withStrikethrough or withObfuscated: the formatting codes set them.
        if ((style & RichText.STRIKE) != 0) {
            s = s.applyFormat(ChatFormatting.STRIKETHROUGH);
        }
        if ((style & RichText.OBFUSCATED) != 0) {
            s = s.applyFormat(ChatFormatting.OBFUSCATED);
        }
        return s;
    }

    @Override
    protected void styledString(String text, int x, int y, int argb, int style) {
        this.font.draw(this.pose, GameScreens.literal(text).setStyle(style(argb, style)), x, y, argb);
    }

    @Override
    public int styledWidth(String text, int style) {
        return this.font.width(GameScreens.literal(text).setStyle(style(0, style)));
    }

    @Override
    protected void rect(int x0, int y0, int x1, int y1, int argb) {
        GuiComponent.fill(this.pose, x0, y0, x1, y1, argb);
    }

    /** One vertex-coloured quad instead of the base's fill per pixel row. */
    @Override
    protected void vertical(int x0, int y0, int x1, int y1, int top, int bottom) {
        Gradients.INSTANCE.fill(this.pose, x0, y0, x1, y1, top, bottom);
    }

    /** {@code fillGradient} is an instance method only a subclass may call. */
    private static final class Gradients extends GuiComponent {
        static final Gradients INSTANCE = new Gradients();

        void fill(PoseStack pose, int x0, int y0, int x1, int y1, int top, int bottom) {
            fillGradient(pose, x0, y0, x1, y1, top, bottom);
        }
    }

    @Override
    protected void string(String text, int x, int y, int argb) {
        this.font.draw(this.pose, text, x, y, argb);
    }

    @Override
    public int width(String text) {
        return this.font.width(text);
    }

    @Override
    public String cut(String text, int maxWidth) {
        return this.font.plainSubstrByWidth(text, maxWidth);
    }

    /** The texture blits take no colour: alpha goes through the GL colour, with blending on. */
    @SuppressWarnings("deprecation")
    private static void textured(float alpha, Runnable blit) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.color4f(1F, 1F, 1F, Maths.clamp(alpha, 0F, 1F));
        blit.run();
        RenderSystem.color4f(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
    }

    private static void textured(ResourceLocation texture, float alpha, Runnable blit) {
        textured(alpha, () -> {
            Minecraft.getInstance().getTextureManager().bind(texture);
            blit.run();
        });
    }

    @Override
    public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH, float alpha) {
        ResourceLocation id = GameIds.id(texture);
        if (id != null) {
            textured(id, alpha, () -> GuiComponent.blit(this.pose, x, y, (float) u, (float) v, w, h, texW, texH));
        }
    }

    @Override
    public void image(String texture, int x, int y, int w, int h, int texW, int texH, float alpha) {
        if (GameSprites.markerFile(this, texture, x, y, w, h, alpha)) {
            return;
        }
        ResourceLocation id = GameIds.id(texture);
        if (id != null) {
            textured(id, alpha, () -> GuiComponent.blit(this.pose, x, y, w, h, 0F, 0F, texW, texH, texW, texH));
        }
    }

    @Override
    public void blitRegion(String texture, int x, int y, int w, int h, int u, int v, int regionW, int regionH,
                           int texW, int texH, float alpha) {
        ResourceLocation id = GameIds.id(texture);
        if (id != null) {
            textured(id, alpha,
                    () -> GuiComponent.blit(this.pose, x, y, w, h, (float) u, (float) v, regionW, regionH, texW, texH));
        }
    }

    @Override
    public int guiScale() {
        return Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
    }

    /** A layer up in depth: what comes next covers the items the game drew in between. The caller pops it. */
    @Override
    public void nextLayer() {
        this.pose.translate(0F, 0F, LAYER);
    }

    @Override
    protected void pushPose(float x, float y, float scale) {
        this.pose.pushPose();
        this.pose.translate(x, y, 0F);
        this.pose.scale(scale, scale, 1F);
    }

    @Override
    protected void popPose() {
        this.pose.popPose();
    }

    @Override
    public void scissor(int x0, int y0, int x1, int y1) {
        Matrix4f m = this.pose.last().pose();
        Vector4f a = new Vector4f(x0, y0, 0F, 1F);
        Vector4f b = new Vector4f(x1, y1, 0F, 1F);
        a.transform(m);
        b.transform(m);
        x0 = (int) Math.floor(Math.min(a.x(), b.x()));
        y0 = (int) Math.floor(Math.min(a.y(), b.y()));
        x1 = (int) Math.floor(Math.max(a.x(), b.x()));
        y1 = (int) Math.floor(Math.max(a.y(), b.y()));
        int[] under = SCISSORS.peek();
        int[] box = under == null ? new int[] {x0, y0, x1, y1}
                : new int[] {Math.max(x0, under[0]), Math.max(y0, under[1]), Math.min(x1, under[2]), Math.min(y1, under[3])};
        SCISSORS.push(box);
        apply(box);
    }

    @Override
    public void endScissor() {
        SCISSORS.poll();
        int[] under = SCISSORS.peek();
        if (under == null) {
            RenderSystem.disableScissor();
        } else {
            apply(under);
        }
    }

    /** In window pixels from the bottom left, the box already through the pose. */
    private static void apply(int[] box) {
        Window window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        int w = Math.max(0, box[2] - box[0]);
        int h = Math.max(0, box[3] - box[1]);
        RenderSystem.enableScissor((int) (box[0] * scale), (int) (window.getHeight() - (box[1] + h) * scale),
                (int) (w * scale), (int) (h * scale));
    }

    /** Through the open screen, as {@link #itemTooltip}. */
    @Override
    public void tooltip(List<String> lines, int x, int y) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) {
            return;
        }
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            // The first line names the thing, the rest is said about it.
            Component line = GameScreens.literal(lines.get(i))
                    .withStyle(i == 0 ? ChatFormatting.WHITE : ChatFormatting.GRAY);
            wrapped.addAll(this.font.split(line, TOOLTIP_WIDTH));
        }
        screen.renderTooltip(this.pose, wrapped, x, y);
    }
}
