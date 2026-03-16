package io.github.andriyko69.weatherclouds.registry;

import io.github.andriyko69.weatherclouds.WeatherClouds;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WeatherClouds.MOD_ID);

    public static final DeferredItem<Item> RAIN_CLOUD = ITEMS.register(
            "rain_cloud",
            () -> new BlockItem(ModBlocks.RAIN_CLOUD.get(), new Item.Properties())
    );

    public static final DeferredItem<Item> SNOW_CLOUD = ITEMS.register(
            "snow_cloud",
            () -> new BlockItem(ModBlocks.SNOW_CLOUD.get(), new Item.Properties())
    );

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}