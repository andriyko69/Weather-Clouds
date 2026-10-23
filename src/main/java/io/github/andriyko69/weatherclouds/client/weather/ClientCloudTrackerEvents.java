package io.github.andriyko69.weatherclouds.client.weather;

import io.github.andriyko69.weatherclouds.WeatherClouds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

// Chunk and level events also fire for the integrated server on its own thread; only client levels are tracked.
@EventBusSubscriber(modid = WeatherClouds.MOD_ID, value = Dist.CLIENT)
public final class ClientCloudTrackerEvents {
    private ClientCloudTrackerEvents() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel level && event.getChunk() instanceof LevelChunk chunk) {
            ClientCloudTracker.onChunkLoad(level, chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level) {
            ClientCloudTracker.onChunkUnload(level, event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientCloudTracker.onClientTick(Minecraft.getInstance().level);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level) {
            ClientCloudTracker.onLevelUnload(level);
        }
    }
}
