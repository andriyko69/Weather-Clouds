package io.github.andriyko69.weatherclouds.client.weather;

import io.github.andriyko69.weatherclouds.util.WeatherCloudType;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.Nullable;

/**
 * Cloud columns grouped by the chunk instance they were read from. Not thread-safe; a single owner thread
 * mutates and reads it. Columns are kept in a dense list so per-frame iteration needs no iterator objects.
 */
public final class CloudColumnIndex {
    @FunctionalInterface
    public interface ChunkValidator {
        boolean isStillLoaded(int chunkX, int chunkZ, Object chunk);
    }

    private static final class TrackedChunk {
        final int chunkX;
        final int chunkZ;
        final Object chunk;
        final ObjectArrayList<CloudColumn> columns = new ObjectArrayList<>();

        TrackedChunk(int chunkX, int chunkZ, Object chunk) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.chunk = chunk;
        }
    }

    private final Long2ObjectOpenHashMap<CloudColumn> columns = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<TrackedChunk> chunks = new Long2ObjectOpenHashMap<>();
    private final ObjectArrayList<CloudColumn> active = new ObjectArrayList<>();

    public static long chunkKey(int chunkX, int chunkZ) {
        return (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
    }

    public void clear() {
        this.columns.clear();
        this.chunks.clear();
        this.active.clear();
    }

    public int size() {
        return this.active.size();
    }

    public CloudColumn get(int index) {
        return this.active.get(index);
    }

    @Nullable
    public CloudColumn columnAt(int x, int z) {
        return this.columns.get(CloudColumn.columnKey(x, z));
    }

    public int trackedChunkCount() {
        return this.chunks.size();
    }

    /**
     * Records a cloud read from {@code chunk}. A different chunk instance at the same position means the old one
     * was replaced, so its stale columns are discarded first.
     */
    public void putCloud(Object chunk, int x, int y, int z, WeatherCloudType type) {
        TrackedChunk trackedChunk = this.trackChunk(chunk, x >> 4, z >> 4);
        long key = CloudColumn.columnKey(x, z);
        CloudColumn column = this.columns.get(key);
        if (column == null) {
            column = new CloudColumn(x, z);
            this.columns.put(key, column);
            trackedChunk.columns.add(column);
            column.trackerIndex = this.active.size();
            this.active.add(column);
        }
        column.putCloud(y, type);
    }

    public void removeCloud(int x, int y, int z) {
        CloudColumn column = this.columns.get(CloudColumn.columnKey(x, z));
        if (column == null) {
            return;
        }

        column.removeCloud(y);
        if (column.isEmpty()) {
            this.removeColumn(column);
        }
    }

    public void markDirty(int x, int z) {
        CloudColumn column = this.columns.get(CloudColumn.columnKey(x, z));
        if (column != null) {
            column.markDirty();
        }
    }

    public void removeChunk(int chunkX, int chunkZ) {
        TrackedChunk trackedChunk = this.chunks.remove(chunkKey(chunkX, chunkZ));
        if (trackedChunk != null) {
            this.dropColumns(trackedChunk);
        }
    }

    /**
     * Drops every chunk the validator no longer reports as loaded. This catches removal paths that fire no
     * unload event, such as client chunk storage being recentred or resized.
     */
    public void retainLoadedChunks(ChunkValidator validator) {
        ObjectIterator<Long2ObjectMap.Entry<TrackedChunk>> iterator = this.chunks.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            TrackedChunk trackedChunk = iterator.next().getValue();
            if (!validator.isStillLoaded(trackedChunk.chunkX, trackedChunk.chunkZ, trackedChunk.chunk)) {
                iterator.remove();
                this.dropColumns(trackedChunk);
            }
        }
    }

    private TrackedChunk trackChunk(Object chunk, int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        TrackedChunk trackedChunk = this.chunks.get(key);
        if (trackedChunk != null && trackedChunk.chunk == chunk) {
            return trackedChunk;
        }

        if (trackedChunk != null) {
            this.dropColumns(trackedChunk);
        }

        trackedChunk = new TrackedChunk(chunkX, chunkZ, chunk);
        this.chunks.put(key, trackedChunk);
        return trackedChunk;
    }

    private void removeColumn(CloudColumn column) {
        this.columns.remove(column.key);
        long key = chunkKey(column.x >> 4, column.z >> 4);
        TrackedChunk trackedChunk = this.chunks.get(key);
        if (trackedChunk != null) {
            trackedChunk.columns.remove(column);
            if (trackedChunk.columns.isEmpty()) {
                this.chunks.remove(key);
            }
        }
        this.removeActive(column);
    }

    private void dropColumns(TrackedChunk trackedChunk) {
        for (int i = 0; i < trackedChunk.columns.size(); i++) {
            CloudColumn column = trackedChunk.columns.get(i);
            this.columns.remove(column.key);
            this.removeActive(column);
        }
    }

    private void removeActive(CloudColumn column) {
        int index = column.trackerIndex;
        CloudColumn last = this.active.pop();
        if (last != column) {
            this.active.set(index, last);
            last.trackerIndex = index;
        }
        column.trackerIndex = -1;
    }
}
