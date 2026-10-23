package io.github.andriyko69.weatherclouds.client.weather;

import io.github.andriyko69.weatherclouds.util.WeatherCloudType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CloudColumnTest {
    private final Map<Integer, Integer> surfaces = new HashMap<>();
    private int surfaceQueries;

    private final CloudColumn.SurfaceFinder finder = (x, cloudY, z) -> {
        this.surfaceQueries++;
        return this.surfaces.getOrDefault(cloudY, CloudColumn.NO_SPAN);
    };

    @Test
    void stackedCloudsWithGapProduceSpansTopDown() {
        CloudColumn column = new CloudColumn(3, -7);
        column.putCloud(60, WeatherCloudType.SNOW);
        column.putCloud(100, WeatherCloudType.RAIN);
        this.surfaces.put(100, 60);
        this.surfaces.put(60, 40);

        column.rebuildIfDirty(this.finder);

        assertEquals(2, column.spanCount());
        assertEquals(WeatherCloudType.RAIN, column.spanType(0));
        assertEquals(61, column.spanBottom(0));
        assertEquals(100, column.spanTop(0));
        assertEquals(WeatherCloudType.SNOW, column.spanType(1));
        assertEquals(41, column.spanBottom(1));
        assertEquals(60, column.spanTop(1));

        assertEquals(WeatherCloudType.RAIN, column.typeAt(61));
        assertEquals(WeatherCloudType.RAIN, column.typeAt(99));
        assertNull(column.typeAt(100));
        assertNull(column.typeAt(60));
        assertEquals(WeatherCloudType.SNOW, column.typeAt(41));
        assertNull(column.typeAt(40));
    }

    @Test
    void cloudRestingOnCloudHasNoSpan() {
        CloudColumn column = new CloudColumn(0, 0);
        column.putCloud(81, WeatherCloudType.RAIN);
        column.putCloud(80, WeatherCloudType.RAIN);
        this.surfaces.put(80, 64);

        column.rebuildIfDirty(this.finder);

        assertEquals(1, column.spanCount());
        assertEquals(80, column.spanTop(0));
        assertEquals(65, column.spanBottom(0));
    }

    @Test
    void spansAreCachedUntilMarkedDirty() {
        CloudColumn column = new CloudColumn(0, 0);
        column.putCloud(80, WeatherCloudType.RAIN);
        this.surfaces.put(80, 64);

        column.rebuildIfDirty(this.finder);
        column.rebuildIfDirty(this.finder);
        assertEquals(1, this.surfaceQueries);

        this.surfaces.put(80, 70);
        column.markDirty();
        column.rebuildIfDirty(this.finder);

        assertEquals(2, this.surfaceQueries);
        assertEquals(71, column.spanBottom(0));
    }

    @Test
    void removingCloudsUpdatesSpansAndEmptiness() {
        CloudColumn column = new CloudColumn(0, 0);
        column.putCloud(90, WeatherCloudType.RAIN);
        column.putCloud(70, WeatherCloudType.SNOW);
        this.surfaces.put(90, 70);
        this.surfaces.put(70, 50);
        column.rebuildIfDirty(this.finder);

        column.removeCloud(90);
        column.rebuildIfDirty(this.finder);

        assertEquals(1, column.spanCount());
        assertEquals(WeatherCloudType.SNOW, column.spanType(0));
        assertFalse(column.isEmpty());

        column.removeCloud(70);
        column.rebuildIfDirty(this.finder);
        assertTrue(column.isEmpty());
        assertEquals(0, column.spanCount());
    }

    @Test
    void replacingCloudTypeKeepsSingleEntry() {
        CloudColumn column = new CloudColumn(0, 0);
        column.putCloud(80, WeatherCloudType.RAIN);
        column.putCloud(80, WeatherCloudType.SNOW);
        this.surfaces.put(80, 64);

        column.rebuildIfDirty(this.finder);

        assertEquals(1, column.spanCount());
        assertEquals(WeatherCloudType.SNOW, column.spanType(0));
    }

    @Test
    void manyCloudsGrowStorage() {
        CloudColumn column = new CloudColumn(0, 0);
        for (int y = 0; y < 20; y++) {
            int cloudY = 200 - y * 10;
            column.putCloud(cloudY, WeatherCloudType.RAIN);
            this.surfaces.put(cloudY, cloudY - 5);
        }

        column.rebuildIfDirty(this.finder);

        assertEquals(20, column.spanCount());
        for (int i = 1; i < column.spanCount(); i++) {
            assertTrue(column.spanTop(i - 1) > column.spanTop(i));
        }
    }

    @Test
    void nearestSpanBottomPicksClosestSpan() {
        CloudColumn column = new CloudColumn(0, 0);
        column.putCloud(100, WeatherCloudType.RAIN);
        column.putCloud(60, WeatherCloudType.SNOW);
        this.surfaces.put(100, 80);
        this.surfaces.put(60, 40);
        column.rebuildIfDirty(this.finder);

        assertEquals(81, column.nearestSpanBottom(90));
        assertEquals(81, column.nearestSpanBottom(120));
        assertEquals(41, column.nearestSpanBottom(50));
        assertEquals(41, column.nearestSpanBottom(0));
        assertEquals(CloudColumn.NO_SPAN, new CloudColumn(1, 1).nearestSpanBottom(64));
    }

    @Test
    void spanIntersectionUsesInclusiveBottomExclusiveTop() {
        CloudColumn column = new CloudColumn(0, 0);
        column.putCloud(100, WeatherCloudType.RAIN);
        this.surfaces.put(100, 79);
        column.rebuildIfDirty(this.finder);

        assertTrue(column.hasSpanIntersecting(60, 80));
        assertFalse(column.hasSpanIntersecting(60, 79));
        assertTrue(column.hasSpanIntersecting(99, 120));
        assertFalse(column.hasSpanIntersecting(100, 120));
    }

    @Test
    void columnKeyDistinguishesNegativeCoordinates() {
        assertFalse(CloudColumn.columnKey(-1, 0) == CloudColumn.columnKey(0, -1));
        assertFalse(CloudColumn.columnKey(1, -1) == CloudColumn.columnKey(-1, 1));
    }
}
