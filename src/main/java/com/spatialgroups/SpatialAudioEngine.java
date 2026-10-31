package com.spatialgroups;

import de.maxhenkel.voicechat.api.events.OpenALSoundEvent;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.lwjgl.openal.AL10;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Renders Simple Voice Chat group audio positionally while the speaker is inside the voice range
 * and cross fades it into the normal non-directional group voice at the edge of that range.
 *
 * <p>The addon hooks the {@link OpenALSoundEvent.Post} event. Simple Voice Chat fires that event
 * for every audio buffer it hands to OpenAL, directly on the OpenAL thread and inside the OpenAL
 * context, right after it has set the listener, the source position and the source gain for that
 * buffer. That makes it possible to change the source in place without touching any of the audio
 * data or Simple Voice Chat's own classes:</p>
 *
 * <ul>
 *     <li>the source gain is multiplied with the cross fade gain (distance attenuation),</li>
 *     <li>the source is placed at a direction that is continuously rotated towards the listener's
 *     view direction (so the stereo direction smoothly disappears),</li>
 *     <li>OpenAL's own distance attenuation is disabled for this source because the distance
 *     attenuation is already part of the cross fade gain.</li>
 * </ul>
 *
 * <p>Only audio that Simple Voice Chat plays without a position is touched, and only if the audio
 * channel belongs to a player entity that is currently loaded. Positional proximity voice,
 * plugin/locational audio and all sounds that are not voice chat group audio are never modified.</p>
 */
public final class SpatialAudioEngine {

    /**
     * Very small distances are treated like "at the listener": the normal, centered group voice is
     * already correct and the direction would be meaningless/undefined.
     */
    private static final double MIN_DISTANCE = 0.5D;

    private static final double DEFAULT_VOICE_DISTANCE = 48.0D;

    /**
     * OpenAL source ids whose rolloff factor was changed by this addon. If such a source is later
     * used for positional audio again (for example in an open group), the default is restored.
     */
    private static final Set<Integer> SPATIALIZED_SOURCES = ConcurrentHashMap.newKeySet();

    private static final AtomicBoolean ACTIVE_NOTICE_LOGGED = new AtomicBoolean();

    private SpatialAudioEngine() {
    }

    /**
     * Called when the OpenAL context is created or destroyed. Source ids are only valid inside
     * one context, so the bookkeeping is thrown away.
     */
    public static void reset() {
        SPATIALIZED_SOURCES.clear();
    }

    public static void onOpenALPost(OpenALSoundEvent.Post event) {
        try {
            handle(event);
        } catch (Throwable t) {
            // Never let an addon break Simple Voice Chat's sound thread.
            SpatialGroupsMod.LOGGER.debug("SpatialGroups: failed to process an OpenAL sound", t);
        }
    }

    private static void handle(OpenALSoundEvent.Post event) {
        int source = event.getSource();
        if (source <= 0) {
            return;
        }

        // Positional audio (normal proximity voice) is not touched. If this source was used for
        // spatialized group audio before, restore the OpenAL default we changed. This runs even
        // while the feature is disabled so a source can never keep an unattenuated rolloff.
        if (event.getPosition() != null) {
            if (SPATIALIZED_SOURCES.remove(source)) {
                AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 1.0F);
            }
            return;
        }

        if (!SpatialGroupsCommonConfig.ENABLED.get()) {
            return;
        }

        // Everything below is audio Simple Voice Chat plays without a position. The channel id of
        // voice chat group audio is the UUID of the speaking player. Plugin audio has its own
        // channel ids and therefore never matches a player.
        UUID channelId = event.getChannelId();
        if (channelId == null) {
            return;
        }

        // The reduced and mono audio types do not use OpenAL 3D playback at all (they use stereo or
        // mono buffers and disable the OpenAL distance model). Respect that user choice and leave
        // their group voice alone instead of positioning a source that the listener was never set
        // up for.
        if (AL10.alGetInteger(AL10.AL_DISTANCE_MODEL) == AL10.AL_NONE) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer listener = minecraft.player;
        if (level == null || listener == null) {
            return;
        }
        if (channelId.equals(listener.getUUID())) {
            // Audio of the listener itself, e.g. when playing back the local camera entity.
            return;
        }
        if (listener.isSpectator() || !listener.equals(minecraft.cameraEntity)) {
            // Spectator mode and freecam: Simple Voice Chat intentionally plays this audio
            // non-positional, because the camera is detached from the player.
            return;
        }

        Player speaker = level.getPlayerByUUID(channelId);
        if (speaker == null || speaker == minecraft.cameraEntity || speaker.isSpectator()) {
            // Cross dimension group voice, plugin audio, a player that is not loaded, or the
            // special spectator possession audio which is meant to stay non-directional.
            return;
        }

        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 listenerPosition = camera.getPosition();
        Vec3 speakerPosition = speaker.getEyePosition();
        double distance = listenerPosition.distanceTo(speakerPosition);
        if (distance < MIN_DISTANCE) {
            return;
        }

        double spatialDistance = resolveSpatialDistance();
        if (spatialDistance <= 0.0D || distance >= spatialDistance) {
            // Beyond the configured distance: the normal, completely non-directional group voice.
            return;
        }

        double fadeDistance = SpatialGroupsCommonConfig.FADE_DISTANCE.get();
        double fade = SpatialGroupsMath.fadeFactor(distance, spatialDistance, fadeDistance);
        double gain = SpatialGroupsMath.blendedGain(
                distance,
                spatialDistance,
                fadeDistance,
                SpatialGroupsCommonConfig.MINIMUM_GAIN.get()
        );

        Vec3 direction = computeBlendedDirection(camera, listenerPosition, speakerPosition, fade);
        if (direction == null) {
            return;
        }
        Vec3 positionedSource = listenerPosition.add(direction.scale(distance));

        SPATIALIZED_SOURCES.add(source);
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, 0);
        // The cross fade gain already contains the distance attenuation.
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0.0F);
        AL10.alSource3f(
                source,
                AL10.AL_POSITION,
                (float) positionedSource.x,
                (float) positionedSource.y,
                (float) positionedSource.z
        );
        float currentGain = AL10.alGetSourcef(source, AL10.AL_GAIN);
        AL10.alSourcef(source, AL10.AL_GAIN, (float) (currentGain * gain));

        int error = AL10.alGetError();
        if (error != 0) {
            SpatialGroupsMod.LOGGER.debug("SpatialGroups: OpenAL error while spatializing group audio: {}", error);
        }

        if (ACTIVE_NOTICE_LOGGED.compareAndSet(false, true)) {
            SpatialGroupsMod.LOGGER.info(
                    "SpatialGroups: spatializing group voice (voice distance: {} blocks, fade: {} blocks)",
                    String.format("%.1f", spatialDistance),
                    String.format("%.1f", fadeDistance)
            );
        }
    }

    /**
     * Computes the direction of the sound in the listener's local space and rotates it towards the
     * listener's view direction by the fade factor.
     *
     * <p>The azimuth and elevation around the listener are scaled by {@code 1 - fade} instead of
     * moving the sound with a naive vector lerp. That keeps the rotation smooth while walking and
     * at {@code fade == 1} the sound ends up exactly in front of the listener, i.e. equally loud on
     * both ears, which is the non-directional group voice. The only degenerate case is a speaker
     * that is exactly behind the listener at a partial fade: a stereo signal has no defined side
     * directly behind, so the side may flip when crossing that exact point. It is a single angle,
     * not an audible jump in normal play.</p>
     *
     * @return a normalized direction, or {@code null} if the camera vectors are invalid
     */
    private static Vec3 computeBlendedDirection(Camera camera, Vec3 listenerPosition, Vec3 speakerPosition, double fade) {
        Vector3f lookVector = camera.getLookVector();
        Vector3f upVector = camera.getUpVector();

        Vec3 forward = new Vec3(lookVector.x(), lookVector.y(), lookVector.z());
        Vec3 up = new Vec3(upVector.x(), upVector.y(), upVector.z());
        Vec3 right = forward.cross(up);
        if (right.lengthSqr() < 1.0E-8D) {
            return null;
        }
        right = right.normalize();

        Vec3 toSpeaker = speakerPosition.subtract(listenerPosition).normalize();
        double localRight = toSpeaker.dot(right);
        double localUp = toSpeaker.dot(up);
        double localForward = toSpeaker.dot(forward);

        double azimuth = Math.atan2(localRight, localForward);
        double elevation = Math.asin(SpatialGroupsMath.clamp(localUp, -1.0D, 1.0D));

        double directionStrength = 1.0D - fade;
        azimuth *= directionStrength;
        elevation *= directionStrength;

        double cosElevation = Math.cos(elevation);
        Vec3 direction = right.scale(Math.sin(azimuth) * cosElevation)
                .add(up.scale(Math.sin(elevation)))
                .add(forward.scale(Math.cos(azimuth) * cosElevation));

        if (direction.lengthSqr() < 1.0E-8D) {
            return forward.normalize();
        }
        return direction.normalize();
    }

    /**
     * @return the distance up to which group voice should be rendered positionally
     */
    private static double resolveSpatialDistance() {
        double configured = SpatialGroupsCommonConfig.SPATIAL_DISTANCE.get();
        if (configured > 0.0D) {
            return configured;
        }
        // Cached once per voice connection by SpatialGroupsClientState; no SVC API call per buffer.
        double serverDistance = SpatialGroupsClientState.getVoiceChatDistance();
        return serverDistance > 0.0D ? serverDistance : DEFAULT_VOICE_DISTANCE;
    }
}
