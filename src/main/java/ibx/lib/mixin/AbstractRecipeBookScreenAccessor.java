package ibx.lib.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读 {@code AbstractRecipeBookScreen#recipeBookComponent}（private）。
 *
 * <p>背包左边的属性面板和配方书占同一块地方：配方书开着时面板不画，点属性按钮时先把配方书收起来，
 * 见 {@link ibx.lib.client.attribute.AttributePanel}。</p>
 */
@Mixin(AbstractRecipeBookScreen.class)
public interface AbstractRecipeBookScreenAccessor {
    @Accessor("recipeBookComponent")
    RecipeBookComponent<?> ibx_lib$getRecipeBookComponent();
}
