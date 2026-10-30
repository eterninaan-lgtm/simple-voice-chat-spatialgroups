package com.spatialgroups.client;

import com.spatialgroups.SpatialGroupsConfig;
import com.spatialgroups.SpatialGroupsPaths;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client only wiring. Kept in a separate class so the common mod entry point never touches client
 * only classes on a dedicated server.
 */
public final class SpatialGroupsClientSetup {

    private SpatialGroupsClientSetup() {
    }

    public static void register(ModContainer modContainer) {
        // Client audio settings: config/spatialgroups/client.toml
        modContainer.registerConfig(ModConfig.Type.CLIENT, SpatialGroupsConfig.SPEC,
                SpatialGroupsPaths.CLIENT_CONFIG);
        // Flat, tabbed in-game settings screen.
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) -> new SpatialGroupsConfigScreen(parent));
    }
}
