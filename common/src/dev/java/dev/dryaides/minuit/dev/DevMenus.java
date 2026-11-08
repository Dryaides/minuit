package dev.dryaides.minuit.dev;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/** Dev only: the menu type, its id and the {@code /minuit_devslots} command, shared by both loaders. */
public final class DevMenus {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("minuit", "dev_slots");

    public static final MenuType<DevSlotMenu> TYPE =
            new MenuType<>((id, inventory) -> new DevSlotMenu(DevMenus.TYPE, id, inventory), FeatureFlags.DEFAULT_FLAGS);

    private DevMenus() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("minuit_devslots").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new DevSlotMenu(TYPE, id, inventory, DevSlotMenu.SHARED),
                    Component.literal("minuit dev slots")));
            return 1;
        });
    }
}
