package com.spatialgroups;

/**
 * Pure math used for the 3D / non-directional cross fade.
 *
 * <p>This class intentionally has no dependency on Minecraft, OpenAL or Simple Voice Chat so the
 * transition curve can be verified without starting the game.</p>
 */
public final class SpatialGroupsMath {

    private SpatialGroupsMath() {
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    /**
     * Classic smoothstep for an input in {@code [0, 1]}: 0 at the start, 1 at the end and with a
     * continuous first derivative at both ends. That is what makes the transition natural.
     */
    public static double smoothstep(double value) {
        double x = clamp(value, 0.0D, 1.0D);
        return x * x * (3.0D - 2.0D * x);
    }

    /**
     * Distance attenuation of Simple Voice Chat's positional voice.
     *
     * <p>Simple Voice Chat uses the OpenAL linear distance model with the reference distance set
     * to half of the maximum distance. That means: no attenuation up to half of the maximum
     * distance, linear attenuation from there to the maximum distance and silence beyond it. This
     * method reproduces exactly that curve.</p>
     *
     * @param distance    distance between listener and speaker in blocks
     * @param maxDistance the voice distance in blocks
     * @return gain in {@code [0, 1]}
     */
    public static double positionalGain(double distance, double maxDistance) {
        if (maxDistance <= 0.0D) {
            return 0.0D;
        }
        double referenceDistance = maxDistance / 2.0D;
        if (distance <= referenceDistance) {
            return 1.0D;
        }
        return clamp(1.0D - (distance - referenceDistance) / (maxDistance - referenceDistance), 0.0D, 1.0D);
    }

    /**
     * How far the rendering is already in non-directional mode.
     *
     * <p>The fade band ends exactly at {@code spatialDistance}, so the voice is completely
     * non-directional at the configured distance (and beyond) and fully positional at the
     * beginning of the band. The band starts at {@code spatialDistance - fadeDistance} (clamped to
     * zero for very wide bands).</p>
     *
     * @return 0 for fully positional, 1 for fully non-directional
     */
    public static double fadeFactor(double distance, double spatialDistance, double fadeDistance) {
        if (spatialDistance <= 0.0D) {
            return 1.0D;
        }
        double start = Math.max(0.0D, spatialDistance - Math.max(0.0D, fadeDistance));
        double span = spatialDistance - start;
        if (span <= 1.0E-6D) {
            return distance >= spatialDistance ? 1.0D : 0.0D;
        }
        return smoothstep((distance - start) / span);
    }

    /**
     * The gain multiplier that is applied on top of the volume Simple Voice Chat already assigned
     * to the group audio.
     *
     * <p>At {@code fade == 0} this is the normal positional distance attenuation (with an
     * optional floor so the sound does not disappear at the edge of the voice range). At
     * {@code fade == 1} it is {@code 1}, i.e. the unchanged group volume.</p>
     */
    public static double blendedGain(double distance, double spatialDistance, double fadeDistance, double minimumGain) {
        double fade = fadeFactor(distance, spatialDistance, fadeDistance);
        double positional = Math.max(clamp(minimumGain, 0.0D, 1.0D), positionalGain(distance, spatialDistance));
        return (1.0D - fade) * positional + fade;
    }
}
