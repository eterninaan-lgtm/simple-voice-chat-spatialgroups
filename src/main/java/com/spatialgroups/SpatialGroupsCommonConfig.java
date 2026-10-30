package com.spatialgroups;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Settings that are relevant on the server side (or in single player, where the integrated server
 * is the server).
 *
 * <p>The file is stored as {@code config/spatialgroups/common.toml}. Client side audio settings
 * live in {@link SpatialGroupsConfig} instead.</p>
 */
public final class SpatialGroupsCommonConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue PERSISTENT_GROUPS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment(
                "Simple Voice Chat SpatialGroups - 通用设置",
                "这里的选项都很简单，按需要打开或关闭即可。"
        ).translation("spatialgroups.configuration.common_general").push("general");

        PERSISTENT_GROUPS = builder
                .comment(
                        "是否保留语音小组。",
                        "开启：玩家掉线或离开服务器后，小组仍然保留，下次进服自动回到该小组。",
                        "最后一名成员主动退出小组后，小组会自动解散。",
                        "关闭：恢复 Simple Voice Chat 默认行为，空小组会被删除。"
                )
                .translation("spatialgroups.configuration.persistent_groups")
                .define("persistent_groups", true);

        builder.pop();
        SPEC = builder.build();
    }

    private SpatialGroupsCommonConfig() {
    }
}
