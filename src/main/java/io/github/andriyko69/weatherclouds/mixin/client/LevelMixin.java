package io.github.andriyko69.weatherclouds.mixin.client;

import io.github.andriyko69.weatherclouds.client.weather.ClientCloudTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's {@code markAndNotifyBlock} runs after every committed client {@code setBlock}, whatever the flags.
 * {@code sendBlockUpdated} is skipped for flag 4 (piston moves) and server-verified states bypass
 * {@code ClientLevel#setBlock}, so neither is a complete hook. Applied on the physical client only; on an
 * integrated server the same method also runs for {@code ServerLevel}, which is filtered out.
 */
@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(method = "markAndNotifyBlock", at = @At("HEAD"))
    private void wc$trackCloudColumns(
            BlockPos pos,
            LevelChunk chunk,
            BlockState oldState,
            BlockState newState,
            int flags,
            int recursionLeft,
            CallbackInfo ci
    ) {
        if ((Object) this instanceof ClientLevel level && chunk != null) {
            ClientCloudTracker.onBlockChanged(level, chunk, pos, level.getBlockState(pos));
        }
    }
}
