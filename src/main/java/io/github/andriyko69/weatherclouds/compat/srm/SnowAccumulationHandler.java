package io.github.andriyko69.weatherclouds.compat.srm;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public interface SnowAccumulationHandler {
    SnowAccumulationHandler NOOP = (level, surfacePos, precipitationPos) -> false;

    /**
     * Server thread only.
     *
     * @return true when this handler owns the accumulation decision (even if it placed nothing),
     * false to let the vanilla snow logic run
     */
    boolean tryAccumulateSnow(ServerLevel level, BlockPos surfacePos, BlockPos precipitationPos);
}
