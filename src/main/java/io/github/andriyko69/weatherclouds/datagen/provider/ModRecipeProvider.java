package io.github.andriyko69.weatherclouds.datagen.provider;

import io.github.andriyko69.weatherclouds.registry.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends RecipeProvider {
    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(@NotNull RecipeOutput recipeOutput, HolderLookup.@NotNull Provider holderLookup) {
        weatherCloudRecipe(
                recipeOutput,
                ModBlocks.RAIN_CLOUD.get(),
                Items.WATER_BUCKET
        );

        weatherCloudRecipe(
                recipeOutput,
                ModBlocks.SNOW_CLOUD.get(),
                Items.SNOWBALL
        );
    }

    private void weatherCloudRecipe(
            RecipeOutput recipeOutput,
            Block result,
            Item specialIngredient
    ) {
        ItemLike mainIngredient = Items.WHITE_WOOL;
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, result)
                .pattern("MMM")
                .pattern("MDM")
                .pattern("MWM")
                .define('M', mainIngredient)
                .define('D', Items.DIAMOND)
                .define('W', specialIngredient)
                .unlockedBy(getHasName(mainIngredient), has(mainIngredient))
                .unlockedBy("has_diamond", has(Items.DIAMOND))
                .unlockedBy(getHasName(specialIngredient), has(specialIngredient))
                .save(recipeOutput);
    }
}