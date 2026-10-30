package com.spatialgroups.server;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.JoinGroupEvent;
import de.maxhenkel.voicechat.api.events.LeaveGroupEvent;
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent;
import de.maxhenkel.voicechat.api.events.RemoveGroupEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;

/**
 * Simple Voice Chat plugin that implements persistent voice groups.
 *
 * <p>This part only does something when the mod is installed on the server (dedicated server or
 * the integrated server of a single player world). The client side audio feature is implemented by
 * {@link com.spatialgroups.SpatialGroupsPlugin}.</p>
 */
@ForgeVoicechatPlugin
public class SpatialGroupsServerPlugin implements VoicechatPlugin {

    public static final String PLUGIN_ID = "spatialgroups_server";

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class,
                event -> PersistentGroupManager.onServerStarted(event.getVoicechat()));

        registration.registerEvent(VoicechatServerStoppedEvent.class,
                event -> PersistentGroupManager.onServerStopped());

        registration.registerEvent(PlayerConnectedEvent.class,
                event -> PersistentGroupManager.onPlayerConnected(event.getVoicechat(), event.getConnection()));

        // Low priority: only react to groups that other plugins did not cancel.
        registration.registerEvent(CreateGroupEvent.class,
                event -> PersistentGroupManager.onGroupCreated(event.getVoicechat(), event.getGroup(), event.getConnection()),
                Integer.MIN_VALUE);
        registration.registerEvent(JoinGroupEvent.class,
                event -> PersistentGroupManager.onGroupJoined(event.getVoicechat(), event.getGroup(), event.getConnection()),
                Integer.MIN_VALUE);

        registration.registerEvent(LeaveGroupEvent.class,
                event -> {
                    if (event.isCancelled()) {
                        return;
                    }
                    PersistentGroupManager.onGroupLeft(event.getVoicechat(), event.getGroup(),
                            event.getConnection());
                }, Integer.MIN_VALUE);
        registration.registerEvent(RemoveGroupEvent.class,
                event -> {
                    if (event.isCancelled()) {
                        return;
                    }
                    PersistentGroupManager.onGroupRemoved(event.getGroup());
                }, Integer.MIN_VALUE);
    }
}
