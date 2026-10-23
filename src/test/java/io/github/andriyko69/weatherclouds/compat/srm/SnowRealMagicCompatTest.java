package io.github.andriyko69.weatherclouds.compat.srm;

import io.github.andriyko69.weatherclouds.WeatherClouds;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class SnowRealMagicCompatTest {
    @Test
    void srmIsAbsentFromTestClasspath() {
        // Guards the remaining tests: they only prove isolation if SRM really cannot be loaded here.
        assertThrows(ClassNotFoundException.class, () -> Class.forName("snownee.snow.Hooks"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("snownee.snow.block.SnowVariant"));
    }

    @Test
    void facadeLoadsAndInitializesWithoutSrm() {
        assertDoesNotThrow(() -> Class.forName(
                "io.github.andriyko69.weatherclouds.compat.srm.SnowRealMagicCompat",
                true,
                getClass().getClassLoader()));
    }

    @Test
    void reportsNotLoadedWhenAbsentFromModList() {
        assertNotNull(ModList.get(), "tests must run in the FML unit-test environment");
        assertTrue(ModList.get().isLoaded(WeatherClouds.MOD_ID));
        assertFalse(SnowRealMagicCompat.isLoaded());
    }

    @Test
    void absentSrmDefersToVanillaSnowLogic() {
        assertFalse(SnowAccumulationHandler.NOOP.tryAccumulateSnow(null, null, null));
        assertFalse(SnowRealMagicCompat.tryAccumulateSnow(null, null, null));
    }

    @Test
    void resolvesToNoopWhenSrmIsAbsent() {
        assertSame(SnowAccumulationHandler.NOOP, SnowRealMagicCompat.snowAccumulation());
    }

    @Test
    void absentSrmNeverInvokesSrmFactory() {
        SnowAccumulationHandler handler = SnowRealMagicCompat.select(false, () -> fail("SRM factory must not run"));
        assertSame(SnowAccumulationHandler.NOOP, handler);
    }
}
