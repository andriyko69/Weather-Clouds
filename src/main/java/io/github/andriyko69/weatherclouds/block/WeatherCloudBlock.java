package io.github.andriyko69.weatherclouds.block;

import com.mojang.serialization.MapCodec;
import io.github.andriyko69.weatherclouds.util.WeatherCloudType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class WeatherCloudBlock extends Block {
    public static final int NO_PRECIPITATION_SURFACE = Integer.MIN_VALUE;
    private static final int MAX_PRECIPITATION_SCAN_DEPTH = 128;

    protected WeatherCloudBlock(Properties properties) {
        super(properties);
    }

    public abstract WeatherCloudType getCloudType();

    @Override
    protected abstract @NotNull MapCodec<? extends WeatherCloudBlock> codec();

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected boolean isRandomlyTicking(@NotNull BlockState state) {
        return true;
    }

    @Override
    public void randomTick(
            @NotNull BlockState state,
            @NotNull ServerLevel level,
            @NotNull BlockPos pos,
            @NotNull RandomSource random
    ) {
        PrecipitationTarget target = findPrecipitationTarget(level, pos);
        if (target == null) {
            return;
        }

        BlockState surfaceState = level.getBlockState(target.surfacePos());
        surfaceState.getBlock().handlePrecipitation(
                surfaceState,
                level,
                target.surfacePos(),
                getPrecipitationType()
        );

        if (this.getCloudType() == WeatherCloudType.SNOW) {
            tryAccumulateSnow(level, target.precipitationPos());
        }
    }

    @Override
    protected boolean skipRendering(@NotNull BlockState state, BlockState adjacentBlockState, @NotNull Direction side) {
        return adjacentBlockState.getBlock() instanceof WeatherCloudBlock
                || super.skipRendering(state, adjacentBlockState, side);
    }

    // Full collision cubes default to blocking skylight and shading neighbours like stone; clouds should
    // behave like glass so nothing beneath them falls into shadow.
    @Override
    protected boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean useShapeForLightOcclusion(@NotNull BlockState state) {
        return false;
    }

    protected Biome.Precipitation getPrecipitationType() {
        return this.getCloudType() == WeatherCloudType.RAIN
                ? Biome.Precipitation.RAIN
                : Biome.Precipitation.SNOW;
    }

    private void tryAccumulateSnow(ServerLevel level, BlockPos precipitationPos) {
        if (precipitationPos.getY() < level.getMinBuildHeight() || precipitationPos.getY() >= level.getMaxBuildHeight()) {
            return;
        }

        BlockState currentState = level.getBlockState(precipitationPos);

        if (currentState.is(Blocks.SNOW)) {
            int layers = currentState.getValue(SnowLayerBlock.LAYERS);
            if (layers < 8) {
                BlockState increased = currentState.setValue(SnowLayerBlock.LAYERS, layers + 1);
                if (increased.canSurvive(level, precipitationPos)) {
                    level.setBlockAndUpdate(precipitationPos, increased);
                }
            }
            return;
        }

        BlockState snowState = Blocks.SNOW.defaultBlockState();
        if (currentState.canBeReplaced() && snowState.canSurvive(level, precipitationPos)) {
            level.setBlockAndUpdate(precipitationPos, snowState);
        }
    }

    @Nullable
    public static PrecipitationTarget findPrecipitationTarget(LevelReader level, BlockPos cloudPos) {
        int surfaceY = findPrecipitationSurfaceY(level, cloudPos.mutable(), cloudPos.getX(), cloudPos.getY(), cloudPos.getZ());
        if (surfaceY == NO_PRECIPITATION_SURFACE) {
            return null;
        }

        BlockPos surfacePos = new BlockPos(cloudPos.getX(), surfaceY, cloudPos.getZ());
        return new PrecipitationTarget(surfacePos, surfacePos.above());
    }

    /**
     * Allocation-free variant of {@link #findPrecipitationTarget} shared by server ticks and client rendering.
     *
     * @return the surface Y, or {@link #NO_PRECIPITATION_SURFACE} when a cloud sits directly below or nothing is hit
     */
    public static int findPrecipitationSurfaceY(LevelReader level, BlockPos.MutableBlockPos cursor, int x, int cloudY, int z) {
        cursor.set(x, cloudY - 1, z);

        if (level.getBlockState(cursor).getBlock() instanceof WeatherCloudBlock) {
            return NO_PRECIPITATION_SURFACE;
        }

        for (int i = 0; i < MAX_PRECIPITATION_SCAN_DEPTH && cursor.getY() >= level.getMinBuildHeight(); i++) {
            BlockState state = level.getBlockState(cursor);

            if (isPrecipitationSurface(level, cursor, state)) {
                return cursor.getY();
            }

            cursor.move(Direction.DOWN);
        }

        return NO_PRECIPITATION_SURFACE;
    }

    private static boolean isPrecipitationSurface(LevelReader level, BlockPos pos, BlockState state) {
        if (!state.getFluidState().isEmpty()) {
            return true;
        }

        if (state.isAir()) {
            return false;
        }

        if (state.isFaceSturdy(level, pos, Direction.UP)) {
            return true;
        }

        VoxelShape shape = state.getCollisionShape(level, pos);
        return !shape.isEmpty() && shape.max(Direction.Axis.Y) > 0.0D;
    }

    public record PrecipitationTarget(BlockPos surfacePos, BlockPos precipitationPos) {
    }
}
