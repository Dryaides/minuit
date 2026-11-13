package dev.dryaides.minuit.fabric;

import dev.dryaides.minuit.internal.Bundling;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.Optional;

/** Fabric {@code include}: the loader names the mod whose jar holds minuit's. Same API on every version. */
final class FabricBundling {

    private FabricBundling() {
    }

    static void check() {
        Optional<ModContainer> self = FabricLoader.getInstance().getModContainer("minuit");
        Optional<ModContainer> parent = self.isPresent() ? self.get().getContainingMod() : Optional.empty();
        if (parent.isPresent()) {
            Bundling.found(parent.get().getMetadata().getName() + " (" + parent.get().getMetadata().getId() + ")");
        }
    }
}
