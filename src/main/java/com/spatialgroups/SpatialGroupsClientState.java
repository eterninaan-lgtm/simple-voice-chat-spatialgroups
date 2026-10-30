package com.spatialgroups;

import de.maxhenkel.voicechat.api.VoicechatClientApi;

/**
 * Small holder for the voice chat distance of the current client connection.
 *
 * <p>Simple Voice Chat sends the server's {@code max_voice_distance} to the client when the voice
 * connection is established. Caching it here means the audio thread and the config screen do not
 * have to call into the Simple Voice Chat client API for every audio buffer; it is updated once on
 * connect/disconnect.</p>
 */
public final class SpatialGroupsClientState {

    private static volatile double voiceChatDistance = -1.0D;

    private SpatialGroupsClientState() {
    }

    /**
     * Called by the Simple Voice Chat client connection event.
     */
    public static void updateConnection(boolean connected, VoicechatClientApi clientApi) {
        double distance = -1.0D;
        if (connected && clientApi != null) {
            try {
                double value = clientApi.getVoiceChatDistance();
                if (value > 0.0D) {
                    distance = value;
                }
            } catch (Throwable ignored) {
                // The voice connection is closing; fall back to "unknown" below.
            }
        }
        voiceChatDistance = distance;
    }

    /**
     * @return the server voice chat distance in blocks, or {@code -1} if it is not known yet
     */
    public static double getVoiceChatDistance() {
        return voiceChatDistance;
    }
}
