package io.github.andriyko69.weatherclouds.datagen;

import io.github.andriyko69.weatherclouds.WeatherClouds;
import io.github.andriyko69.weatherclouds.datagen.provider.ModBlockStateProvider;
import io.github.andriyko69.weatherclouds.datagen.provider.ModLanguageProvider;
import io.github.andriyko69.weatherclouds.datagen.provider.ModLootTableProvider;
import io.github.andriyko69.weatherclouds.datagen.provider.ModRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = WeatherClouds.MOD_ID)
public class ModDataGenerators {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        PackOutput output = event.getGenerator().getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        if (event.includeClient()) {
            event.getGenerator().addProvider(true, new ModBlockStateProvider(output, event.getExistingFileHelper()));
            event.getGenerator().addProvider(true, new ModLanguageProvider(output));
        }

        if (event.includeServer()) {
            event.getGenerator().addProvider(true, new ModRecipeProvider(output, lookupProvider));
            event.getGenerator().addProvider(true, new ModLootTableProvider(output, lookupProvider));
        }
    }
}