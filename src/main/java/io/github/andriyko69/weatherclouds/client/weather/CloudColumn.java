package io.github.andriyko69.weatherclouds.client.weather;

import io.github.andriyko69.weatherclouds.util.WeatherCloudType;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Cloud blocks of one (x, z) column and their cached precipitation spans. Spans are rebuilt lazily after
 * {@link #markDirty()}; every accessor is allocation-free so it can run inside per-frame render loops.
 */
public final class CloudColumn {
    public static final int NO_SPAN = Integer.MIN_VALUE;

    @FunctionalInterface
    public interface SurfaceFinder {
        /**
         * @return the precipitation surface Y below the cloud, or {@link #NO_SPAN}
         */
        int findSurfaceY(int x, int cloudY, int z);
    }

    final int x;
    final int z;
    final long key;
    int trackerIndex = -1;

    // Sorted top-down so spans keep the stacked-cloud order of a downward column scan.
    private int[] cloudYs = new int[2];
    private WeatherCloudType[] cloudTypes = new WeatherCloudType[2];
    private int cloudCount;

    private boolean dirty = true;
    private int[] spanBottoms = new int[2];
    private int[] spanTops = new int[2];
    private WeatherCloudType[] spanTypes = new WeatherCloudType[2];
    private int spanCount;

    public CloudColumn(int x, int z) {
        this.x = x;
        this.z = z;
        this.key = columnKey(x, z);
    }

    public static long columnKey(int x, int z) {
        return (long) x << 32 | (z & 0xFFFFFFFFL);
    }

    public int x() {
        return this.x;
    }

    public int z() {
        return this.z;
    }

    public boolean isEmpty() {
        return this.cloudCount == 0;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void putCloud(int y, WeatherCloudType type) {
        int index = 0;
        while (index < this.cloudCount && this.cloudYs[index] > y) {
            index++;
        }

        if (index < this.cloudCount && this.cloudYs[index] == y) {
            this.cloudTypes[index] = type;
        } else {
            if (this.cloudCount == this.cloudYs.length) {
                this.cloudYs = Arrays.copyOf(this.cloudYs, this.cloudCount * 2);
                this.cloudTypes = Arrays.copyOf(this.cloudTypes, this.cloudCount * 2);
            }
            System.arraycopy(this.cloudYs, index, this.cloudYs, index + 1, this.cloudCount - index);
            System.arraycopy(this.cloudTypes, index, this.cloudTypes, index + 1, this.cloudCount - index);
            this.cloudYs[index] = y;
            this.cloudTypes[index] = type;
            this.cloudCount++;
        }

        this.dirty = true;
    }

    public void removeCloud(int y) {
        for (int i = 0; i < this.cloudCount; i++) {
            if (this.cloudYs[i] == y) {
                System.arraycopy(this.cloudYs, i + 1, this.cloudYs, i, this.cloudCount - i - 1);
                System.arraycopy(this.cloudTypes, i + 1, this.cloudTypes, i, this.cloudCount - i - 1);
                this.cloudCount--;
                this.cloudTypes[this.cloudCount] = null;
                this.dirty = true;
                return;
            }
        }
    }

    public void rebuildIfDirty(SurfaceFinder surfaceFinder) {
        if (!this.dirty) {
            return;
        }

        if (this.spanBottoms.length < this.cloudCount) {
            this.spanBottoms = new int[this.cloudYs.length];
            this.spanTops = new int[this.cloudYs.length];
            this.spanTypes = new WeatherCloudType[this.cloudYs.length];
        }

        Arrays.fill(this.spanTypes, null);
        this.spanCount = 0;
        for (int i = 0; i < this.cloudCount; i++) {
            int cloudY = this.cloudYs[i];
            int surfaceY = surfaceFinder.findSurfaceY(this.x, cloudY, this.z);
            if (surfaceY == NO_SPAN) {
                continue;
            }

            this.spanBottoms[this.spanCount] = surfaceY + 1;
            this.spanTops[this.spanCount] = cloudY;
            this.spanTypes[this.spanCount] = this.cloudTypes[i];
            this.spanCount++;
        }

        this.dirty = false;
    }

    public int spanCount() {
        return this.spanCount;
    }

    /** Lowest Y receiving precipitation (one above the surface), inclusive. */
    public int spanBottom(int index) {
        return this.spanBottoms[index];
    }

    /** Y of the emitting cloud block, exclusive. */
    public int spanTop(int index) {
        return this.spanTops[index];
    }

    public WeatherCloudType spanType(int index) {
        return this.spanTypes[index];
    }

    @Nullable
    public WeatherCloudType typeAt(int y) {
        for (int i = 0; i < this.spanCount; i++) {
            if (y >= this.spanBottoms[i] && y < this.spanTops[i]) {
                return this.spanTypes[i];
            }
        }
        return null;
    }

    /**
     * @return the bottom Y of the span closest to {@code y}, or {@link #NO_SPAN}
     */
    public int nearestSpanBottom(int y) {
        int nearestBottom = NO_SPAN;
        int nearestDistance = Integer.MAX_VALUE;

        for (int i = 0; i < this.spanCount; i++) {
            int distance = distanceToSpan(i, y);
            if (distance < nearestDistance) {
                nearestBottom = this.spanBottoms[i];
                nearestDistance = distance;
            }
        }

        return nearestBottom;
    }

    public boolean hasSpanIntersecting(int minY, int maxY) {
        for (int i = 0; i < this.spanCount; i++) {
            if (this.spanBottoms[i] <= maxY && this.spanTops[i] > minY) {
                return true;
            }
        }
        return false;
    }

    private int distanceToSpan(int index, int y) {
        if (y < this.spanBottoms[index]) {
            return this.spanBottoms[index] - y;
        }
        if (y >= this.spanTops[index]) {
            return y - this.spanTops[index] + 1;
        }
        return 0;
    }
}
