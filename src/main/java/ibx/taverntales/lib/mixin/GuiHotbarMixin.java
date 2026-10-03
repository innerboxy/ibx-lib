package ibx.taverntales.lib.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import ibx.taverntales.lib.client.hud.TavernHud;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 本模组的 HUD 把六个属性放在快捷栏旁边（平时右侧，左手习惯时左侧），原版画在同一侧的攻击冷却指示器（快捷栏模式）
 * 要让到属性块外面。
 *
 * <p>指示器在原版 {@code HOTBAR} 层的 {@code extractItemHotbar} 里画，NeoForge 只能整层替换，
 * 所以这里包住该方法里的两种贴图调用，挪多少由 {@link TavernHud#hotbarSpriteShift} 决定：
 * 只挪指示器，副手栏、快捷栏本身都不动；切回原版 HUD 或创造模式时一律不挪。</p>
 */
@Mixin(Gui.class)
public abstract class GuiHotbarMixin {
    @WrapOperation(method = "extractItemHotbar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void taverntales$shiftAttackIndicator(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite,
                                                  int x, int y, int width, int height, Operation<Void> original) {
        original.call(graphics, pipeline, sprite, x + TavernHud.hotbarSpriteShift(sprite, x, graphics.guiWidth()), y, width, height);
    }

    @WrapOperation(method = "extractItemHotbar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIII)V"))
    private void taverntales$shiftAttackIndicatorProgress(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite,
                                                          int spriteWidth, int spriteHeight, int u, int v, int x, int y,
                                                          int width, int height, Operation<Void> original) {
        original.call(graphics, pipeline, sprite, spriteWidth, spriteHeight, u, v,
                x + TavernHud.hotbarSpriteShift(sprite, x, graphics.guiWidth()), y, width, height);
    }
}
