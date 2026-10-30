package com.spatialgroups.server;

import com.spatialgroups.SpatialGroupsCommonConfig;
import com.spatialgroups.SpatialGroupsMod;
import com.spatialgroups.SpatialGroupsPaths;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps Simple Voice Chat groups and their members across sessions.
 *
 * <p>When a player creates or joins a group the group is marked as persistent, its definition is
 * written to {@code config/spatialgroups/persistent-groups.properties} and the player is
 * remembered as a member. On server start the saved groups are recreated, and when a remembered
 * player reconnects to the server they are put back into their previous group automatically.</p>
 *
 * <p>Explicitly leaving a group removes the player from the remembered members, so the player is
 * not pulled back into that group later. When the last remembered member leaves the group
 * explicitly, the group is dissolved instead of being kept for a later reconnect.</p>
 */
public final class PersistentGroupManager {

    private static final int MAX_DISSOLVE_ATTEMPTS = 40;

    private static final Map<UUID, StoredGroup> GROUPS = new HashMap<>();
    private static final Map<UUID, UUID> MEMBERS = new HashMap<>();

    /**
     * Groups whose last member left during the current tick. Simple Voice Chat still reports the
     * leaving player as a group member while the leave event is dispatched, so the actual removal
     * has to wait until the end of the tick.
     */
    private static final Map<UUID, PendingDissolve> PENDING_DISSOLVES = new ConcurrentHashMap<>();

    private static final AtomicBoolean LOADED = new AtomicBoolean();
    private static final AtomicBoolean PERSISTENCE_WARNING_LOGGED = new AtomicBoolean();

    private PersistentGroupManager() {
    }

    public static void onServerStarted(VoicechatServerApi api) {
        PENDING_DISSOLVES.clear();
        if (!isEnabled() || api == null || !groupsEnabled(api)) {
            return;
        }
        ensureLoaded();

        // Stored groups without a single remembered member cannot get a last member leaving them
        // anymore. They are leftovers from older versions and are cleaned up here.
        purgeOrphanGroups(api);

        List<StoredGroup> groups;
        synchronized (GROUPS) {
            groups = new ArrayList<>(GROUPS.values());
        }

        for (StoredGroup stored : groups) {
            if (findGroup(api, stored.id()) != null) {
                continue;
            }
            try {
                recreate(api, stored);
                logger().info("Restored persistent voice group '{}'", stored.name());
            } catch (Throwable t) {
                logger().warn("Could not restore persistent voice group '{}'", stored.name(), t);
            }
        }
    }

    public static void onGroupCreated(VoicechatServerApi api, Group group, VoicechatConnection connection) {
        rememberGroup(api, group, connection, "created");
    }

    public static void onGroupJoined(VoicechatServerApi api, Group group, VoicechatConnection connection) {
        rememberGroup(api, group, connection, "joined");
    }

    public static void onGroupLeft(VoicechatServerApi api, Group group, VoicechatConnection connection) {
        if (!isEnabled() || connection == null) {
            return;
        }
        ensureLoaded();
        UUID player;
        try {
            player = connection.getPlayer().getUuid();
        } catch (Throwable t) {
            return;
        }
        if (player == null) {
            return;
        }

        UUID leftGroup = null;
        try {
            if (group != null) {
                leftGroup = group.getId();
            }
        } catch (Throwable ignored) {
        }

        UUID rememberedGroup;
        synchronized (GROUPS) {
            rememberedGroup = MEMBERS.remove(player);
            if (rememberedGroup != null) {
                saveLocked();
            }
        }

        // Usually the group from the event and the remembered group are the same. Check both in
        // case another plugin changed the membership before this leave event was dispatched.
        if (api == null) {
            return;
        }
        if (leftGroup != null) {
            PENDING_DISSOLVES.put(leftGroup, new PendingDissolve(api));
        }
        if (rememberedGroup != null && !rememberedGroup.equals(leftGroup)) {
            PENDING_DISSOLVES.put(rememberedGroup, new PendingDissolve(api));
        }
    }

    /**
     * Called at the end of every server tick (see {@code SpatialGroupsMod}). At this point Simple
     * Voice Chat has already cleared the leaving player's group, so removing a group that is now
     * empty succeeds.
     */
    public static void onServerTick() {
        if (PENDING_DISSOLVES.isEmpty()) {
            return;
        }
        if (!isEnabled()) {
            PENDING_DISSOLVES.clear();
            return;
        }

        for (Iterator<Map.Entry<UUID, PendingDissolve>> iterator = PENDING_DISSOLVES.entrySet().iterator();
             iterator.hasNext(); ) {
            Map.Entry<UUID, PendingDissolve> entry = iterator.next();
            UUID groupId = entry.getKey();
            PendingDissolve pending = entry.getValue();

            StoredGroup stored;
            synchronized (GROUPS) {
                if (MEMBERS.containsValue(groupId)) {
                    // Somebody rejoined in the meantime; the group must stay.
                    iterator.remove();
                    continue;
                }
                stored = GROUPS.get(groupId);
            }
            if (stored == null) {
                iterator.remove();
                continue;
            }

            try {
                if (findGroup(pending.api(), groupId) == null) {
                    // The group is already gone (for example removed by an admin command).
                    forget(groupId);
                    iterator.remove();
                    continue;
                }

                if (pending.api().removeGroup(groupId)) {
                    forget(groupId);
                    iterator.remove();
                    logger().info("Dissolved voice group '{}' because all of its members left", stored.name());
                } else if (++pending.attempts >= MAX_DISSOLVE_ATTEMPTS) {
                    // Another plugin may be holding the group open. Keep it remembered instead of
                    // silently dropping the saved group.
                    iterator.remove();
                    logger().warn("Could not dissolve empty voice group '{}'; it is still in use", stored.name());
                }
            } catch (Throwable t) {
                iterator.remove();
                logger().warn("Could not dissolve empty voice group '{}'", stored.name(), t);
            }
        }
    }

    /**
     * Drops any scheduled cleanup. Called when the voice chat server stops so nothing from the
     * previous world is processed later.
     */
    public static void onServerStopped() {
        PENDING_DISSOLVES.clear();
    }

    public static void onGroupRemoved(Group group) {
        if (!isEnabled() || group == null) {
            return;
        }
        ensureLoaded();
        UUID groupId;
        try {
            groupId = group.getId();
        } catch (Throwable t) {
            return;
        }
        if (groupId == null) {
            return;
        }

        boolean persistent;
        try {
            persistent = group.isPersistent();
        } catch (Throwable t) {
            persistent = true;
        }

        synchronized (GROUPS) {
            if (persistent) {
                // A persistent group was removed explicitly (for example by an admin command or
                // another plugin). Forget it completely.
                GROUPS.remove(groupId);
                MEMBERS.values().removeIf(groupId::equals);
                saveLocked();
            }
            // A non persistent group being removed is the normal cleanup of an empty group. Keep
            // the stored definition so the group can be restored when a member comes back.
        }
    }

    /**
     * Removes groups that are stored but have no remembered member. Such groups can only come from
     * versions that kept a group after every member had left, or from a manually edited file; under
     * the current rules they should no longer exist.
     */
    private static void purgeOrphanGroups(VoicechatServerApi api) {
        List<StoredGroup> orphans = new ArrayList<>();
        synchronized (GROUPS) {
            for (StoredGroup stored : GROUPS.values()) {
                if (!MEMBERS.containsValue(stored.id())) {
                    orphans.add(stored);
                }
            }
        }

        for (StoredGroup orphan : orphans) {
            try {
                Group running = findGroup(api, orphan.id());
                if (running == null || api.removeGroup(orphan.id())) {
                    forget(orphan.id());
                    logger().info("Removed empty persistent voice group '{}'", orphan.name());
                }
            } catch (Throwable t) {
                logger().warn("Could not remove empty persistent voice group '{}'", orphan.name(), t);
            }
        }
    }

    /**
     * Removes a group and all of its member records from the saved data.
     */
    private static void forget(UUID groupId) {
        synchronized (GROUPS) {
            boolean changed = GROUPS.remove(groupId) != null;
            if (MEMBERS.values().removeIf(groupId::equals)) {
                changed = true;
            }
            if (changed) {
                saveLocked();
            }
        }
    }

    public static void onPlayerConnected(VoicechatServerApi api, VoicechatConnection connection) {
        if (!isEnabled() || connection == null) {
            return;
        }
        ensureLoaded();

        UUID player;
        try {
            player = connection.getPlayer().getUuid();
        } catch (Throwable t) {
            return;
        }
        if (player == null) {
            return;
        }

        UUID groupId;
        synchronized (GROUPS) {
            groupId = MEMBERS.get(player);
        }
        if (groupId == null) {
            return;
        }

        UUID targetGroup = groupId;
        runOnServerThread(connection, () -> tryRejoin(api, player, targetGroup));
    }

    private static void tryRejoin(VoicechatServerApi api, UUID player, UUID groupId) {
        try {
            if (!groupsEnabled(api)) {
                return;
            }
            VoicechatConnection connection = api.getConnectionOf(player);
            if (connection == null) {
                return;
            }
            if (connection.getGroup() != null) {
                // The player is already in a group (for example because another plugin put them
                // there). Never override that.
                return;
            }

            Group group = findGroup(api, groupId);
            if (group == null) {
                StoredGroup stored;
                synchronized (GROUPS) {
                    stored = GROUPS.get(groupId);
                }
                if (stored == null) {
                    return;
                }
                try {
                    group = recreate(api, stored);
                } catch (Throwable t) {
                    logger().warn("Could not recreate voice group '{}'", stored.name(), t);
                    return;
                }
            }

            connection.setGroup(group);
            logger().info("Automatically rejoined {} to voice group '{}'", player, group.getName());
        } catch (Throwable t) {
            logger().warn("Could not automatically rejoin {} to its voice group", player, t);
        }
    }

    private static void rememberGroup(VoicechatServerApi api, Group group, VoicechatConnection connection, String action) {
        if (!isEnabled() || group == null) {
            return;
        }
        ensureLoaded();

        UUID groupId;
        String name;
        boolean hidden;
        Group.Type type;
        try {
            groupId = group.getId();
            name = group.getName();
            hidden = group.isHidden();
            type = group.getType();
        } catch (Throwable t) {
            return;
        }
        if (groupId == null || name == null || type == null) {
            return;
        }

        String password = null;
        if (group.hasPassword()) {
            password = readPassword(group);
            if (password == null) {
                if (PERSISTENCE_WARNING_LOGGED.compareAndSet(false, true)) {
                    logger().warn("Could not read the password of a protected voice group; "
                            + "password protected groups will not be remembered across restarts");
                }
                return;
            }
        }

        markPersistent(group);

        UUID player = null;
        if (connection != null) {
            try {
                player = connection.getPlayer().getUuid();
            } catch (Throwable ignored) {
            }
        }

        StoredGroup stored = new StoredGroup(groupId, name, password, hidden, type);
        UUID previousGroup = null;
        synchronized (GROUPS) {
            GROUPS.put(groupId, stored);
            if (player != null) {
                previousGroup = MEMBERS.put(player, groupId);
            }
            saveLocked();
        }
        if (previousGroup != null && !previousGroup.equals(groupId)) {
            // The player switched groups. If this was the last member of the old group, the
            // normal dissolve check will remove it at the end of the tick.
            PENDING_DISSOLVES.put(previousGroup, new PendingDissolve(api));
        }
        logger().debug("Remembered voice group '{}' ({})", name, action);
    }

    private static Group recreate(VoicechatServerApi api, StoredGroup stored) {
        Group group = api.groupBuilder()
                .setId(stored.id())
                .setName(stored.name())
                .setPassword(stored.password())
                .setPersistent(true)
                .setHidden(stored.hidden())
                .setType(stored.type())
                .build();
        preserveLongName(group, stored.name());
        return group;
    }

    /**
     * The Simple Voice Chat API builder truncates group names to 16 characters while the game
     * itself allows 24. Restore the original name in the internal group object (and refresh the
     * group list for online players) so saved groups keep their full name.
     */
    private static void preserveLongName(Group group, String name) {
        if (group == null || name == null || name.length() <= 16) {
            return;
        }
        try {
            Object internal = group.getClass().getMethod("getGroup").invoke(group);
            if (internal == null) {
                return;
            }
            Field nameField = internal.getClass().getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(internal, name);

            Object voicechat = Class.forName("de.maxhenkel.voicechat.Voicechat")
                    .getField("SERVER").get(null);
            Object server = voicechat.getClass().getMethod("getServer").invoke(voicechat);
            if (server == null) {
                return;
            }
            Object groupManager = server.getClass().getMethod("getGroupManager").invoke(server);
            Method broadcast = groupManager.getClass().getDeclaredMethod("broadcastAddGroup", internal.getClass());
            broadcast.setAccessible(true);
            broadcast.invoke(groupManager, internal);
        } catch (Throwable t) {
            logger().warn("Could not restore the full name of voice group '{}'", name, t);
        }
    }

    private static Group findGroup(VoicechatServerApi api, UUID groupId) {
        try {
            for (Group group : api.getGroups()) {
                if (groupId.equals(group.getId())) {
                    return group;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Marks the internal SVC group object as persistent. The API has no setter for this, so the
     * private field is used; if that ever fails the group is still remembered and gets recreated
     * when a member reconnects or the server restarts.
     */
    private static void markPersistent(Group group) {
        try {
            if (group.isPersistent()) {
                return;
            }
            Object internal = group.getClass().getMethod("getGroup").invoke(group);
            if (internal == null) {
                return;
            }
            Field field = internal.getClass().getDeclaredField("persistent");
            field.setAccessible(true);
            field.setBoolean(internal, true);
        } catch (Throwable t) {
            if (PERSISTENCE_WARNING_LOGGED.compareAndSet(false, true)) {
                logger().warn("Could not mark voice groups as persistent; groups will be restored when members reconnect", t);
            }
        }
    }

    private static String readPassword(Group group) {
        try {
            Object internal = group.getClass().getMethod("getGroup").invoke(group);
            if (internal == null) {
                return null;
            }
            Method getPassword = internal.getClass().getMethod("getPassword");
            return (String) getPassword.invoke(internal);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void runOnServerThread(VoicechatConnection connection, Runnable task) {
        try {
            ServerPlayer player = connection.getPlayer();
            Object rawLevel = player.getServerLevel().getServerLevel();
            if (rawLevel instanceof ServerLevel level) {
                MinecraftServer server = level.getServer();
                if (server != null) {
                    server.execute(task);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
        task.run();
    }

    private static boolean isEnabled() {
        try {
            return SpatialGroupsCommonConfig.PERSISTENT_GROUPS.get();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean groupsEnabled(VoicechatServerApi api) {
        try {
            return api.getServerConfig().getBoolean("enable_groups", true);
        } catch (Throwable t) {
            return true;
        }
    }

    private static void ensureLoaded() {
        if (LOADED.compareAndSet(false, true)) {
            SpatialGroupsPaths.migrateLegacyFiles();
            load();
        }
    }

    private static Path file() {
        return SpatialGroupsPaths.persistentGroups();
    }

    private static void load() {
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (Throwable t) {
            logger().warn("Could not read {}", file, t);
            return;
        }

        Map<UUID, StoredGroup> loadedGroups = new HashMap<>();
        Set<UUID> ids = new HashSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("group.") && key.endsWith(".name")) {
                try {
                    ids.add(UUID.fromString(key.substring("group.".length(), key.length() - ".name".length())));
                } catch (Throwable ignored) {
                }
            }
        }
        for (UUID id : ids) {
            String prefix = "group." + id;
            String name = properties.getProperty(prefix + ".name");
            if (name == null) {
                continue;
            }
            String password = properties.getProperty(prefix + ".password");
            boolean hidden = Boolean.parseBoolean(properties.getProperty(prefix + ".hidden", "false"));
            Group.Type type = parseType(properties.getProperty(prefix + ".type"));
            loadedGroups.put(id, new StoredGroup(id, name, password, hidden, type));
        }

        synchronized (GROUPS) {
            GROUPS.clear();
            GROUPS.putAll(loadedGroups);
            MEMBERS.clear();
            for (String key : properties.stringPropertyNames()) {
                if (!key.startsWith("member.")) {
                    continue;
                }
                try {
                    UUID player = UUID.fromString(key.substring("member.".length()));
                    UUID group = UUID.fromString(properties.getProperty(key));
                    if (loadedGroups.containsKey(group)) {
                        MEMBERS.put(player, group);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        logger().info("Loaded {} persistent voice group(s) and {} member(s)", loadedGroups.size(), MEMBERS.size());
    }

    private static void saveLocked() {
        Properties properties = new Properties();
        for (StoredGroup stored : GROUPS.values()) {
            String prefix = "group." + stored.id();
            properties.setProperty(prefix + ".name", stored.name());
            if (stored.password() != null) {
                properties.setProperty(prefix + ".password", stored.password());
            }
            properties.setProperty(prefix + ".hidden", Boolean.toString(stored.hidden()));
            properties.setProperty(prefix + ".type", typeName(stored.type()));
        }
        for (Map.Entry<UUID, UUID> entry : MEMBERS.entrySet()) {
            properties.setProperty("member." + entry.getKey(), entry.getValue().toString());
        }

        Path file = file();
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "Simple Voice Chat SpatialGroups persistent groups");
            }
        } catch (Throwable t) {
            logger().warn("Could not save {}", file, t);
        }
    }

    private static String typeName(Group.Type type) {
        if (type == Group.Type.OPEN) {
            return "OPEN";
        }
        if (type == Group.Type.ISOLATED) {
            return "ISOLATED";
        }
        return "NORMAL";
    }

    private static Group.Type parseType(String name) {
        if ("OPEN".equalsIgnoreCase(name)) {
            return Group.Type.OPEN;
        }
        if ("ISOLATED".equalsIgnoreCase(name)) {
            return Group.Type.ISOLATED;
        }
        return Group.Type.NORMAL;
    }

    private static Logger logger() {
        return SpatialGroupsMod.LOGGER;
    }

    private record StoredGroup(UUID id, String name, String password, boolean hidden, Group.Type type) {
    }

    private static final class PendingDissolve {
        private final VoicechatServerApi api;
        private int attempts;

        private PendingDissolve(VoicechatServerApi api) {
            this.api = api;
        }

        private VoicechatServerApi api() {
            return api;
        }
    }
}
