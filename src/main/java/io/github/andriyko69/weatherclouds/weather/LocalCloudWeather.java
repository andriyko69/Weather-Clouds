package io.github.andriyko69.weatherclouds.weather;

import io.github.andriyko69.weatherclouds.block.WeatherCloudBlock;
import io.github.andriyko69.weatherclouds.util.WeatherCloudType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class LocalCloudWeather {
    private static final int MAX_SCAN_DOWN = 128;

    private LocalCloudWeather() {
    }

    @Nullable
    public static LocalPrecipitation getLocalPrecipitation(LevelReader level, BlockPos pos) {
        int startY = Math.min(level.getMaxBuildHeight() - 1, pos.getY() + MAX_SCAN_DOWN);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(pos.getX(), startY, pos.getZ());

        for (int i = 0; i < MAX_SCAN_DOWN * 2 && cursor.getY() >= level.getMinBuildHeight(); i++) {
            BlockState state = level.getBlockState(cursor);

            if (state.getBlock() instanceof WeatherCloudBlock cloudBlock) {
                WeatherCloudBlock.PrecipitationTarget target = WeatherCloudBlock.findPrecipitationTarget(level, cursor);
                if (target != null
                        && target.precipitationPos().getX() == pos.getX()
                        && target.precipitationPos().getZ() == pos.getZ()) {
                    return new LocalPrecipitation(
                            cloudBlock.getCloudType(),
                            cursor.immutable(),
                            target.surfacePos(),
                            target.precipitationPos()
                    );
                }
            }

            cursor.move(Direction.DOWN);
        }

        return null;
    }

    @Nullable
    public static WeatherCloudType getLocalPrecipitationAt(LevelReader level, BlockPos pos) {
        LocalPrecipitation precipitation = getLocalPrecipitation(level, pos);
        return precipitation == null ? null : precipitation.type();
    }

    public static boolean hasLocalPrecipitationAround(LevelReader level, BlockPos center, int horizontalRadius, int verticalRadius) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int topY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + verticalRadius);

        for (int x = center.getX() - horizontalRadius; x <= center.getX() + horizontalRadius; x++) {
            for (int z = center.getZ() - horizontalRadius; z <= center.getZ() + horizontalRadius; z++) {
                cursor.set(x, topY, z);
                if (getLocalPrecipitation(level, cursor) != null) {
                    return true;
                }
            }
        }

        return false;
    }

    public record LocalPrecipitation(
            WeatherCloudType type,
            BlockPos cloudPos,
            BlockPos surfacePos,
            BlockPos precipitationPos
    ) {
    }
}