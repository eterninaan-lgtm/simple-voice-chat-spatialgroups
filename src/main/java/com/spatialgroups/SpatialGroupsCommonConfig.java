package com.spatialgroups;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * All settings of Simple Voice Chat SpatialGroups.
 *
 * <p>The options live in a single NeoForge {@link ModConfigSpec} common config stored in
 * {@code config/spatialgroups/common.toml}. They can also be edited in game through the mod's own
 * flat settings screen.</p>
 *
 * <p>The 3D audio options are only read by the client that plays the audio. The persistent group
 * option is only used by the server (the integrated server in single player).</p>
 */
public final class SpatialGroupsCommonConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue PERSISTENT_GROUPS;
    public static final ModConfigSpec.DoubleValue SPATIAL_DISTANCE;
    public static final ModConfigSpec.DoubleValue FADE_DISTANCE;
    public static final ModConfigSpec.DoubleValue MINIMUM_GAIN;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment(
                "Simple Voice Chat SpatialGroups settings.",
                "All options are stored in this file and can also be edited in game from the mod's settings screen.",
                "",
                "The 3D audio options only affect the client that plays the audio.",
                "The persistent group option is used by the server,",
                "or by the integrated server in single player."
        ).translation("spatialgroups.configuration.general").push("general");

        ENABLED = builder
                .comment(
                        "Enable 3D audio for group voice chat.",
                        "On: nearby teammates in a voice group are heard with direction and distance.",
                        "Off: group voice chat behaves exactly like vanilla Simple Voice Chat."
                )
                .translation("spatialgroups.configuration.enabled")
                .define("enabled", true);

        PERSISTENT_GROUPS = builder
                .comment(
                        "Keep voice groups after players disconnect or leave the server.",
                        "On: groups stay after players leave, and you rejoin your old group next time you connect.",
                        "    A group you left on purpose is not rejoined automatically.",
                        "    Once its last member leaves on purpose, the group is dissolved.",
                        "Off: vanilla Simple Voice Chat behavior; empty groups are removed.",
                        "Works immediately in single player.",
                        "For multiplayer servers, the mod must also be installed on the server."
                )
                .translation("spatialgroups.configuration.persistent_groups")
                .define("persistent_groups", true);

        builder.pop();

        builder.comment(
                "Distance settings for the 3D audio.",
                "These options are client-side and only affect the client that plays the audio."
        ).translation("spatialgroups.configuration.distance").push("distance");

        SPATIAL_DISTANCE = builder
                .comment(
                        "The maximum distance in blocks at which group voice keeps positional audio.",
                        "Set to 0 to automatically use the server's configured voice distance."
                )
                .translation("spatialgroups.configuration.spatial_distance")
                .defineInRange("spatial_distance", 0.0D, 0.0D, 512.0D);

        FADE_DISTANCE = builder
                .comment(
                        "The distance range in blocks over which positional audio gradually transitions back",
                        "to regular group voice chat. Higher values result in a smoother, more gradual transition."
                )
                .translation("spatialgroups.configuration.fade_distance")
                .defineInRange("fade_distance", 12.0D, 1.0D, 256.0D);

        MINIMUM_GAIN = builder
                .comment(
                        "The minimum volume at the outer edge of the 3D audio range.",
                        "For example, 0.25 means voices still play at 25% volume at the edge,",
                        "preventing them from suddenly disappearing at the boundary."
                )
                .translation("spatialgroups.configuration.minimum_gain")
                .defineInRange("minimum_gain", 0.25D, 0.0D, 1.0D);

        builder.pop();
        SPEC = builder.build();
    }

    private SpatialGroupsCommonConfig() {
    }
}
