package io.github.andriyko69.weatherclouds.block;

import com.mojang.serialization.MapCodec;
import io.github.andriyko69.weatherclouds.util.WeatherCloudType;
import org.jetbrains.annotations.NotNull;

public class RainCloudBlock extends WeatherCloudBlock {
    public static final MapCodec<RainCloudBlock> CODEC = simpleCodec(RainCloudBlock::new);

    public RainCloudBlock(Properties properties) {
        super(properties);
    }

    @Override
    public WeatherCloudType getCloudType() {
        return WeatherCloudType.RAIN;
    }

    @Override
    protected @NotNull MapCodec<? extends WeatherCloudBlock> codec() {
        return CODEC;
    }
}