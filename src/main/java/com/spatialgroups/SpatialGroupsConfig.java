package com.spatialgroups;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client side audio settings.
 *
 * <p>The config is a normal NeoForge {@link ModConfigSpec} client config, stored in
 * {@code config/spatialgroups/client.toml}, and can also be edited in game through the mod's own
 * flat settings screen.</p>
 */
public final class SpatialGroupsConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue SPATIAL_DISTANCE;
    public static final ModConfigSpec.DoubleValue FADE_DISTANCE;
    public static final ModConfigSpec.DoubleValue MINIMUM_GAIN;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment(
                "Simple Voice Chat SpatialGroups - 设置",
                "打开后：群组里靠近你的队友会有左右和远近感；走远后自动变回普通群组语音。",
                "所有选项都可以随时修改，保存后立刻生效。"
        ).translation("spatialgroups.configuration.general").push("general");

        ENABLED = builder
                .comment(
                        "是否开启 3D 语音。",
                        "开启：群组里靠近你的队友有距离和左右方位。",
                        "关闭：和原版 Simple Voice Chat 完全一样。"
                )
                .translation("spatialgroups.configuration.enabled")
                .define("enabled", true);

        SPATIAL_DISTANCE = builder
                .comment(
                        "3D 声音能听清多远（格）。",
                        "0 = 自动使用服务器的语音距离。"
                )
                .translation("spatialgroups.configuration.spatial_distance")
                .defineInRange("spatial_distance", 0.0D, 0.0D, 512.0D);

        FADE_DISTANCE = builder
                .comment(
                        "从 3D 过渡到普通群组语音的渐变距离（格）。",
                        "数值越大，过渡越平缓，不会突然跳变。"
                )
                .translation("spatialgroups.configuration.fade_distance")
                .defineInRange("fade_distance", 12.0D, 1.0D, 256.0D);

        MINIMUM_GAIN = builder
                .comment(
                        "3D 范围边缘的最小音量（0~1）。",
                        "0.25 表示最远边缘还有 25% 音量，避免声音突然消失。"
                )
                .translation("spatialgroups.configuration.minimum_gain")
                .defineInRange("minimum_gain", 0.25D, 0.0D, 1.0D);

        builder.pop();
        SPEC = builder.build();
    }

    private SpatialGroupsConfig() {
    }
}
