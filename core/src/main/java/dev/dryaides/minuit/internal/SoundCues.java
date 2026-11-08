package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.UiSound;

/** Which game sound, pitch and volume each of the kit's cues plays. Not API. */
public final class SoundCues {

    /** Plays a game sound by id at a pitch and a volume. */
    @FunctionalInterface
    public interface Player {
        void play(String soundId, float pitch, float volume);
    }

    public static final String CLICK = "minecraft:ui.button.click";
    public static final String HAT = "minecraft:block.note_block.hat";
    public static final String BELL = "minecraft:block.note_block.bell";

    private SoundCues() {
    }

    public static UiSound over(Player player) {
        return new UiSound() {
            @Override
            public void click() {
                // the game's own button click: forUI(holder, pitch) plays at 0.25
                player.play(CLICK, 1.0F, 0.25F);
            }

            @Override
            public void tap() {
                player.play(CLICK, 1.0F, 0.45F);
            }

            @Override
            public void tick(float frac) {
                player.play(HAT, 0.8F + 0.9F * Maths.clamp(frac, 0F, 1F), 0.55F);
            }

            @Override
            public void land() {
                player.play(HAT, 2.0F, 0.55F);
            }

            @Override
            public void confirm() {
                player.play(BELL, 1.5F, 0.5F);
            }
        };
    }
}
