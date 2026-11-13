package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.HudLayer;
import dev.dryaides.minuit.VanillaHud;
import dev.dryaides.minuit.client.GuiDraw;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.ChatVisiblity;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * The HUD layer of {@link HudLayer#main()}: fills a {@link VanillaHud} from the 1.20.1 and 1.21.1 client with the
 * fields the game's own {@code Gui} reads, then draws the parts. Forge and NeoForge draw it under the chat, Fabric's
 * HUD callback after it. Client only, render thread only.
 */
public final class HudFeed {

    private static final VanillaHud HUD = new VanillaHud();
    private static Field bossEvents;
    private static boolean bossLookedUp;
    /** Effects are counted again when their number changes and every 10 frames: iterating them allocates each frame. */
    private static final int EFFECT_RECOUNT = 10;
    private static int effectsSeen = -1;
    private static int effectAge;
    private static int goodEffects;
    private static int badEffects;

    private HudFeed() {
    }

    public static void render(GuiGraphics graphics, float partial) {
        HudLayer layer = HudLayer.main();
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.options.hideGui) {
            return;
        }
        fill(client, player, graphics.guiWidth(), graphics.guiHeight());
        layer.render(new GuiDraw(graphics), HUD, partial);
    }

    private static void fill(Minecraft client, LocalPlayer player, int width, int height) {
        Options options = client.options;
        VanillaHud hud = HUD;
        hud.guiWidth = width;
        hud.guiHeight = height;
        hud.hidden = false;
        boolean survival = client.gameMode != null && client.gameMode.canHurtPlayer();
        hud.statusBars = survival;
        hud.healthRows = VanillaHud.healthRows(player.getMaxHealth(), player.getAbsorptionAmount());
        hud.armour = player.getArmorValue() > 0;
        hud.air = player.isEyeInFluid(FluidTags.WATER) || player.getAirSupply() < player.getMaxAirSupply();
        hud.mountRows = mountRows(player.getVehicle());
        hud.contextualBar = client.gameMode != null && client.gameMode.hasExperience() || player.jumpableVehicle() != null;
        boolean rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
        hud.offhand = player.getOffhandItem().isEmpty() ? 0 : rightHanded ? -1 : 1;
        hud.attackIndicator = options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR ? (rightHanded ? 1 : -1) : 0;
        hud.bossBars = bossBars(client.gui.getBossOverlay());
        int effects = player.getActiveEffects().size();
        if (effects != effectsSeen || ++effectAge >= EFFECT_RECOUNT) {
            effectsSeen = effects;
            effectAge = 0;
            int good = 0;
            int bad = 0;
            for (MobEffectInstance effect : player.getActiveEffects()) {
                if (effect.showIcon()) {
                    if (GameScreens.beneficial(effect)) {
                        good++;
                    } else {
                        bad++;
                    }
                }
            }
            goodEffects = good;
            badEffects = bad;
        }
        hud.goodEffects = goodEffects;
        hud.badEffects = badEffects;
        hud.chat = options.chatVisibility().get() != ChatVisiblity.HIDDEN;
        double chatScale = options.chatScale().get();
        hud.chatWidth = (int) Math.ceil(ChatComponent.getWidth(options.chatWidth().get()) * chatScale);
        hud.chatHeight = (int) Math.ceil(ChatComponent.getHeight(options.chatHeightUnfocused().get()) * chatScale);
        hud.chatBottom = 40;
        hud.subtitles = options.showSubtitles().get();
        hud.sidebar = GameScreens.sidebarShown(client);
        hud.crosshair = options.getCameraType().isFirstPerson();
    }

    /** Rows of the ridden animal's hearts, as the game's vehicle health draws them: 10 a row, 30 at most. */
    private static int mountRows(Entity vehicle) {
        if (!(vehicle instanceof LivingEntity living) || !living.showVehicleHealth()) {
            return 0;
        }
        int hearts = Math.min(30, (int) (living.getMaxHealth() + 0.5F) / 2);
        return (hearts + 9) / 10;
    }

    /** The overlay keeps its bars in a private map; found by type once, as no getter exposes the count. */
    private static int bossBars(BossHealthOverlay overlay) {
        if (!bossLookedUp) {
            bossLookedUp = true;
            for (Field f : BossHealthOverlay.class.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        bossEvents = f;
                    } catch (RuntimeException ignored) {
                        // No access: boss bars are not kept clear.
                    }
                    break;
                }
            }
        }
        if (bossEvents == null) {
            return 0;
        }
        try {
            return ((Map<?, ?>) bossEvents.get(overlay)).size();
        } catch (IllegalAccessException e) {
            return 0;
        }
    }
}
