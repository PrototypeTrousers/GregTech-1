package gregtech.mixins.jei;

import gregtech.api.gui.impl.ModularUIContainer;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.integration.jei.recipe.RecipeMapCategory;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.gui.recipes.RecipeGuiLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.inventory.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;

@Mixin(value = RecipeGuiLogic.class, remap = false)
public class RecipeGuiLogicMixin {
    @Inject(method = "getRecipeCategoryIndexToShowFirst", at = @At("HEAD"), cancellable = true)
    void getRecipeCategoryIndexToShowFirst(List<IRecipeCategory> recipeCategories, CallbackInfoReturnable<Integer> cir) {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.player;
        if (player != null) {
            Container openContainer = player.openContainer;
            if (openContainer instanceof ModularUIContainer modularUIContainer) {
                if (modularUIContainer.getModularUI().holder instanceof MetaTileEntityHolder metaTileEntityHolder) {
                    MetaTileEntity mte = metaTileEntityHolder.getMetaTileEntity();
                    Collection<RecipeMapCategory> r = RecipeMapCategory.getCategoriesFor(mte.getRecipeMap());
                    if (r != null) {
                        for (RecipeMapCategory rmc : r) {
                            for (int i = 0; i < recipeCategories.size(); i++) {
                                IRecipeCategory category = recipeCategories.get(i);
                                if (rmc == category) {
                                    cir.setReturnValue(i);
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
