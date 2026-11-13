package com.terraformersmc.modmenu.api;

import net.minecraft.client.gui.screens.Screen;

/** Compile stand-in for Mod Menu's interface; left out of the jar (fabric/build.gradle). */
@FunctionalInterface
public interface ConfigScreenFactory<S extends Screen> {

    S create(Screen parent);
}
