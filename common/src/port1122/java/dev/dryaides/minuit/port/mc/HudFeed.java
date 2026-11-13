package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.HudLayer;
import dev.dryaides.minuit.VanillaHud;
import dev.dryaides.minuit.client.GuiDraw;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiBossOverlay;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHandSide;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * The HUD layer of {@link HudLayer#main()}, under the chat: fills a {@link VanillaHud} from the 1.12.2 client with
 * the fields {@code GuiIngameForge} reads, then draws the parts. Client only, render thread only.
 */
public final class HudFeed {

    private static final VanillaHud HUD = new VanillaHud();
    private static Field bossEvents;
    private static boolean bossLookedUp;
    /** Effects are counted again when their number changes and every 10 frames: iterating them allocates each frame. */
    private static final int EFFECT_RECOUNT = 10;
    private static ScaledResolution res;
    private static int resWidth;
    private static int resHeight;
    private static int resScale;
    private static boolean resUnicode;
    private static int effectsSeen = -1;
    private static int effectAge;
    private static int goodEffects;
    private static int badEffects;

    private HudFeed() {
    }

    public static void render(float partial) {
        Minecraft client = Minecraft.getMinecraft();
        EntityPlayerSP player = client.player;
        if (player == null || client.gameSettings.hideGUI) {
            return;
        }
        if (res == null || client.displayWidth != resWidth || client.displayHeight != resHeight
                || client.gameSettings.guiScale != resScale || client.isUnicode() != resUnicode) {
            res = new ScaledResolution(client);
            resWidth = client.displayWidth;
            resHeight = client.displayHeight;
            resScale = client.gameSettings.guiScale;
            resUnicode = client.isUnicode();
        }
        fill(client, player, res.getScaledWidth(), res.getScaledHeight());
        HudLayer.main().render(new GuiDraw(), HUD, partial);
    }

    private static void fill(Minecraft client, EntityPlayerSP player, int width, int height) {
        GameSettings options = client.gameSettings;
        VanillaHud hud = HUD;
        hud.guiWidth = width;
        hud.guiHeight = height;
        hud.hidden = false;
        hud.statusBars = client.playerController != null && client.playerController.shouldDrawHUD();
        hud.healthRows = VanillaHud.healthRows(player.getMaxHealth(), player.getAbsorptionAmount());
        hud.armour = player.getTotalArmorValue() > 0;
        hud.air = player.isInsideOfMaterial(Material.WATER);
        hud.mountRows = mountRows(player.getRidingEntity());
        hud.contextualBar = client.playerController != null && client.playerController.gameIsSurvivalOrAdventure()
                || player.isRidingHorse();
        boolean rightHanded = player.getPrimaryHand() == EnumHandSide.RIGHT;
        hud.offhand = player.getHeldItemOffhand().isEmpty() ? 0 : rightHanded ? -1 : 1;
        hud.attackIndicator = options.attackIndicator == 2 ? (rightHanded ? 1 : -1) : 0;
        hud.bossBars = bossBars(client.ingameGUI.getBossOverlay());
        int effects = player.getActivePotionEffects().size();
        if (effects != effectsSeen || ++effectAge >= EFFECT_RECOUNT) {
            effectsSeen = effects;
            effectAge = 0;
            int good = 0;
            int bad = 0;
            for (PotionEffect effect : player.getActivePotionEffects()) {
                if (effect.doesShowParticles() && effect.getPotion().shouldRenderHUD(effect)) {
                    if (effect.getPotion().isBeneficial()) {
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
        hud.chat = options.chatVisibility != EntityPlayer.EnumChatVisibility.HIDDEN;
        float chatScale = options.chatScale;
        hud.chatWidth = (int) Math.ceil(GuiNewChat.calculateChatboxWidth(options.chatWidth) * chatScale);
        hud.chatHeight = (int) Math.ceil(GuiNewChat.calculateChatboxHeight(options.chatHeightUnfocused) * chatScale);
        // GuiIngameForge moves the chat to height - 48, then GuiNewChat 8 down before its last line ends.
        hud.chatBottom = 40;
        hud.subtitles = options.showSubtitles;
        hud.sidebar = client.world != null && client.world.getScoreboard().getObjectiveInDisplaySlot(1) != null;
        hud.crosshair = options.thirdPersonView == 0;
    }

    /** Rows of the ridden animal's hearts, as GuiIngameForge draws them: 10 a row, 30 at most. */
    private static int mountRows(Entity vehicle) {
        if (!(vehicle instanceof EntityLivingBase)) {
            return 0;
        }
        int hearts = Math.min(30, (int) (((EntityLivingBase) vehicle).getMaxHealth() + 0.5F) / 2);
        return (hearts + 9) / 10;
    }

    /** The overlay keeps its bars in a private map; found by type once, as no getter exposes the count. */
    private static int bossBars(GuiBossOverlay overlay) {
        if (!bossLookedUp) {
            bossLookedUp = true;
            for (Field f : GuiBossOverlay.class.getDeclaredFields()) {
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
