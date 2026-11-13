package dev.dryaides.minuit;

/**
 * What the game's own HUD shows on this frame, filled by the loader side before {@link HudLayer#render}, and the
 * boxes it covers, which {@link HudPart}s keep clear of. Plain fields so a feed writes them each frame with no
 * allocation. Every size is in GUI pixels.
 *
 * <p>The boxes follow the 26.1.2 client ({@code Gui}, {@code BossHealthOverlay}, {@code ChatComponent},
 * {@code SubtitleOverlay}); 1.12.2 to 1.21.1 place them the same. Subtitles and the scoreboard sidebar change
 * width with their text, so they are kept as fixed boxes wide enough for common lines. Toasts, titles, the action
 * bar, the held item's name and the tab list stay a few seconds and are not kept clear: a part that moved for each
 * of them would jump around.
 *
 * <p>The chat is kept clear only for the bottom-left and middle-left anchors. Its box spans 328 px from the left edge, past the hotbar's
 * left end on any screen under 838 px wide, while its lines show for ten seconds after a message: parts at the
 * bottom centre stay just above the hearts, as the game's own rows do, and the chat draws over them while it
 * shows.
 */
@Incubating
public final class VanillaHud {

    /** What a kept box is, for a debug view or a bench. */
    public enum Kind { HOTBAR, OFFHAND, ATTACK, XP, HEALTH, ARMOUR, FOOD, AIR, BOSS, EFFECTS, CHAT, CROSSHAIR, SUBTITLES, SIDEBAR }

    public int guiWidth;
    public int guiHeight;
    /** F1: nothing of the HUD draws, minuit's parts neither. */
    public boolean hidden;
    /** Hearts, armour, food and air: survival and adventure. */
    public boolean statusBars = true;
    /** Rows of hearts, absorption included; see {@link #healthRows(float, float)}. */
    public int healthRows = 1;
    public boolean armour;
    public boolean air;
    /** Rows of the ridden animal's hearts, which replace the food row; 0 when not riding one. */
    public int mountRows;
    /** The experience, locator or jump bar over the hotbar, with the level number above it. */
    public boolean contextualBar = true;
    /** The offhand slot beside the hotbar: -1 on the left, 1 on the right, 0 when empty. */
    public int offhand;
    /** The attack cooldown drawn beside the hotbar: -1 on the left, 1 on the right, 0 when not there. */
    public int attackIndicator;
    public int bossBars;
    public int goodEffects;
    public int badEffects;
    /** The chat is set to show; its box is kept even between messages so parts do not jump. */
    public boolean chat = true;
    /** The chat's width and unfocused height, chat scale applied. */
    public int chatWidth = 320;
    public int chatHeight = 90;
    /** How far above the screen's bottom the chat's last line ends: 40 on every port from 1.12.2 to 26.x. */
    public int chatBottom = 40;
    public boolean subtitles;
    public boolean sidebar;
    public boolean crosshair = true;

    private int count;
    private final int[] boxes = new int[4 * 24];
    private final Kind[] kinds = new Kind[24];
    private final int[] masks = new int[24];

    private static final int ALL = (1 << HudAnchor.values().length) - 1;
    private static final int LEFT = 1 << HudAnchor.MIDDLE_LEFT.ordinal() | 1 << HudAnchor.BOTTOM_LEFT.ordinal();

    /** Rows of hearts the game draws for this much health and absorption. */
    public static int healthRows(float maxHealth, float absorption) {
        return Math.max(1, (int) Math.ceil((maxHealth + absorption) / 2F / 10F));
    }

    /** The space between rows of hearts once there are many. */
    public static int healthRowStep(int rows) {
        return Math.max(10 - (rows - 2), 3);
    }

    /** Copies every field of {@code o}. */
    public void copyFrom(VanillaHud o) {
        guiWidth = o.guiWidth;
        guiHeight = o.guiHeight;
        hidden = o.hidden;
        statusBars = o.statusBars;
        healthRows = o.healthRows;
        armour = o.armour;
        air = o.air;
        mountRows = o.mountRows;
        contextualBar = o.contextualBar;
        offhand = o.offhand;
        attackIndicator = o.attackIndicator;
        bossBars = o.bossBars;
        goodEffects = o.goodEffects;
        badEffects = o.badEffects;
        chat = o.chat;
        chatWidth = o.chatWidth;
        chatHeight = o.chatHeight;
        chatBottom = o.chatBottom;
        subtitles = o.subtitles;
        sidebar = o.sidebar;
        crosshair = o.crosshair;
    }

    public boolean sameAs(VanillaHud o) {
        return guiWidth == o.guiWidth && guiHeight == o.guiHeight && hidden == o.hidden && statusBars == o.statusBars
                && healthRows == o.healthRows && armour == o.armour && air == o.air && mountRows == o.mountRows
                && contextualBar == o.contextualBar && offhand == o.offhand && attackIndicator == o.attackIndicator
                && bossBars == o.bossBars && goodEffects == o.goodEffects && badEffects == o.badEffects
                && chat == o.chat && chatWidth == o.chatWidth && chatHeight == o.chatHeight
                && chatBottom == o.chatBottom && subtitles == o.subtitles && sidebar == o.sidebar
                && crosshair == o.crosshair;
    }

    /** Works out the kept boxes from the fields; read them with {@link #count} and the accessors after. */
    public void reserve() {
        count = 0;
        int w = guiWidth;
        int h = guiHeight;
        int left = w / 2 - 91;
        int right = w / 2 + 91;
        add(Kind.HOTBAR, left, h - 22, right, h);
        if (offhand != 0) {
            add(Kind.OFFHAND, offhand < 0 ? left - 29 : right, h - 23, offhand < 0 ? left : right + 29, h);
        }
        if (attackIndicator != 0) {
            add(Kind.ATTACK, attackIndicator < 0 ? left - 6 - 18 : right + 6, h - 20,
                    attackIndicator < 0 ? left - 6 : right + 6 + 18, h - 2);
        }
        if (contextualBar) {
            add(Kind.XP, left, h - 35, right, h - 24);
        }
        if (statusBars) {
            int rows = Math.max(1, healthRows);
            int heartsTop = h - 39 - (rows - 1) * healthRowStep(rows);
            add(Kind.HEALTH, left, heartsTop, left + 81, h - 30);
            if (armour) {
                add(Kind.ARMOUR, left, heartsTop - 10, left + 81, heartsTop - 1);
            }
            int rightRows = Math.max(1, mountRows);
            add(Kind.FOOD, right - 81, h - 39 - (rightRows - 1) * 10, right, h - 30);
            if (air) {
                int airTop = h - 39 - rightRows * 10;
                add(Kind.AIR, right - 81, airTop, right, airTop + 9);
            }
        } else if (mountRows > 0) {
            add(Kind.FOOD, right - 81, h - 39 - (mountRows - 1) * 10, right, h - 30);
        }
        int bosses = shownBossBars();
        if (bosses > 0) {
            add(Kind.BOSS, left, 3, right, 12 + 19 * (bosses - 1) + 5);
        }
        if (goodEffects > 0) {
            add(Kind.EFFECTS, w - 25 * goodEffects, 1, w, 25);
        }
        if (badEffects > 0) {
            add(Kind.EFFECTS, w - 25 * badEffects, 27, w, 51);
        }
        if (chat) {
            int bottom = h - chatBottom;
            add(Kind.CHAT, 0, Math.max(0, bottom - chatHeight), chatWidth + 8, bottom, LEFT);
        }
        if (crosshair) {
            add(Kind.CROSSHAIR, w / 2 - 8, h / 2 - 8, w / 2 + 8, h / 2 + 8);
        }
        if (subtitles) {
            add(Kind.SUBTITLES, w - 152, h - 30 - 50, w, h - 30);
        }
        if (sidebar) {
            add(Kind.SIDEBAR, w - 102, Math.max(0, h / 2 - 72), w, Math.min(h, h / 2 + 72));
        }
    }

    /** The game stops drawing boss bars once the next would start below a third of the screen. */
    private int shownBossBars() {
        int shown = 0;
        int y = 12;
        while (shown < bossBars) {
            shown++;
            y += 19;
            if (y >= guiHeight / 3) {
                break;
            }
        }
        return shown;
    }

    private void add(Kind kind, int x0, int y0, int x1, int y1) {
        add(kind, x0, y0, x1, y1, ALL);
    }

    private void add(Kind kind, int x0, int y0, int x1, int y1, int anchors) {
        if (count == kinds.length) {
            return;
        }
        int i = 4 * count;
        boxes[i] = x0;
        boxes[i + 1] = y0;
        boxes[i + 2] = x1;
        boxes[i + 3] = y1;
        masks[count] = anchors;
        kinds[count++] = kind;
    }

    public int count() {
        return count;
    }

    /** Whether box {@code i} is kept clear for parts at {@code anchor}. */
    public boolean blocks(int i, HudAnchor anchor) {
        return (masks[i] & 1 << anchor.ordinal()) != 0;
    }

    /** The anchors box {@code i} is kept clear for, one bit per {@link HudAnchor#ordinal()}. */
    public int anchors(int i) {
        return masks[i];
    }

    public Kind kind(int i) {
        return kinds[i];
    }

    public int x0(int i) {
        return boxes[4 * i];
    }

    public int y0(int i) {
        return boxes[4 * i + 1];
    }

    public int x1(int i) {
        return boxes[4 * i + 2];
    }

    public int y1(int i) {
        return boxes[4 * i + 3];
    }
}
