package io.github.andriyko69.weatherclouds.compat.srm;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

import java.util.function.Supplier;

// Must never reference snownee.* types: this class loads whether or not Snow! Real Magic! is installed.
public final class SnowRealMagicCompat {
    public static final String MOD_ID = "snowrealmagic";

    private static volatile SnowAccumulationHandler handler;

    private SnowRealMagicCompat() {
    }

    public static boolean isLoaded() {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(MOD_ID);
    }

    public static boolean tryAccumulateSnow(ServerLevel level, BlockPos surfacePos, BlockPos precipitationPos) {
        SnowAccumulationHandler current = snowAccumulation();
        try {
            return current.tryAccumulateSnow(level, surfacePos, precipitationPos);
        } catch (LinkageError e) {
            // An SRM release changed the API we compiled against; degrade to vanilla snow instead of crashing the server.
            disable(e);
            return false;
        }
    }

    public static SnowAccumulationHandler snowAccumulation() {
        SnowAccumulationHandler current = handler;
        if (current == null) {
            try {
                current = select(isLoaded(), () -> new SnowRealMagicSnowAccumulation());
            } catch (LinkageError e) {
                disable(e);
                return SnowAccumulationHandler.NOOP;
            }
            handler = current;
        }
        return current;
    }

    // The factory is a lambda so SnowRealMagicSnowAccumulation (and SRM) is only loaded when it is invoked.
    static SnowAccumulationHandler select(boolean srmLoaded, Supplier<SnowAccumulationHandler> srmFactory) {
        return srmLoaded ? srmFactory.get() : SnowAccumulationHandler.NOOP;
    }

    private static void disable(LinkageError e) {
        handler = SnowAccumulationHandler.NOOP;
        LogUtils.getLogger().warn("Snow! Real Magic! compat disabled, falling back to vanilla snow accumulation", e);
    }
}
