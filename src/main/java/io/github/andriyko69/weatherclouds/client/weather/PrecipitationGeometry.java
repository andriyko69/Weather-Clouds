package io.github.andriyko69.weatherclouds.client.weather;

public final class PrecipitationGeometry {
    public static final int FANCY_RADIUS = 48;
    public static final int FAST_RADIUS = 32;
    public static final float NEAR_DISTANCE = 10.0F;
    public static final float MAX_STACKED_ALPHA = 0.6F;
    public static final int ANGULAR_BINS = 128;

    private static final float NEAR_EDGE_ALPHA = 0.5F;
    private static final float FAR_DECAY = 0.045F;
    private static final float STACK_CAP_BLEND_DISTANCE = 6.0F;
    private static final float EDGE_FADE_DISTANCE = 8.0F;

    private PrecipitationGeometry() {
    }

    public static int renderRadius(boolean fancyGraphics, int renderDistanceChunks) {
        return Math.min(fancyGraphics ? FANCY_RADIUS : FAST_RADIUS, renderDistanceChunks * 16);
    }

    /**
     * Sheet alpha by horizontal distance from the camera.
     * <ul>
     *     <li>Within {@link #NEAR_DISTANCE}: vanilla's curve {@code (1 - (d / 10)^2) * strength + 0.5}.</li>
     *     <li>Beyond: exponential decay from 0.5 (about 0.4 at 15, 0.2 at 30, 0.15 at 40), limited by
     *     {@code stackCap} so overlapping sheets stay translucent.</li>
     *     <li>Smoothstep fade to zero over the last blocks of {@code radius}.</li>
     * </ul>
     */
    public static float columnAlpha(float distance, float radius, float strength, float stackCap) {
        if (distance >= radius) {
            return 0.0F;
        }

        float alpha;
        if (distance <= NEAR_DISTANCE) {
            float normalized = distance / NEAR_DISTANCE;
            alpha = (1.0F - normalized * normalized) * strength + NEAR_EDGE_ALPHA;
        } else {
            alpha = NEAR_EDGE_ALPHA * (float) Math.exp(-FAR_DECAY * (distance - NEAR_DISTANCE));
            float capBlend = smoothstep(NEAR_DISTANCE, NEAR_DISTANCE + STACK_CAP_BLEND_DISTANCE, distance);
            alpha = Math.min(alpha, 1.0F + (stackCap - 1.0F) * capBlend);
        }

        return alpha * (1.0F - smoothstep(radius - EDGE_FADE_DISTANCE, radius, distance));
    }

    /**
     * Largest per-sheet alpha keeping {@code layers} overlapping sheets at or below {@link #MAX_STACKED_ALPHA}
     * combined, since alpha blending accumulates as {@code 1 - (1 - a)^n}.
     */
    public static float stackAlphaCap(int layers) {
        if (layers <= 1) {
            return MAX_STACKED_ALPHA;
        }
        return (float) (1.0D - Math.pow(1.0D - MAX_STACKED_ALPHA, 1.0D / layers));
    }

    /**
     * Horizontal view-direction bin of a column offset from the camera. Columns in the same bin overlap on screen,
     * so their count estimates how many sheets stack along that line of sight.
     */
    public static int angularBin(double offsetX, double offsetZ) {
        double turns = (Math.atan2(offsetZ, offsetX) + Math.PI) / (2.0D * Math.PI);
        return Math.min(ANGULAR_BINS - 1, (int) (turns * ANGULAR_BINS));
    }

    /**
     * Horizontal half-extent of the camera-facing sheet, matching vanilla's {@code rainSizeX/Z} tables, which
     * only cover offsets within 15 blocks.
     */
    public static float sheetHalfX(int offsetX, int offsetZ) {
        float length = (float) Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
        return length == 0.0F ? 0.0F : -offsetZ / length * 0.5F;
    }

    public static float sheetHalfZ(int offsetX, int offsetZ) {
        float length = (float) Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
        return length == 0.0F ? 0.5F : offsetX / length * 0.5F;
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Math.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
