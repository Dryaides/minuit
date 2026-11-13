package dev.dryaides.minuit.client;

import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link Draw} over the game's immediate-mode {@code GuiGraphics} (1.20.1 and 1.21.1), and the only
 * class of the kit that calls it. Everything lands in call order and in depth: the game draws an item
 * about 150 deep, so a later fill under it stays hidden until {@link #nextLayer} lifts the pose.
 *
 * <p>Client only. Never reachable from a class a dedicated server loads.
 */
public final class GuiDraw extends Draw {

    /**
     * What {@link #nextLayer} lifts: over a container's slot items (100 up, drawn about 150 deep, their
     * counts at 200) and under the carried item (232 up).
     */
    private static final float LAYER = 310F;

    private static final java.util.Map<String, ItemStack> ITEMS = new java.util.concurrent.ConcurrentHashMap<>();

    private final GuiGraphics g;
    private final Font font;

    public GuiDraw(GuiGraphics g) {
        this.g = g;
        this.font = Minecraft.getInstance().font;
    }

    public int guiWidth() {
        return this.g.guiWidth();
    }

    public int guiHeight() {
        return this.g.guiHeight();
    }

    @Override
    public void item(String itemId, int x, int y) {
        ItemStack stack = byId(itemId);
        if (!stack.isEmpty()) {
            this.g.renderItem(stack, x, y);
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
        if (item.stack() instanceof ItemStack own) {
            return own;
        }
        ItemStack shared = byId(item.id());
        return item.count() == 1 || shared.isEmpty() ? shared : shared.copyWithCount(item.count());
    }

    @Override
    public void item(ItemView item, int x, int y) {
        ItemStack stack = stack(item);
        if (!stack.isEmpty()) {
            this.g.renderItem(stack, x, y);
        }
    }

    @Override
    public void itemDecorations(ItemView item, int x, int y) {
        ItemStack stack = stack(item);
        if (!stack.isEmpty()) {
            this.g.renderItemDecorations(this.font, stack, x, y);
        }
    }

    @Override
    public void itemDecorations(ItemView item, int x, int y, String count) {
        ItemStack stack = stack(item);
        if (!stack.isEmpty()) {
            // an empty string hides the count: the game draws its own for a null one
            this.g.renderItemDecorations(this.font, stack, x, y, count == null ? "" : count);
        }
    }

    @Override
    public boolean itemTooltip(ItemView item, int mx, int my) {
        ItemStack stack = stack(item);
        if (stack.isEmpty()) {
            return false;
        }
        if (itemTooltipLines(item) == null) {
            return true;
        }
        this.g.renderTooltip(this.font, stack, mx, my);
        return true;
    }

    /** The lines of the game's item tooltip, each style's colour and format as section codes; null when all are blank. */
    @Override
    public List<String> itemTooltipLines(ItemView item) {
        ItemStack stack = stack(item);
        if (stack.isEmpty()) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        boolean blank = true;
        for (Component line : net.minecraft.client.gui.screens.Screen.getTooltipFromItem(Minecraft.getInstance(), stack)) {
            StringBuilder out = new StringBuilder();
            line.visit((style, text) -> {
                out.append(ChatFormatting.RESET);
                if (style.getColor() != null) {
                    ChatFormatting named = ChatFormatting.getByName(style.getColor().serialize());
                    if (named != null) {
                        out.append(named);
                    }
                }
                if (style.isBold()) out.append(ChatFormatting.BOLD);
                if (style.isItalic()) out.append(ChatFormatting.ITALIC);
                if (style.isUnderlined()) out.append(ChatFormatting.UNDERLINE);
                if (style.isStrikethrough()) out.append(ChatFormatting.STRIKETHROUGH);
                out.append(text);
                return java.util.Optional.empty();
            }, Style.EMPTY);
            blank &= line.getString().isBlank();
            lines.add(out.toString());
        }
        return blank ? null : lines;
    }

    /** Through the atlas, so a sprite of any pixel size fills the box and an animated one moves. */
    @Override
    public void sprite(String atlas, String sprite, int x, int y, int w, int h, float alpha) {
        ResourceLocation id = GameIds.id(sprite);
        ResourceLocation atlasId = GameIds.id(atlas);
        if (id == null || atlasId == null) {
            return;
        }
        if (SpriteIcon.GUI.equals(atlas)) {
            GameSprites.gui(this.g, id, x, y, w, h, alpha);
            return;
        }
        GameSprites.atlas(this.g, atlasId, id, x, y, w, h, alpha);
    }

    /** Through the GUI atlas, whose .mcmeta nine-slices the sprite, so resource packs restyle it. */
    @Override
    public boolean guiPart(GuiPart part, int x, int y, int w, int h, float alpha) {
        return GameSprites.part(this.g, part, x, y, w, h, alpha);
    }

    @Override
    public boolean entity(EntityView view, int x0, int y0, int x1, int y1, float yaw, float pitch) {
        return EntityDraw.draw(this, this.g, view, x0, y0, x1, y1, yaw, pitch);
    }

    private static Style style(int color, int style) {
        return Style.EMPTY.withColor(color & 0xFFFFFF)
                .withBold((style & RichText.BOLD) != 0)
                .withItalic((style & RichText.ITALIC) != 0)
                .withUnderlined((style & RichText.UNDERLINE) != 0)
                .withStrikethrough((style & RichText.STRIKE) != 0)
                .withObfuscated((style & RichText.OBFUSCATED) != 0);
    }

    @Override
    protected void styledString(String text, int x, int y, int argb, int style) {
        this.g.drawString(this.font, Component.literal(text).setStyle(style(argb, style)), x, y, argb, false);
    }

    @Override
    public int styledWidth(String text, int style) {
        return this.font.width(Component.literal(text).setStyle(style(0, style)));
    }

    @Override
    protected void rect(int x0, int y0, int x1, int y1, int argb) {
        this.g.fill(x0, y0, x1, y1, argb);
    }

    @Override
    protected void vertical(int x0, int y0, int x1, int y1, int top, int bottom) {
        this.g.fillGradient(x0, y0, x1, y1, top, bottom);
    }

    @Override
    protected void string(String text, int x, int y, int argb) {
        this.g.drawString(this.font, text, x, y, argb, false);
    }

    @Override
    public int width(String text) {
        return this.font.width(text);
    }

    @Override
    public String cut(String text, int maxWidth) {
        return this.font.plainSubstrByWidth(text, maxWidth);
    }

    /** The texture blits take no colour: alpha goes through the shader colour, with blending on. */
    private void textured(float alpha, Runnable blit) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        this.g.setColor(1F, 1F, 1F, Maths.clamp(alpha, 0F, 1F));
        blit.run();
        this.g.setColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
    }

    @Override
    public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH, float alpha) {
        ResourceLocation id = GameIds.id(texture);
        if (id != null) {
            textured(alpha, () -> this.g.blit(id, x, y, (float) u, (float) v, w, h, texW, texH));
        }
    }

    @Override
    public void image(String texture, int x, int y, int w, int h, int texW, int texH, float alpha) {
        if (GameSprites.markerFile(this.g, texture, x, y, w, h, alpha)) {
            return;
        }
        ResourceLocation id = GameIds.id(texture);
        if (id != null) {
            textured(alpha, () -> this.g.blit(id, x, y, w, h, 0F, 0F, texW, texH, texW, texH));
        }
    }

    @Override
    public void blitRegion(String texture, int x, int y, int w, int h, int u, int v, int regionW, int regionH,
                           int texW, int texH, float alpha) {
        ResourceLocation id = GameIds.id(texture);
        if (id != null) {
            textured(alpha, () -> this.g.blit(id, x, y, w, h, (float) u, (float) v, regionW, regionH, texW, texH));
        }
    }

    @Override
    public int guiScale() {
        return Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
    }

    /** A layer up in depth: what comes next covers the items the game drew in between. The caller pops it. */
    @Override
    public void nextLayer() {
        this.g.pose().translate(0F, 0F, LAYER);
    }

    @Override
    protected void pushPose(float x, float y, float scale) {
        this.g.pose().pushPose();
        this.g.pose().translate(x, y, 0F);
        this.g.pose().scale(scale, scale, 1F);
    }

    @Override
    protected void popPose() {
        this.g.pose().popPose();
    }

    @Override
    public void scissor(int x0, int y0, int x1, int y1) {
        // GuiGraphics.enableScissor ignores the pose before 26.x.
        Matrix4f pose = this.g.pose().last().pose();
        Vector3f a = pose.transformPosition(x0, y0, 0F, new Vector3f());
        Vector3f b = pose.transformPosition(x1, y1, 0F, new Vector3f());
        this.g.enableScissor((int) Math.floor(Math.min(a.x, b.x)), (int) Math.floor(Math.min(a.y, b.y)),
                (int) Math.floor(Math.max(a.x, b.x)), (int) Math.floor(Math.max(a.y, b.y)));
    }

    @Override
    public void endScissor() {
        this.g.disableScissor();
    }

    @Override
    public void tooltip(List<String> lines, int x, int y) {
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            // The first line names the thing, the rest is said about it.
            Component line = Component.literal(lines.get(i))
                    .withStyle(i == 0 ? ChatFormatting.WHITE : ChatFormatting.GRAY);
            wrapped.addAll(this.font.split(line, TOOLTIP_WIDTH));
        }
        this.g.renderTooltip(this.font, wrapped, x, y);
    }
}
