package com.spatialgroups.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client only wiring. Kept in a separate class so the common mod entry point never touches client
 * only classes on a dedicated server.
 */
public final class SpatialGroupsClientSetup {

    private SpatialGroupsClientSetup() {
    }

    public static void register(ModContainer modContainer) {
        // Flat, tabbed in-game settings screen. The settings themselves are part of the common
        // config, registered by the mod entry point.
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) -> new SpatialGroupsConfigScreen(parent));
    }
}
