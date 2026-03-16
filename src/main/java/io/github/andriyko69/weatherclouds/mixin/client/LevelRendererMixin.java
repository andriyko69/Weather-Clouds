package io.github.andriyko69.weatherclouds.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import io.github.andriyko69.weatherclouds.util.WeatherCloudType;
import io.github.andriyko69.weatherclouds.weather.LocalCloudWeather;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biome.Precipitation;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private ClientLevel level;

    @Shadow
    private int ticks;

    @Shadow
    @Final
    private float[] rainSizeX;

    @Shadow
    @Final
    private float[] rainSizeZ;

    @Shadow
    @Final
    private static net.minecraft.resources.ResourceLocation RAIN_LOCATION;

    @Shadow
    @Final
    private static net.minecraft.resources.ResourceLocation SNOW_LOCATION;

    @Shadow
    public static int getLightColor(BlockAndTintGetter level, BlockPos pos) {
        throw new AssertionError();
    }

    @Unique
    @Nullable
    private WeatherCloudType wc$getLocalWeatherAt(LevelReader level, BlockPos pos) {
        return LocalCloudWeather.getLocalPrecipitationAt(level, pos);
    }

    @Unique
    private boolean wc$globalWeatherActive() {
        ClientLevel level = this.minecraft.level;
        return level != null && level.getRainLevel(this.minecraft.getTimer().getGameTimeDeltaPartialTick(false)) > 0.0F;
    }

    @Unique
    private boolean wc$hasLocalWeatherAround(BlockPos cameraPos) {
        ClientLevel level = this.minecraft.level;
        return level != null && LocalCloudWeather.hasLocalPrecipitationAround(level, cameraPos, 16, 32);
    }

    /**
     * @author Andriyko69
     * @reason Render local cloud weather with vanilla precipitation rendering.
     */
    @Overwrite
    private void renderSnowAndRain(LightTexture lightTexture, float partialTick, double camX, double camY, double camZ) {
        if (this.level.effects().renderSnowAndRain(this.level, this.ticks, partialTick, lightTexture, camX, camY, camZ)) {
            return;
        }

        float globalRainStrength = this.minecraft.level.getRainLevel(partialTick);
        boolean globalWeather = globalRainStrength > 0.0F;

        if (!globalWeather) {
            BlockPos cameraPos = BlockPos.containing(camX, camY, camZ);
            if (!this.wc$hasLocalWeatherAround(cameraPos)) {
                return;
            }
        }

        lightTexture.turnOnLightLayer();
        Level level = this.minecraft.level;
        int i = Mth.floor(camX);
        int j = Mth.floor(camY);
        int k = Mth.floor(camZ);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = null;

        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();

        int l = 5;
        if (Minecraft.useFancyGraphics()) {
            l = 10;
        }

        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        int i1 = -1;
        float f1 = (float) this.ticks + partialTick;
        RenderSystem.setShader(GameRenderer::getParticleShader);
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

        for (int j1 = k - l; j1 <= k + l; ++j1) {
            for (int k1 = i - l; k1 <= i + l; ++k1) {
                int l1 = (j1 - k + 16) * 32 + k1 - i + 16;
                double d0 = (double) this.rainSizeX[l1] * 0.5D;
                double d1 = (double) this.rainSizeZ[l1] * 0.5D;

                blockpos$mutableblockpos.set(k1, camY, j1);
                Biome biome = level.getBiome(blockpos$mutableblockpos).value();

                LocalCloudWeather.LocalPrecipitation local = LocalCloudWeather.getLocalPrecipitation(
                        level,
                        new BlockPos(k1, level.getMaxBuildHeight() - 1, j1)
                );

                boolean columnHasWeather;
                if (local != null) {
                    columnHasWeather = true;
                } else if (globalWeather) {
                    columnHasWeather = biome.hasPrecipitation();
                } else {
                    columnHasWeather = false;
                }

                if (!columnHasWeather) {
                    continue;
                }

                float columnStrength;
                if (local != null) {
                    columnStrength = 1.0F;
                } else {
                    columnStrength = globalRainStrength;
                }

                int i2;
                int j2;
                int k2;
                int l2;

                Biome.Precipitation biome$precipitation;
                if (local != null) {
                    biome$precipitation = local.type() == WeatherCloudType.RAIN
                            ? Precipitation.RAIN
                            : Precipitation.SNOW;
                } else {
                    blockpos$mutableblockpos.set(k1, j - l, j1);
                    biome$precipitation = biome.getPrecipitationAt(blockpos$mutableblockpos);
                }

                if (biome$precipitation == Precipitation.NONE) {
                    continue;
                }

                if (local != null) {
                    i2 = local.surfacePos().getY() + 1;
                    j2 = i2;
                    k2 = local.cloudPos().getY();
                    l2 = Mth.clamp(j, j2, k2);
                } else {
                    i2 = level.getHeight(Types.MOTION_BLOCKING, k1, j1);
                    j2 = j - l;
                    k2 = j + l;

                    if (j2 < i2) {
                        j2 = i2;
                    }

                    if (k2 < i2) {
                        k2 = i2;
                    }

                    l2 = Math.max(i2, j);
                }

                if (j2 >= k2) {
                    continue;
                }

                RandomSource randomsource = RandomSource.create(
                        (long) k1 * k1 * 3121L + k1 * 45238971L ^ (long) j1 * j1 * 418711L + j1 * 13761L
                );
                blockpos$mutableblockpos.set(k1, j2, j1);

                if (biome$precipitation == Precipitation.RAIN) {
                    if (i1 != 0) {
                        if (i1 >= 0) {
                            BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
                        }

                        i1 = 0;
                        RenderSystem.setShaderTexture(0, RAIN_LOCATION);
                        bufferbuilder = tesselator.begin(Mode.QUADS, DefaultVertexFormat.PARTICLE);
                    }

                    int i3 = this.ticks & 131071;
                    int j3 = k1 * k1 * 3121 + k1 * 45238971 + j1 * j1 * 418711 + j1 * 13761 & 255;
                    float f2 = 3.0F + randomsource.nextFloat();
                    float f3 = -((float) (i3 + j3) + partialTick) / 32.0F * f2;
                    float f4 = f3 % 32.0F;
                    double d2 = (double) k1 + 0.5D - camX;
                    double d3 = (double) j1 + 0.5D - camZ;
                    float f6 = (float) Math.sqrt(d2 * d2 + d3 * d3) / (float) l;
                    float f7 = ((1.0F - f6 * f6) * 0.5F + 0.5F) * columnStrength;

                    blockpos$mutableblockpos.set(k1, l2, j1);
                    int k3 = getLightColor(level, blockpos$mutableblockpos);

                    bufferbuilder.addVertex((float) ((double) k1 - camX - d0 + 0.5D), (float) ((double) k2 - camY), (float) ((double) j1 - camZ - d1 + 0.5D))
                            .setUv(0.0F, (float) j2 * 0.25F + f4)
                            .setColor(1.0F, 1.0F, 1.0F, f7)
                            .setLight(k3);
                    bufferbuilder.addVertex((float) ((double) k1 - camX + d0 + 0.5D), (float) ((double) k2 - camY), (float) ((double) j1 - camZ + d1 + 0.5D))
                            .setUv(1.0F, (float) j2 * 0.25F + f4)
                            .setColor(1.0F, 1.0F, 1.0F, f7)
                            .setLight(k3);
                    bufferbuilder.addVertex((float) ((double) k1 - camX + d0 + 0.5D), (float) ((double) j2 - camY), (float) ((double) j1 - camZ + d1 + 0.5D))
                            .setUv(1.0F, (float) k2 * 0.25F + f4)
                            .setColor(1.0F, 1.0F, 1.0F, f7)
                            .setLight(k3);
                    bufferbuilder.addVertex((float) ((double) k1 - camX - d0 + 0.5D), (float) ((double) j2 - camY), (float) ((double) j1 - camZ - d1 + 0.5D))
                            .setUv(0.0F, (float) k2 * 0.25F + f4)
                            .setColor(1.0F, 1.0F, 1.0F, f7)
                            .setLight(k3);
                } else if (biome$precipitation == Precipitation.SNOW) {
                    if (i1 != 1) {
                        if (i1 >= 0) {
                            BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
                        }

                        i1 = 1;
                        RenderSystem.setShaderTexture(0, SNOW_LOCATION);
                        bufferbuilder = tesselator.begin(Mode.QUADS, DefaultVertexFormat.PARTICLE);
                    }

                    float f8 = -((float) (this.ticks & 511) + partialTick) / 512.0F;
                    float f9 = (float) (randomsource.nextDouble() + (double) f1 * 0.01D * (double) ((float) randomsource.nextGaussian()));
                    float f10 = (float) (randomsource.nextDouble() + (double) (f1 * (float) randomsource.nextGaussian()) * 0.001D);
                    double d4 = (double) k1 + 0.5D - camX;
                    double d5 = (double) j1 + 0.5D - camZ;
                    float f11 = (float) Math.sqrt(d4 * d4 + d5 * d5) / (float) l;
                    float f5 = ((1.0F - f11 * f11) * 0.3F + 0.5F) * columnStrength;

                    blockpos$mutableblockpos.set(k1, l2, j1);
                    int j4 = getLightColor(level, blockpos$mutableblockpos);
                    int k4 = j4 >> 16 & 65535;
                    int l4 = j4 & 65535;
                    int l3 = (k4 * 3 + 240) / 4;
                    int i4 = (l4 * 3 + 240) / 4;

                    bufferbuilder.addVertex((float) ((double) k1 - camX - d0 + 0.5D), (float) ((double) k2 - camY), (float) ((double) j1 - camZ - d1 + 0.5D))
                            .setUv(0.0F + f9, (float) j2 * 0.25F + f8 + f10)
                            .setColor(1.0F, 1.0F, 1.0F, f5)
                            .setUv2(i4, l3);
                    bufferbuilder.addVertex((float) ((double) k1 - camX + d0 + 0.5D), (float) ((double) k2 - camY), (float) ((double) j1 - camZ + d1 + 0.5D))
                            .setUv(1.0F + f9, (float) j2 * 0.25F + f8 + f10)
                            .setColor(1.0F, 1.0F, 1.0F, f5)
                            .setUv2(i4, l3);
                    bufferbuilder.addVertex((float) ((double) k1 - camX + d0 + 0.5D), (float) ((double) j2 - camY), (float) ((double) j1 - camZ + d1 + 0.5D))
                            .setUv(1.0F + f9, (float) k2 * 0.25F + f8 + f10)
                            .setColor(1.0F, 1.0F, 1.0F, f5)
                            .setUv2(i4, l3);
                    bufferbuilder.addVertex((float) ((double) k1 - camX - d0 + 0.5D), (float) ((double) j2 - camY), (float) ((double) j1 - camZ - d1 + 0.5D))
                            .setUv(0.0F + f9, (float) k2 * 0.25F + f8 + f10)
                            .setColor(1.0F, 1.0F, 1.0F, f5)
                            .setUv2(i4, l3);
                }
            }
        }

        if (i1 >= 0) {
            BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        lightTexture.turnOffLightLayer();
    }

    @WrapOperation(
            method = "tickRain",
            at = @org.spongepowered.asm.mixin.injection.At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"
            )
    )
    private float wc$allowRainTickForLocalClouds(
            ClientLevel level,
            float partialTick,
            Operation<Float> original,
            Camera camera
    ) {
        float vanillaRainLevel = original.call(level, partialTick);
        if (vanillaRainLevel > 0.0F) {
            return vanillaRainLevel;
        }

        BlockPos cameraPos = BlockPos.containing(camera.getPosition());
        return this.wc$hasLocalWeatherAround(cameraPos) ? 1.0F : 0.0F;
    }

    @WrapOperation(
            method = "tickRain",
            at = @org.spongepowered.asm.mixin.injection.At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"
            )
    )
    private Biome.Precipitation wc$replaceTickedPrecipitationType(
            Biome biome,
            BlockPos pos,
            Operation<Biome.Precipitation> original
    ) {
        ClientLevel level = this.minecraft.level;
        if (level == null) {
            return original.call(biome, pos);
        }

        WeatherCloudType local = this.wc$getLocalWeatherAt(level, pos);
        if (local == WeatherCloudType.RAIN) {
            return Biome.Precipitation.RAIN;
        }
        if (local == WeatherCloudType.SNOW) {
            return Biome.Precipitation.SNOW;
        }

        if (!this.wc$globalWeatherActive()) {
            return Biome.Precipitation.NONE;
        }

        return original.call(biome, pos);
    }

    @WrapOperation(
            method = "tickRain",
            at = @org.spongepowered.asm.mixin.injection.At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelReader;getHeightmapPos(Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"
            )
    )
    private BlockPos wc$useLocalWeatherHeightInTickRain(
            LevelReader instance,
            Types heightmapType,
            BlockPos pos,
            Operation<BlockPos> original
    ) {
        BlockPos vanilla = original.call(instance, heightmapType, pos);

        if (!(instance instanceof Level level)) {
            return vanilla;
        }

        LocalCloudWeather.LocalPrecipitation precipitation = LocalCloudWeather.getLocalPrecipitation(
                level,
                new BlockPos(pos.getX(), level.getMaxBuildHeight() - 1, pos.getZ())
        );

        if (precipitation == null) {
            return vanilla;
        }

        return precipitation.precipitationPos();
    }
}