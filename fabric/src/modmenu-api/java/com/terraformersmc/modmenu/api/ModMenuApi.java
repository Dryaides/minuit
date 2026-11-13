package com.terraformersmc.modmenu.api;

/** Compile stand-in for Mod Menu's interface; left out of the jar (fabric/build.gradle). */
public interface ModMenuApi {

    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return null;
    }
}
