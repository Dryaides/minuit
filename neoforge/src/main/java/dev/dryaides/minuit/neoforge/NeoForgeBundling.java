package dev.dryaides.minuit.neoforge;

import dev.dryaides.minuit.internal.Bundling;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;

/** NeoForge jarJar: FML records the jar a nested mod file came out of. Same API from FML 4 (1.21.1) to 12 (26.x). */
final class NeoForgeBundling {

    private NeoForgeBundling() {
    }

    static void check() {
        IModFileInfo self = ModList.get().getModFileById("minuit");
        IModFile parent = self == null ? null : self.getFile().getDiscoveryAttributes().parent();
        if (parent == null) {
            return;
        }
        IModInfo mod = parent.getModInfos().isEmpty() ? null : parent.getModInfos().get(0);
        Bundling.found(mod == null ? String.valueOf(parent.getFilePath().getFileName()) : mod.getDisplayName() + " (" + mod.getModId() + ")");
    }
}
