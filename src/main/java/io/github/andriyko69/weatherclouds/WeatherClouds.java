package io.github.andriyko69.weatherclouds;

import io.github.andriyko69.weatherclouds.registry.ModBlocks;
import io.github.andriyko69.weatherclouds.registry.ModCreativeTabs;
import io.github.andriyko69.weatherclouds.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(WeatherClouds.MOD_ID)
public class WeatherClouds {
    public static final String MOD_ID = "weatherclouds";

    public WeatherClouds(IEventBus modEventBus) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
    }
}
