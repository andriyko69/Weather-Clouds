package io.github.andriyko69.weatherclouds.registry;

import io.github.andriyko69.weatherclouds.WeatherClouds;
import io.github.andriyko69.weatherclouds.block.RainCloudBlock;
import io.github.andriyko69.weatherclouds.block.SnowCloudBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WeatherClouds.MOD_ID);

    public static final DeferredBlock<Block> RAIN_CLOUD = BLOCKS.register(
            "rain_cloud",
            () -> new RainCloudBlock(commonProperties())
    );

    public static final DeferredBlock<Block> SNOW_CLOUD = BLOCKS.register(
            "snow_cloud",
            () -> new SnowCloudBlock(commonProperties())
    );

    private static BlockBehaviour.Properties commonProperties() {
        return BlockBehaviour.Properties.of()
                .strength(0.2F)
                .sound(SoundType.WOOL)
                .noOcclusion()
                .randomTicks()
                .isViewBlocking((state, level, pos) -> false);
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}