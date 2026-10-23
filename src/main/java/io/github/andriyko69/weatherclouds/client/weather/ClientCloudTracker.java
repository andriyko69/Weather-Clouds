package io.github.andriyko69.weatherclouds.client.weather;

import io.github.andriyko69.weatherclouds.block.WeatherCloudBlock;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Index of cloud block columns in the client's loaded chunks, so weather rendering visits only cloud columns
 * instead of scanning every column around the camera.
 * <p>
 * Owned by the client thread: it is fed by client chunk events and client block updates, and read by
 * {@code LevelRenderer}. A column's spans depend only on blocks of that column, which always live in one chunk,
 * so per-column dirty flags plus chunk load/unload give complete invalidation. Chunks the client cache drops
 * without an unload event are swept on client ticks.
 */
public final class ClientCloudTracker {
    private static final Predicate<BlockState> IS_CLOUD = state -> state.getBlock() instanceof WeatherCloudBlock;

    private static final CloudColumnIndex INDEX = new CloudColumnIndex();
    private static final BlockPos.MutableBlockPos SURFACE_CURSOR = new BlockPos.MutableBlockPos();
    private static final CloudColumn.SurfaceFinder SURFACE_FINDER = ClientCloudTracker::findSurfaceY;
    private static final CloudColumnIndex.ChunkValidator CHUNK_VALIDATOR = ClientCloudTracker::isStillLoaded;

    @Nullable
    private static ClientLevel trackedLevel;

    private ClientCloudTracker() {
    }

    public static void reset() {
        INDEX.clear();
        trackedLevel = null;
    }

    public static void onLevelUnload(ClientLevel level) {
        if (level == trackedLevel) {
            reset();
        }
    }

    public static void onChunkLoad(ClientLevel level, LevelChunk chunk) {
        track(level);

        ChunkPos chunkPos = chunk.getPos();
        // Chunk data packets may refill the same LevelChunk instance, so identity alone cannot detect a reload.
        INDEX.removeChunk(chunkPos.x, chunkPos.z);

        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section.hasOnlyAir() || !section.maybeHas(IS_CLOUD)) {
                continue;
            }

            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex));
            for (int localY = 0; localY < 16; localY++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        if (section.getBlockState(localX, localY, localZ).getBlock() instanceof WeatherCloudBlock cloud) {
                            INDEX.putCloud(
                                    chunk,
                                    chunkPos.getBlockX(localX),
                                    baseY + localY,
                                    chunkPos.getBlockZ(localZ),
                                    cloud.getCloudType()
                            );
                        }
                    }
                }
            }
        }
    }

    public static void onChunkUnload(ClientLevel level, ChunkPos chunkPos) {
        if (level == trackedLevel) {
            INDEX.removeChunk(chunkPos.x, chunkPos.z);
        }
    }

    /**
     * Reconciles the index with the committed state at {@code pos}. The previous state is deliberately not trusted:
     * piston moves pass through {@code MOVING_PISTON}, so a cloud may leave a position via a non-cloud state.
     */
    public static void onBlockChanged(ClientLevel level, LevelChunk chunk, BlockPos pos, BlockState newState) {
        track(level);

        if (newState.getBlock() instanceof WeatherCloudBlock cloud) {
            INDEX.putCloud(chunk, pos.getX(), pos.getY(), pos.getZ(), cloud.getCloudType());
        } else {
            INDEX.removeCloud(pos.getX(), pos.getY(), pos.getZ());
            INDEX.markDirty(pos.getX(), pos.getZ());
        }
    }

    public static void onClientTick(@Nullable ClientLevel level) {
        if (level == null || level != trackedLevel) {
            if (trackedLevel != null) {
                reset();
            }
            return;
        }

        INDEX.retainLoadedChunks(CHUNK_VALIDATOR);
    }

    public static int columnCount() {
        return INDEX.size();
    }

    /**
     * @return the column at {@code index}; its spans may be stale until {@link #refresh(CloudColumn)}
     */
    public static CloudColumn column(int index) {
        return INDEX.get(index);
    }

    public static void refresh(CloudColumn column) {
        column.rebuildIfDirty(SURFACE_FINDER);
    }

    @Nullable
    public static CloudColumn columnAt(int x, int z) {
        CloudColumn column = INDEX.columnAt(x, z);
        if (column != null) {
            column.rebuildIfDirty(SURFACE_FINDER);
        }
        return column;
    }

    public static boolean hasPrecipitationColumn(int x, int z) {
        CloudColumn column = columnAt(x, z);
        return column != null && column.spanCount() > 0;
    }

    public static boolean hasColumnWithin(double centerX, double centerZ, double radius) {
        double radiusSqr = radius * radius;
        for (int i = 0; i < INDEX.size(); i++) {
            if (horizontalDistanceSqr(INDEX.get(i), centerX, centerZ) < radiusSqr) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasPrecipitationNear(BlockPos center, int horizontalRadius, int verticalRadius) {
        int minY = center.getY() - verticalRadius;
        int maxY = center.getY() + verticalRadius;

        for (int i = 0; i < INDEX.size(); i++) {
            CloudColumn column = INDEX.get(i);
            if (Math.abs(column.x - center.getX()) > horizontalRadius || Math.abs(column.z - center.getZ()) > horizontalRadius) {
                continue;
            }

            column.rebuildIfDirty(SURFACE_FINDER);
            if (column.hasSpanIntersecting(minY, maxY)) {
                return true;
            }
        }
        return false;
    }

    public static double horizontalDistanceSqr(CloudColumn column, double x, double z) {
        double dx = column.x + 0.5D - x;
        double dz = column.z + 0.5D - z;
        return dx * dx + dz * dz;
    }

    private static int findSurfaceY(int x, int cloudY, int z) {
        ClientLevel level = trackedLevel;
        if (level == null) {
            return CloudColumn.NO_SPAN;
        }

        int surfaceY = WeatherCloudBlock.findPrecipitationSurfaceY(level, SURFACE_CURSOR, x, cloudY, z);
        return surfaceY == WeatherCloudBlock.NO_PRECIPITATION_SURFACE ? CloudColumn.NO_SPAN : surfaceY;
    }

    private static boolean isStillLoaded(int chunkX, int chunkZ, Object chunk) {
        ClientLevel level = trackedLevel;
        return level != null && level.getChunkSource().getChunkNow(chunkX, chunkZ) == chunk;
    }

    private static void track(ClientLevel level) {
        if (level != trackedLevel) {
            reset();
            trackedLevel = level;
        }
    }
}
