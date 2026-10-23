package io.github.andriyko69.weatherclouds.compat.srm;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import snownee.snow.CoreModule;
import snownee.snow.Hooks;
import snownee.snow.SnowCommonConfig;
import snownee.snow.block.SnowVariant;
import snownee.snow.util.CommonProxy;

/**
 * Mirrors SRM's own weather accumulation ({@code WorldTickHandler.doSnow} / {@code Hooks.randomTick}) minus
 * its {@code isRaining}, biome temperature and season gates, which do not apply to artificial cloud weather.
 * SRM mixes {@link SnowVariant} into vanilla {@link SnowLayerBlock}, so plain snow layers go through here too.
 */
final class SnowRealMagicSnowAccumulation implements SnowAccumulationHandler {
    private static final int MAX_LAYERS = 8;

    @Override
    public boolean tryAccumulateSnow(ServerLevel level, BlockPos surfacePos, BlockPos precipitationPos) {
        if (SnowCommonConfig.forceVanillaIceSnowLogic || SnowCommonConfig.restoreOriginalBlocks) {
            return false;
        }

        int maxLayers = Math.min(SnowCommonConfig.snowAccumulationMaxLayers, MAX_LAYERS);
        if (maxLayers <= 0) {
            return true;
        }

        // Snow piles of 2+ layers and snowy slabs/stairs have collision, so the surface scan stops on them.
        if (growLayer(level, surfacePos, maxLayers) || growLayer(level, precipitationPos, maxLayers)) {
            return true;
        }

        if (level.getBrightness(LightLayer.BLOCK, precipitationPos) > SnowCommonConfig.snowSpawnMaxLightLevel) {
            return true;
        }

        BlockState surfaceState = level.getBlockState(surfacePos);
        BlockState precipitationState = level.getBlockState(precipitationPos);
        boolean mayStackOnSnow = SnowCommonConfig.snowAccumulationMaxLayers >= 9
                || !(surfaceState.getBlock() instanceof SnowLayerBlock);

        if (mayStackOnSnow && !(precipitationState.getBlock() instanceof SnowVariant)
                && convert(level, precipitationPos, precipitationState)) {
            return true;
        }

        // Fences, walls and full slabs cannot hold snow on top, so SRM snows them in place instead.
        if (!(surfaceState.getBlock() instanceof SnowVariant)) {
            convert(level, surfacePos, surfaceState);
        }
        return true;
    }

    private static boolean convert(ServerLevel level, BlockPos pos, BlockState state) {
        return Hooks.convert(level, pos, state, 1, Block.UPDATE_ALL, SnowCommonConfig.placeSnowOnBlockNaturally);
    }

    private static boolean growLayer(ServerLevel level, BlockPos pos, int maxLayers) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SnowVariant)) {
            return false;
        }

        IntegerProperty layersProperty;
        if (state.hasProperty(SnowLayerBlock.LAYERS)) {
            layersProperty = SnowLayerBlock.LAYERS;
        } else if (state.hasProperty(SnowVariant.OPTIONAL_LAYERS)) {
            layersProperty = SnowVariant.OPTIONAL_LAYERS;
        } else {
            return false;
        }

        int layers = state.getValue(layersProperty);
        if (layers >= maxLayers || level.getBlockState(pos.below()).is(CoreModule.CANNOT_ACCUMULATE_ON)) {
            return false;
        }
        if (layers == 0 && !Hooks.canSnowSurvive(level, pos)) {
            return false;
        }
        // SRM melts snow this bright, so growing it would just fight SRM's own melt ticks.
        if (!SnowCommonConfig.snowNeverMelt && CommonProxy.blockLightEnoughToMelt(level, pos)) {
            return false;
        }

        BlockState grown = state.setValue(layersProperty, layers + 1);
        level.setBlockAndUpdate(pos, grown);
        Block.pushEntitiesUp(state, grown, level, pos);
        return true;
    }
}
