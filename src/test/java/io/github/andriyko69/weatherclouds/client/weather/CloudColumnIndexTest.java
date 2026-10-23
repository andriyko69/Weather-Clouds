package io.github.andriyko69.weatherclouds.client.weather;

import io.github.andriyko69.weatherclouds.util.WeatherCloudType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudColumnIndexTest {
    private final CloudColumnIndex index = new CloudColumnIndex();
    private final Map<Long, Object> loadedChunks = new HashMap<>();

    private Object loadChunk(int chunkX, int chunkZ) {
        Object chunk = new Object();
        this.loadedChunks.put(CloudColumnIndex.chunkKey(chunkX, chunkZ), chunk);
        return chunk;
    }

    private void sweep() {
        this.index.retainLoadedChunks((chunkX, chunkZ, chunk) ->
                this.loadedChunks.get(CloudColumnIndex.chunkKey(chunkX, chunkZ)) == chunk);
    }

    private void assertDenseListConsistent() {
        Set<CloudColumn> seen = new HashSet<>();
        for (int i = 0; i < this.index.size(); i++) {
            CloudColumn column = this.index.get(i);
            assertTrue(seen.add(column), "duplicate column in dense list");
            assertSame(column, this.index.columnAt(column.x(), column.z()));
        }
    }

    @Test
    void teleportWithoutUnloadEventsDropsStaleChunksOnSweep() {
        for (int teleport = 0; teleport < 50; teleport++) {
            int chunkX = teleport * 1000;
            Object chunk = this.loadChunk(chunkX, 0);
            this.index.putCloud(chunk, chunkX * 16 + 3, 100, 5, WeatherCloudType.RAIN);
            this.index.putCloud(chunk, chunkX * 16 + 4, 100, 5, WeatherCloudType.SNOW);

            // The client cache recentred: the previous area is gone without any unload event.
            this.loadedChunks.keySet().removeIf(key -> key != CloudColumnIndex.chunkKey(chunkX, 0));
            this.sweep();

            assertEquals(2, this.index.size());
            assertEquals(1, this.index.trackedChunkCount());
            assertDenseListConsistent();
        }
    }

    @Test
    void storageShrinkDropsOutOfRangeChunks() {
        for (int chunkX = -4; chunkX <= 4; chunkX++) {
            Object chunk = this.loadChunk(chunkX, 0);
            this.index.putCloud(chunk, chunkX * 16, 90, 0, WeatherCloudType.RAIN);
        }

        this.loadedChunks.keySet().removeIf(key -> Math.abs((int) (long) key) > 2);
        this.sweep();

        assertEquals(5, this.index.size());
        assertEquals(5, this.index.trackedChunkCount());
        assertNull(this.index.columnAt(-64, 0));
        assertNotNull(this.index.columnAt(32, 0));
        assertDenseListConsistent();
    }

    @Test
    void replacedChunkInstanceDiscardsStaleColumns() {
        Object oldChunk = this.loadChunk(0, 0);
        this.index.putCloud(oldChunk, 1, 100, 1, WeatherCloudType.RAIN);
        this.index.putCloud(oldChunk, 2, 100, 2, WeatherCloudType.RAIN);

        Object newChunk = this.loadChunk(0, 0);
        this.index.putCloud(newChunk, 5, 80, 5, WeatherCloudType.SNOW);

        assertEquals(1, this.index.size());
        assertNull(this.index.columnAt(1, 1));
        assertNotNull(this.index.columnAt(5, 5));

        this.sweep();
        assertEquals(1, this.index.size());
        assertDenseListConsistent();
    }

    @Test
    void removingLastCloudReleasesColumnAndChunk() {
        Object chunk = this.loadChunk(-1, -1);
        this.index.putCloud(chunk, -3, 100, -3, WeatherCloudType.RAIN);
        this.index.putCloud(chunk, -3, 80, -3, WeatherCloudType.RAIN);
        this.index.putCloud(chunk, -5, 100, -5, WeatherCloudType.RAIN);

        this.index.removeCloud(-3, 100, -3);
        assertEquals(2, this.index.size());

        this.index.removeCloud(-3, 80, -3);
        this.index.removeCloud(-5, 100, -5);
        assertEquals(0, this.index.size());
        assertEquals(0, this.index.trackedChunkCount());
    }

    @Test
    void removeChunkKeepsOtherChunksAndDenseOrder() {
        Object a = this.loadChunk(0, 0);
        Object b = this.loadChunk(1, 0);
        Object c = this.loadChunk(2, 0);
        this.index.putCloud(a, 0, 100, 0, WeatherCloudType.RAIN);
        this.index.putCloud(b, 16, 100, 0, WeatherCloudType.RAIN);
        this.index.putCloud(b, 17, 100, 0, WeatherCloudType.RAIN);
        this.index.putCloud(c, 32, 100, 0, WeatherCloudType.SNOW);

        this.index.removeChunk(1, 0);

        assertEquals(2, this.index.size());
        assertNull(this.index.columnAt(16, 0));
        assertNotNull(this.index.columnAt(0, 0));
        assertNotNull(this.index.columnAt(32, 0));
        assertDenseListConsistent();
    }

    @Test
    void negativeCoordinatesMapToTheirOwnChunk() {
        Object chunk = this.loadChunk(-1, 0);
        this.index.putCloud(chunk, -1, 100, 0, WeatherCloudType.RAIN);

        this.index.removeChunk(0, 0);
        assertEquals(1, this.index.size());

        this.index.removeChunk(-1, 0);
        assertEquals(0, this.index.size());
    }

    @Test
    void pistonStyleMoveLeavesNoGhostEmitter() {
        Object chunk = this.loadChunk(0, 0);
        this.index.putCloud(chunk, 4, 100, 4, WeatherCloudType.RAIN);

        // Source becomes MOVING_PISTON, then the destination gets the cloud; removal happens from a non-cloud state.
        this.index.removeCloud(4, 100, 4);
        this.index.markDirty(4, 4);
        this.index.putCloud(chunk, 5, 100, 4, WeatherCloudType.RAIN);
        // A later PISTON_HEAD update at the source must be a harmless no-op.
        this.index.removeCloud(4, 100, 4);

        assertNull(this.index.columnAt(4, 4));
        assertNotNull(this.index.columnAt(5, 4));
        assertEquals(1, this.index.size());
        assertDenseListConsistent();
    }
}
