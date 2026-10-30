import com.spatialgroups.SpatialGroupsMath;

/**
 * Small standalone check for the cross fade math. Not shipped in the mod.
 * Compile together with src/main/java/com/spatialgroups/SpatialGroupsMath.java and run.
 */
public final class MathSelfTest {

    private static int failures;

    public static void main(String[] args) {
        double r = 48.0D;
        double fade = 12.0D;
        double minGain = 0.25D;
        double bandStart = r - fade;

        // Positional attenuation must match Simple Voice Chat's OpenAL setup:
        // no attenuation up to half of the voice distance, then linear to zero at the distance.
        near("positional gain at 0", SpatialGroupsMath.positionalGain(0.0D, r), 1.0D);
        near("positional gain at reference distance", SpatialGroupsMath.positionalGain(r / 2.0D, r), 1.0D);
        near("positional gain at 3/4 distance", SpatialGroupsMath.positionalGain(r * 0.75D, r), 0.5D);
        near("positional gain at full distance", SpatialGroupsMath.positionalGain(r, r), 0.0D);

        // Fade factor: fully positional up to the start of the band, fully non-directional at the
        // configured distance.
        near("fade before band", SpatialGroupsMath.fadeFactor(0.0D, r, fade), 0.0D);
        near("fade at band start", SpatialGroupsMath.fadeFactor(bandStart, r, fade), 0.0D);
        near("fade at distance", SpatialGroupsMath.fadeFactor(r, r, fade), 1.0D);
        near("fade beyond distance", SpatialGroupsMath.fadeFactor(r + 10.0D, r, fade), 1.0D);
        near("fade in the middle of the band", SpatialGroupsMath.fadeFactor(bandStart + fade / 2.0D, r, fade), 0.5D);

        // Gain: starts with the positional attenuation, ends with the unchanged group volume.
        near("gain at band start", SpatialGroupsMath.blendedGain(bandStart, r, fade, minGain),
                Math.max(minGain, SpatialGroupsMath.positionalGain(bandStart, r)));
        near("gain at distance", SpatialGroupsMath.blendedGain(r, r, fade, minGain), 1.0D);
        near("gain beyond distance", SpatialGroupsMath.blendedGain(r + 5.0D, r, fade, minGain), 1.0D);
        near("gain at listener", SpatialGroupsMath.blendedGain(0.0D, r, fade, minGain), 1.0D);

        // The gain must never leave [0, 1] and must be continuous over the whole band.
        double previous = SpatialGroupsMath.blendedGain(0.0D, r, fade, minGain);
        for (double d = 0.0D; d <= r + fade; d += 0.05D) {
            double gain = SpatialGroupsMath.blendedGain(d, r, fade, minGain);
            if (!(gain >= 0.0D && gain <= 1.0D)) {
                fail("gain out of range at " + d + ": " + gain);
            }
            if (Math.abs(gain - previous) > 0.05D) {
                fail("gain jump at " + d + ": " + previous + " -> " + gain);
            }
            previous = gain;
        }

        // Minimum gain is respected for small fade bands.
        near("minimum gain floor", SpatialGroupsMath.blendedGain(r - 0.1D, r, 1.0D, 0.4D), 1.0D, 0.05D);

        // Very wide fade bands must not produce invalid values.
        near("wide band at listener", SpatialGroupsMath.blendedGain(0.0D, r, 200.0D, minGain), 1.0D);

        if (failures == 0) {
            System.out.println("SpatialGroups math self test: OK");
        } else {
            System.out.println("SpatialGroups math self test: " + failures + " failure(s)");
            System.exit(1);
        }
    }

    private static void near(String name, double actual, double expected) {
        near(name, actual, expected, 1.0E-6D);
    }

    private static void near(String name, double actual, double expected, double tolerance) {
        if (Math.abs(actual - expected) > tolerance) {
            fail(name + ": expected " + expected + " but got " + actual);
        }
    }

    private static void fail(String message) {
        failures++;
        System.out.println("FAILED: " + message);
    }
}
