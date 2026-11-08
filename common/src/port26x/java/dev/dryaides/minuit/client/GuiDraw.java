package dev.dryaides.minuit.client;

import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.EntityView;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.RichText;
import dev.dryaides.minuit.SpriteIcon;
import dev.dryaides.minuit.internal.GuiPart;
import dev.dryaides.minuit.internal.NineSlice;
import dev.dryaides.minuit.internal.PartTable;
import dev.dryaides.minuit.port.mc.EntityDraw;
import dev.dryaides.minuit.port.mc.GameIds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link Draw} over the game's renderer, and the only class of the kit that calls
 * {@code GuiGraphicsExtractor}. Every call has the same descriptor in the 26.1.2 and 26.2 jars, so
 * one source tree ships both versions.
 *
 * <p>Client only. Never reachable from a class a dedicated server loads.
 */
public final class GuiDraw extends Draw {

    private static final java.util.Map<String, ItemStack> ITEMS = new java.util.concurrent.ConcurrentHashMap<>();

    private final GuiGraphicsExtractor g;
    private final Font font;

    public GuiDraw(GuiGraphicsExtractor g) {
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
            this.g.item(stack, x, y);
        }
    }

    private static ItemStack byId(String itemId) {
        return ITEMS.computeIfAbsent(itemId, id -> {
            Identifier parsed = GameIds.id(id);
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
            this.g.item(stack, x, y);
        }
    }

    @Override
    public void itemDecorations(ItemView item, int x, int y) {
        ItemStack stack = stack(item);
        if (!stack.isEmpty()) {
            this.g.itemDecorations(this.font, stack, x, y);
        }
    }

    @Override
    public void itemDecorations(ItemView item, int x, int y, String count) {
        ItemStack stack = stack(item);
        if (!stack.isEmpty()) {
            // an empty string hides the count: the game draws its own for a null one
            this.g.itemDecorations(this.font, stack, x, y, count == null ? "" : count);
        }
    }

    @Override
    public boolean itemTooltip(ItemView item, int mx, int my) {
        ItemStack stack = stack(item);
        if (stack.isEmpty()) {
            return false;
        }
        this.g.setTooltipForNextFrame(this.font, stack, mx, my);
        return true;
    }

    /** Through the atlas, so a sprite of any pixel size fills the box and an animated one moves. */
    @Override
    public void sprite(String atlas, String sprite, int x, int y, int w, int h, float alpha) {
        Identifier id = GameIds.id(sprite);
        Identifier atlasId = GameIds.id(atlas);
        if (id == null || atlasId == null) {
            return;
        }
        if (SpriteIcon.GUI.equals(atlas)) {
            this.g.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, w, h, alpha);
            return;
        }
        this.g.blitSprite(RenderPipelines.GUI_TEXTURED, Minecraft.getInstance().getAtlasManager().get(new SpriteId(atlasId, id)),
                x, y, w, h, white(alpha));
    }

    /** Through the GUI atlas, whose .mcmeta nine-slices the sprite, or a texture cut in nine; resource packs restyle both. */
    @Override
    public boolean guiPart(GuiPart part, int x, int y, int w, int h, float alpha) {
        PartTable.Art art = PartTable.ATLAS.resolve(part);
        if (!(art instanceof PartTable.Sprite sprite)) {
            return NineSlice.draw(this, art, x, y, w, h, alpha);
        }
        Identifier id = GameIds.id(sprite.id());
        if (id == null) {
            return false;
        }
        this.g.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, w, h, alpha);
        return true;
    }

    @Override
    public boolean entity(EntityView view, int x0, int y0, int x1, int y1, float yaw, float pitch) {
        return EntityDraw.draw(this.g, view, x0, y0, x1, y1, yaw, pitch);
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
        this.g.text(this.font, Component.literal(text).setStyle(style(argb, style)), x, y, argb, false);
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
    protected void string(String text, int x, int y, int argb) {
        this.g.text(this.font, text, x, y, argb, false);
    }

    @Override
    public int width(String text) {
        return this.font.width(text);
    }

    @Override
    protected String cut(String text, int maxWidth) {
        return this.font.plainSubstrByWidth(text, maxWidth);
    }

    private static int white(float alpha) {
        return (Math.round(Math.clamp(alpha, 0F, 1F) * 255F) << 24) | 0xFFFFFF;
    }

    @Override
    public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH, float alpha) {
        Identifier id = GameIds.id(texture);
        if (id != null) {
            this.g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, texW, texH, white(alpha));
        }
    }

    @Override
    public void image(String texture, int x, int y, int w, int h, int texW, int texH, float alpha) {
        Identifier id = GameIds.id(texture);
        if (id != null) {
            this.g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0F, 0F, w, h, texW, texH, texW, texH, white(alpha));
        }
    }

    @Override
    public void blitRegion(String texture, int x, int y, int w, int h, int u, int v, int regionW, int regionH,
                           int texW, int texH, float alpha) {
        Identifier id = GameIds.id(texture);
        if (id != null) {
            this.g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, regionW, regionH, texW, texH, white(alpha));
        }
    }

    @Override
    public int guiScale() {
        return Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
    }

    /** A stratum up: what comes next covers the items the game drew in between. */
    @Override
    public void nextLayer() {
        this.g.nextStratum();
    }

    @Override
    public void push(float x, float y, float scale) {
        this.g.pose().pushMatrix();
        this.g.pose().translate(x, y);
        this.g.pose().scale(scale, scale);
    }

    @Override
    public void pop() {
        this.g.pose().popMatrix();
    }

    @Override
    public void scissor(int x0, int y0, int x1, int y1) {
        this.g.enableScissor(x0, y0, x1, y1);
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
        this.g.setTooltipForNextFrame(this.font, wrapped, x, y);
    }
}
