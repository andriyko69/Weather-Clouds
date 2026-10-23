package io.github.andriyko69.weatherclouds.client.weather;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrecipitationGeometryTest {
    private static final float NO_CAP = 1.0F;

    @Test
    void renderRadiusExtendsBeyondVanillaAndRespectsRenderDistance() {
        assertEquals(48, PrecipitationGeometry.renderRadius(true, 12));
        assertEquals(32, PrecipitationGeometry.renderRadius(false, 12));
        assertEquals(32, PrecipitationGeometry.renderRadius(true, 2));
    }

    @Test
    void nearRangeMatchesVanillaCurve() {
        for (float distance = 0.0F; distance <= 10.0F; distance += 0.5F) {
            float normalized = distance / 10.0F;
            assertEquals((1.0F - normalized * normalized) * 0.5F + 0.5F,
                    PrecipitationGeometry.columnAlpha(distance, 48.0F, 0.5F, NO_CAP), 1.0E-6F);
            assertEquals((1.0F - normalized * normalized) * 0.3F + 0.5F,
                    PrecipitationGeometry.columnAlpha(distance, 48.0F, 0.3F, NO_CAP), 1.0E-6F);
        }
    }

    @Test
    void farRangeHitsTargetOpacityBands() {
        for (float strength : new float[]{0.5F, 0.3F}) {
            assertInRange(PrecipitationGeometry.columnAlpha(15.0F, 48.0F, strength, NO_CAP), 0.35F, 0.42F);
            assertInRange(PrecipitationGeometry.columnAlpha(20.0F, 48.0F, strength, NO_CAP), 0.30F, 0.40F);
            assertInRange(PrecipitationGeometry.columnAlpha(30.0F, 48.0F, strength, NO_CAP), 0.15F, 0.21F);
            assertInRange(PrecipitationGeometry.columnAlpha(35.0F, 48.0F, strength, NO_CAP), 0.15F, 0.20F);
            assertTrue(PrecipitationGeometry.columnAlpha(40.0F, 48.0F, strength, NO_CAP) <= 0.16F);
            assertTrue(PrecipitationGeometry.columnAlpha(47.0F, 48.0F, strength, NO_CAP) < 0.01F);
        }
    }

    @Test
    void alphaIsContinuousMonotonicAndZeroAtRadius() {
        for (float radius : new float[]{32.0F, 48.0F}) {
            float previous = PrecipitationGeometry.columnAlpha(0.0F, radius, 0.5F, NO_CAP);
            for (float distance = 0.25F; distance <= radius + 2.0F; distance += 0.25F) {
                float alpha = PrecipitationGeometry.columnAlpha(distance, radius, 0.5F, NO_CAP);
                assertTrue(alpha >= 0.0F, "negative alpha at " + distance);
                assertTrue(alpha <= previous + 1.0E-6F, "alpha increased at " + distance);
                assertTrue(previous - alpha < 0.05F, "alpha jumped at " + distance);
                previous = alpha;
            }
            assertEquals(0.0F, PrecipitationGeometry.columnAlpha(radius, radius, 0.5F, NO_CAP));
        }
    }

    @Test
    void stackCapKeepsCombinedAlphaTranslucent() {
        for (int layers = 1; layers <= 400; layers++) {
            float cap = PrecipitationGeometry.stackAlphaCap(layers);
            double combined = 1.0D - Math.pow(1.0D - cap, layers);
            assertTrue(combined <= PrecipitationGeometry.MAX_STACKED_ALPHA + 1.0E-4D, "layers " + layers);
        }
        assertTrue(PrecipitationGeometry.stackAlphaCap(20) < PrecipitationGeometry.stackAlphaCap(5));
    }

    @Test
    void stackCapLimitsFarSheetsButNotNearSheets() {
        float cap = PrecipitationGeometry.stackAlphaCap(25);
        assertTrue(PrecipitationGeometry.columnAlpha(20.0F, 48.0F, 0.5F, cap) <= cap + 1.0E-6F);
        assertEquals(PrecipitationGeometry.columnAlpha(5.0F, 48.0F, 0.5F, NO_CAP),
                PrecipitationGeometry.columnAlpha(5.0F, 48.0F, 0.5F, cap));
    }

    @Test
    void angularBinsCoverFullCircle() {
        assertNotEquals(PrecipitationGeometry.angularBin(10, 0), PrecipitationGeometry.angularBin(-10, 0));
        assertEquals(PrecipitationGeometry.angularBin(10, 0), PrecipitationGeometry.angularBin(40, 0));
        for (int degrees = 0; degrees < 360; degrees++) {
            double radians = Math.toRadians(degrees);
            int bin = PrecipitationGeometry.angularBin(Math.cos(radians), Math.sin(radians));
            assertTrue(bin >= 0 && bin < PrecipitationGeometry.ANGULAR_BINS);
        }
        int binAtPi = PrecipitationGeometry.angularBin(-1.0D, 0.0D);
        assertTrue(binAtPi >= 0 && binAtPi < PrecipitationGeometry.ANGULAR_BINS);
    }

    @Test
    void sheetMatchesVanillaRainSizeTables() {
        for (int dz = -15; dz <= 15; dz++) {
            for (int dx = -15; dx <= 15; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                float length = (float) Math.sqrt(dx * dx + dz * dz);
                assertEquals(-dz / length * 0.5F, PrecipitationGeometry.sheetHalfX(dx, dz), 1.0E-6F);
                assertEquals(dx / length * 0.5F, PrecipitationGeometry.sheetHalfZ(dx, dz), 1.0E-6F);
            }
        }
    }

    @Test
    void cameraColumnSheetIsFinite() {
        assertTrue(Float.isFinite(PrecipitationGeometry.sheetHalfX(0, 0)));
        assertTrue(Float.isFinite(PrecipitationGeometry.sheetHalfZ(0, 0)));
    }

    private static void assertInRange(float value, float min, float max) {
        assertTrue(value >= min && value <= max, value + " not in [" + min + ", " + max + "]");
    }
}
