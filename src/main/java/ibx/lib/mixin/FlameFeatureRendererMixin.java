package ibx.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import ibx.lib.client.CustomFireRendering;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FlameFeatureRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 实体身上烧的是换色的火（{@link ibx.lib.api.CustomFire}）时，第三人称火苗的两张贴图换成那种火的。
 * 原版在 {@code renderFlame} 开头从图集取 fire_0、fire_1，这里按调用顺序（ordinal 0、1）换掉；没换色时原样返回。
 */
@Mixin(FlameFeatureRenderer.class)
public abstract class FlameFeatureRendererMixin {
    @ModifyExpressionValue(method = "renderFlame", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/model/sprite/AtlasManager;get(Lnet/minecraft/client/resources/model/sprite/SpriteId;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;",
            ordinal = 0))
    private TextureAtlasSprite ibx_lib$customFire0(TextureAtlasSprite original, @Local(argsOnly = true) EntityRenderState state) {
        Identifier look = state.getRenderData(CustomFireRendering.LOOK);
        return look == null ? original : CustomFireRendering.sprite(look, 0);
    }

    @ModifyExpressionValue(method = "renderFlame", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/model/sprite/AtlasManager;get(Lnet/minecraft/client/resources/model/sprite/SpriteId;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;",
            ordinal = 1))
    private TextureAtlasSprite ibx_lib$customFire1(TextureAtlasSprite original, @Local(argsOnly = true) EntityRenderState state) {
        Identifier look = state.getRenderData(CustomFireRendering.LOOK);
        return look == null ? original : CustomFireRendering.sprite(look, 1);
    }
}
