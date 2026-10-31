package com.spatialgroups;

import com.spatialgroups.client.SpatialGroupsClientSetup;
import com.spatialgroups.server.PersistentGroupManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple Voice Chat SpatialGroups - a Simple Voice Chat addon that keeps 3D audio for nearby
 * party members.
 *
 * <p>The audio part is client side only: every listener that wants distance attenuation and
 * stereo direction for nearby group members has to install the mod. The optional persistent group
 * part is server side: when the mod is installed on the server (or in single player), groups are
 * kept when players disconnect and members are put back into their group when they reconnect. When
 * the last member leaves a group on purpose, the group is dissolved.</p>
 */
@Mod(value = SpatialGroupsMod.MOD_ID)
public final class SpatialGroupsMod {

    public static final String MOD_ID = "spatialgroups";
    public static final Logger LOGGER = LoggerFactory.getLogger("SpatialGroups");

    public SpatialGroupsMod(IEventBus modEventBus, ModContainer modContainer) {
        // Move files written by older versions into config/spatialgroups/ before NeoForge loads
        // the configs.
        SpatialGroupsPaths.migrateLegacyFiles();

        // All settings: config/spatialgroups/common.toml. The 3D audio options are read on the
        // client, the persistent group option on the server.
        modContainer.registerConfig(ModConfig.Type.COMMON, SpatialGroupsCommonConfig.SPEC,
                SpatialGroupsPaths.COMMON_CONFIG);

        if (FMLEnvironment.dist.isClient()) {
            SpatialGroupsClientSetup.register(modContainer);
        }

        // Groups are removed at the end of the tick in which their last member left. Doing it on
        // the next tick is required because Simple Voice Chat still has the player in the group
        // while the leave event is being handled.
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
                event -> PersistentGroupManager.onServerTick());

        LOGGER.info("SpatialGroups initialized: 3D audio for nearby group members and persistent voice groups");
    }
}
