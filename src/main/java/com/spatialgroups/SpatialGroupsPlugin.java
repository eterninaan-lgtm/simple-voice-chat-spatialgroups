package com.spatialgroups;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.CreateOpenALContextEvent;
import de.maxhenkel.voicechat.api.events.DestroyOpenALContextEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.OpenALSoundEvent;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Simple Voice Chat plugin entry point.
 *
 * <p>Simple Voice Chat discovers this class through the {@link ForgeVoicechatPlugin} annotation.
 * The plugin only registers client events; all processing happens in {@link SpatialAudioEngine}.
 * Because the mod itself is client only, this class must not touch any client-only class on a
 * dedicated server. The {@link FMLEnvironment#dist} check below makes sure that the plugin simply
 * does nothing if the jar ever ends up in a dedicated server's mods folder.</p>
 */
@ForgeVoicechatPlugin
public class SpatialGroupsPlugin implements VoicechatPlugin {

    public static final String PLUGIN_ID = "spatialgroups";

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        registration.registerEvent(OpenALSoundEvent.Post.class, SpatialAudioEngine::onOpenALPost);
        registration.registerEvent(CreateOpenALContextEvent.class, event -> SpatialAudioEngine.reset());
        registration.registerEvent(DestroyOpenALContextEvent.class, event -> SpatialAudioEngine.reset());
        // Cache the server's voice distance once per connection instead of querying it for every
        // audio buffer (and so the config screen can show the effective value).
        registration.registerEvent(ClientVoicechatConnectionEvent.class,
                event -> SpatialGroupsClientState.updateConnection(event.isConnected(), event.getVoicechat()));
    }
}
