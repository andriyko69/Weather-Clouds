package io.github.andriyko69.weatherclouds.datagen.provider;

import io.github.andriyko69.weatherclouds.WeatherClouds;
import io.github.andriyko69.weatherclouds.registry.ModBlocks;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModBlockStateProvider extends BlockStateProvider {
    public ModBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, WeatherClouds.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        simpleBlockWithItem(
                ModBlocks.RAIN_CLOUD.get(),
                models().getExistingFile(modLoc("block/rain_cloud"))
        );

        simpleBlockWithItem(
                ModBlocks.SNOW_CLOUD.get(),
                models().getExistingFile(modLoc("block/snow_cloud"))
        );
    }
}