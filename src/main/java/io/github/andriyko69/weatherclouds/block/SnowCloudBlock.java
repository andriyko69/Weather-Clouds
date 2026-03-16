package io.github.andriyko69.weatherclouds.block;

import com.mojang.serialization.MapCodec;
import io.github.andriyko69.weatherclouds.util.WeatherCloudType;
import org.jetbrains.annotations.NotNull;

public class SnowCloudBlock extends WeatherCloudBlock {
    public static final MapCodec<SnowCloudBlock> CODEC = simpleCodec(SnowCloudBlock::new);

    public SnowCloudBlock(Properties properties) {
        super(properties);
    }

    @Override
    public WeatherCloudType getCloudType() {
        return WeatherCloudType.SNOW;
    }

    @Override
    protected @NotNull MapCodec<? extends WeatherCloudBlock> codec() {
        return CODEC;
    }
}