package io.github.andriyko69.weatherclouds.datagen.provider;

import io.github.andriyko69.weatherclouds.WeatherClouds;
import io.github.andriyko69.weatherclouds.registry.ModBlocks;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class ModLanguageProvider extends LanguageProvider {
    public ModLanguageProvider(PackOutput output) {
        super(output, WeatherClouds.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add(ModBlocks.RAIN_CLOUD.get(), "Rain Cloud");
        add(ModBlocks.SNOW_CLOUD.get(), "Snow Cloud");

        add("itemGroup." + WeatherClouds.MOD_ID, "Weather Clouds");
    }
}