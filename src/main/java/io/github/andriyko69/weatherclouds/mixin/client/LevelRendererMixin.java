package io.github.andriyko69.weatherclouds.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import io.github.andriyko69.weatherclouds.client.weather.ClientCloudTracker;
import io.github.andriyko69.weatherclouds.client.weather.CloudColumn;
import io.github.andriyko69.weatherclouds.client.weather.PrecipitationGeometry;
import io.github.andriyko69.weatherclouds.util.WeatherCloudType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Unique private static final int WC_TICK_RAIN_HORIZONTAL_RADIUS = 16;
    @Unique private static final int WC_TICK_RAIN_VERTICAL_RADIUS = 32;

    @Shadow @Final private Minecraft minecraft;
    @Shadow private ClientLevel level;
    @Shadow private int ticks;

    @Shadow @Final private static ResourceLocation RAIN_LOCATION;
    @Shadow @Final private static ResourceLocation SNOW_LOCATION;

    @Unique private final BlockPos.MutableBlockPos wc$lightPos = new BlockPos.MutableBlockPos();
    // Reseeded per column, yielding the same sequence as vanilla's per-column RandomSource.create(seed).
    @Unique private final RandomSource wc$columnRandom = new SingleThreadedRandomSource(0L);
    @Unique private final int[] wc$sheetsPerViewBin = new int[PrecipitationGeometry.ANGULAR_BINS];

    @Shadow
    public static int getLightColor(BlockAndTintGetter level, BlockPos pos) {
        throw new AssertionError();
    }

    @Unique
    private boolean wc$globalWeatherActive() {
        ClientLevel level = this.minecraft.level;
        return level != null && level.getRainLevel(this.minecraft.getTimer().getGameTimeDeltaPartialTick(false)) > 0.0F;
    }

    @WrapOperation(
            method = "renderSnowAndRain",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/biome/Biome;hasPrecipitation()Z"
            )
    )
    private boolean wc$suppressVanillaWeatherInLocalColumns(
            Biome biome,
            Operation<Boolean> original,
            @Local BlockPos.MutableBlockPos blockpos$mutableblockpos
    ) {
        if (ClientCloudTracker.hasPrecipitationColumn(blockpos$mutableblockpos.getX(), blockpos$mutableblockpos.getZ())) {
            return false;
        }

        return original.call(biome);
    }

    /**
     * Draws one sheet per tracked cloud span within an extended radius, instead of vanilla's fixed grid around
     * the camera, so distant clouds stay visible without scanning empty columns.
     */
    @Inject(
            method = "renderSnowAndRain",
            at = @At("RETURN")
    )
    private void wc$renderLocalSnowAndRain(
            LightTexture lightTexture,
            float partialTick,
            double camX,
            double camY,
            double camZ,
            CallbackInfo ci
    ) {
        if (this.level == null || ClientCloudTracker.columnCount() == 0) {
            return;
        }

        if (this.level.effects().renderSnowAndRain(this.level, this.ticks, partialTick, lightTexture, camX, camY, camZ)) {
            return;
        }

        int radius = PrecipitationGeometry.renderRadius(
                Minecraft.useFancyGraphics(),
                this.minecraft.options.getEffectiveRenderDistance()
        );
        if (!ClientCloudTracker.hasColumnWithin(camX, camZ, radius)) {
            return;
        }

        lightTexture.turnOnLightLayer();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        RenderSystem.setShader(GameRenderer::getParticleShader);

        this.wc$countSheetsPerViewBin(radius, camX, camZ);

        // One pass per type keeps texture switches, and therefore draw calls, at two per frame.
        this.wc$renderLocalPrecipitation(WeatherCloudType.RAIN, radius, partialTick, camX, camY, camZ);
        this.wc$renderLocalPrecipitation(WeatherCloudType.SNOW, radius, partialTick, camX, camY, camZ);

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        lightTexture.turnOffLightLayer();
    }

    /**
     * Counts precipitating columns per view direction, rain and snow together since both blend over each other.
     * Also refreshes the spans of every column in range ahead of the render passes.
     */
    @Unique
    private void wc$countSheetsPerViewBin(int radius, double camX, double camZ) {
        Arrays.fill(this.wc$sheetsPerViewBin, 0);
        double radiusSqr = (double) radius * radius;

        for (int columnIndex = 0; columnIndex < ClientCloudTracker.columnCount(); columnIndex++) {
            CloudColumn column = ClientCloudTracker.column(columnIndex);
            if (ClientCloudTracker.horizontalDistanceSqr(column, camX, camZ) >= radiusSqr) {
                continue;
            }

            ClientCloudTracker.refresh(column);
            if (column.spanCount() > 0) {
                this.wc$sheetsPerViewBin[PrecipitationGeometry.angularBin(column.x() + 0.5D - camX, column.z() + 0.5D - camZ)]++;
            }
        }
    }

    @Unique
    private void wc$renderLocalPrecipitation(
            WeatherCloudType type,
            int radius,
            float partialTick,
            double camX,
            double camY,
            double camZ
    ) {
        int cameraX = Mth.floor(camX);
        int cameraY = Mth.floor(camY);
        int cameraZ = Mth.floor(camZ);
        double radiusSqr = (double) radius * radius;
        float animationTime = (float) this.ticks + partialTick;
        BufferBuilder bufferbuilder = null;

        for (int columnIndex = 0; columnIndex < ClientCloudTracker.columnCount(); columnIndex++) {
            CloudColumn column = ClientCloudTracker.column(columnIndex);
            double distanceSqr = ClientCloudTracker.horizontalDistanceSqr(column, camX, camZ);
            if (distanceSqr >= radiusSqr) {
                continue;
            }

            int x = column.x();
            int z = column.z();
            float distance = (float) Math.sqrt(distanceSqr);
            float halfX = PrecipitationGeometry.sheetHalfX(x - cameraX, z - cameraZ);
            float halfZ = PrecipitationGeometry.sheetHalfZ(x - cameraX, z - cameraZ);
            float stackCap = PrecipitationGeometry.stackAlphaCap(
                    this.wc$sheetsPerViewBin[PrecipitationGeometry.angularBin(x + 0.5D - camX, z + 0.5D - camZ)]
            );
            long seed = (long) x * x * 3121L + x * 45238971L ^ (long) z * z * 418711L + z * 13761L;

            for (int spanIndex = 0; spanIndex < column.spanCount(); spanIndex++) {
                if (column.spanType(spanIndex) != type) {
                    continue;
                }

                int bottom = column.spanBottom(spanIndex);
                int top = column.spanTop(spanIndex);
                if (bottom >= top) {
                    continue;
                }

                if (bufferbuilder == null) {
                    RenderSystem.setShaderTexture(0, type == WeatherCloudType.RAIN ? RAIN_LOCATION : SNOW_LOCATION);
                    bufferbuilder = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.PARTICLE);
                }

                this.wc$columnRandom.setSeed(seed);
                this.wc$lightPos.set(x, Mth.clamp(cameraY, bottom, top), z);
                int light = getLightColor(this.level, this.wc$lightPos);

                float x0 = (float) (x - camX - halfX + 0.5D);
                float x1 = (float) (x - camX + halfX + 0.5D);
                float z0 = (float) (z - camZ - halfZ + 0.5D);
                float z1 = (float) (z - camZ + halfZ + 0.5D);
                float yTop = (float) (top - camY);
                float yBottom = (float) (bottom - camY);

                if (type == WeatherCloudType.RAIN) {
                    int tickPhase = this.ticks & 131071;
                    int columnPhase = x * x * 3121 + x * 45238971 + z * z * 418711 + z * 13761 & 255;
                    float speed = 3.0F + this.wc$columnRandom.nextFloat();
                    float scroll = (-((float) (tickPhase + columnPhase) + partialTick) / 32.0F * speed) % 32.0F;
                    float alpha = PrecipitationGeometry.columnAlpha(distance, radius, 0.5F, stackCap);

                    bufferbuilder.addVertex(x0, yTop, z0).setUv(0.0F, bottom * 0.25F + scroll).setColor(1.0F, 1.0F, 1.0F, alpha).setLight(light);
                    bufferbuilder.addVertex(x1, yTop, z1).setUv(1.0F, bottom * 0.25F + scroll).setColor(1.0F, 1.0F, 1.0F, alpha).setLight(light);
                    bufferbuilder.addVertex(x1, yBottom, z1).setUv(1.0F, top * 0.25F + scroll).setColor(1.0F, 1.0F, 1.0F, alpha).setLight(light);
                    bufferbuilder.addVertex(x0, yBottom, z0).setUv(0.0F, top * 0.25F + scroll).setColor(1.0F, 1.0F, 1.0F, alpha).setLight(light);
                } else {
                    float scroll = -((float) (this.ticks & 511) + partialTick) / 512.0F;
                    float driftU = (float) (this.wc$columnRandom.nextDouble() + animationTime * 0.01D * (float) this.wc$columnRandom.nextGaussian());
                    float driftV = (float) (this.wc$columnRandom.nextDouble() + (animationTime * (float) this.wc$columnRandom.nextGaussian()) * 0.001D);
                    float alpha = PrecipitationGeometry.columnAlpha(distance, radius, 0.3F, stackCap);
                    int blockLight = ((light & 65535) * 3 + 240) / 4;
                    int skyLight = ((light >> 16 & 65535) * 3 + 240) / 4;

                    bufferbuilder.addVertex(x0, yTop, z0).setUv(driftU, bottom * 0.25F + scroll + driftV).setColor(1.0F, 1.0F, 1.0F, alpha).setUv2(blockLight, skyLight);
                    bufferbuilder.addVertex(x1, yTop, z1).setUv(1.0F + driftU, bottom * 0.25F + scroll + driftV).setColor(1.0F, 1.0F, 1.0F, alpha).setUv2(blockLight, skyLight);
                    bufferbuilder.addVertex(x1, yBottom, z1).setUv(1.0F + driftU, top * 0.25F + scroll + driftV).setColor(1.0F, 1.0F, 1.0F, alpha).setUv2(blockLight, skyLight);
                    bufferbuilder.addVertex(x0, yBottom, z0).setUv(driftU, top * 0.25F + scroll + driftV).setColor(1.0F, 1.0F, 1.0F, alpha).setUv2(blockLight, skyLight);
                }
            }
        }

        if (bufferbuilder != null) {
            BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
        }
    }

    // Splash particles and sounds keep vanilla's ±10 block sampling, so only nearby clouds are audible.
    @WrapOperation(
            method = "tickRain",
            at = @At(
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
        return ClientCloudTracker.hasPrecipitationNear(cameraPos, WC_TICK_RAIN_HORIZONTAL_RADIUS, WC_TICK_RAIN_VERTICAL_RADIUS)
                ? 1.0F
                : 0.0F;
    }

    @WrapOperation(
            method = "tickRain",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"
            )
    )
    private Biome.Precipitation wc$replaceTickedPrecipitationType(
            Biome biome,
            BlockPos pos,
            Operation<Biome.Precipitation> original
    ) {
        CloudColumn column = ClientCloudTracker.columnAt(pos.getX(), pos.getZ());
        WeatherCloudType local = column != null ? column.typeAt(pos.getY()) : null;
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
            at = @At(
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

        CloudColumn column = ClientCloudTracker.columnAt(pos.getX(), pos.getZ());
        if (column == null) {
            return vanilla;
        }

        int precipitationY = column.nearestSpanBottom(pos.getY());
        return precipitationY == CloudColumn.NO_SPAN ? vanilla : new BlockPos(pos.getX(), precipitationY, pos.getZ());
    }
}
