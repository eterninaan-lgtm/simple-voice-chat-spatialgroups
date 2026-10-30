package com.spatialgroups;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single place that knows where the mod stores its files.
 *
 * <p>All generated files live in one folder, {@code config/spatialgroups/}:</p>
 * <ul>
 *     <li>{@code client.toml} - client side 3D audio settings</li>
 *     <li>{@code common.toml} - server side persistent group setting</li>
 *     <li>{@code persistent-groups.properties} - saved groups and members</li>
 * </ul>
 *
 * <p>Versions before 2.1.0 wrote these files directly into {@code config/}. They are moved into the
 * new folder automatically the first time the mod starts, so existing settings and groups are
 * kept.</p>
 */
public final class SpatialGroupsPaths {

    public static final String FOLDER = "spatialgroups";

    /** File name as passed to NeoForge; a relative path keeps the config inside the folder. */
    public static final String CLIENT_CONFIG = FOLDER + "/client.toml";
    public static final String COMMON_CONFIG = FOLDER + "/common.toml";

    private static final String LEGACY_CLIENT_CONFIG = "spatialgroups-client.toml";
    private static final String LEGACY_COMMON_CONFIG = "spatialgroups-common.toml";
    private static final String LEGACY_PERSISTENT_GROUPS = "spatialgroups-persistent-groups.properties";

    private static final AtomicBoolean MIGRATED = new AtomicBoolean();

    private SpatialGroupsPaths() {
    }

    public static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve(FOLDER);
    }

    public static Path persistentGroups() {
        return folder().resolve("persistent-groups.properties");
    }

    /**
     * Moves files from the old {@code config/} layout into {@code config/spatialgroups/}.
     * Safe to call more than once; only the first call does any work.
     */
    public static void migrateLegacyFiles() {
        if (!MIGRATED.compareAndSet(false, true)) {
            return;
        }
        Path folder = folder();
        try {
            Files.createDirectories(folder);
        } catch (Throwable t) {
            SpatialGroupsMod.LOGGER.warn("Could not create the config folder {}", folder, t);
            return;
        }

        migrate(LEGACY_CLIENT_CONFIG, "client.toml");
        migrate(LEGACY_COMMON_CONFIG, "common.toml");
        migrate(LEGACY_PERSISTENT_GROUPS, "persistent-groups.properties");
    }

    private static void migrate(String legacyName, String newName) {
        Path legacy = FMLPaths.CONFIGDIR.get().resolve(legacyName);
        if (!Files.isRegularFile(legacy)) {
            return;
        }

        Path target = folder().resolve(newName);
        try {
            if (!Files.exists(target)) {
                Files.move(legacy, target);
                SpatialGroupsMod.LOGGER.info("Moved legacy config {} to {}", legacy, target);
                return;
            }

            // Both layouts exist (for example after downgrading and upgrading again). Keep the
            // file from the new folder and preserve the old one as a backup inside that folder.
            Path backup = folder().resolve(legacyName + ".bak");
            int suffix = 1;
            while (Files.exists(backup)) {
                backup = folder().resolve(legacyName + ".bak." + suffix++);
            }
            Files.move(legacy, backup);
            SpatialGroupsMod.LOGGER.warn("Both {} and {} exist; kept the new file and moved the old one to {}",
                    legacy, target, backup);
        } catch (Throwable t) {
            SpatialGroupsMod.LOGGER.warn("Could not move legacy config {} to {}", legacy, target, t);
        }
    }
}
