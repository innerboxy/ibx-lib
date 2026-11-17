package ibx.lib.mixin;

import ibx.lib.client.tooltip.InlineIcons;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.AtlasGlyphProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 文字里嵌的小图标（属性、效果、法术、物品）按整数倍缩小、后面空 1 像素，不一律压成 8×8，见 {@link InlineIcons}。 */
@Mixin(AtlasGlyphProvider.class)
public abstract class AtlasGlyphProviderMixin {
    @Inject(method = "createSprite", at = @At("RETURN"), cancellable = true)
    private void ibx_lib$inlineIconSize(TextureAtlasSprite sprite, CallbackInfoReturnable<GlyphSource> cir) {
        if (InlineIcons.applies(sprite)) {
            cir.setReturnValue(InlineIcons.wrap(cir.getReturnValue(), sprite));
        }
    }
}
