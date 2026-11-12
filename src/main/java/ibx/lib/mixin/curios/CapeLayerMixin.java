package ibx.lib.mixin.curios;

import com.mojang.blaze3d.vertex.PoseStack;
import ibx.lib.compat.curios.client.ElytraCurioRendering;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 背上画了鞘翅槽的鞘翅时不画披风，同原版胸甲栏有鞘翅时（见 {@link ElytraCurioRendering}）。
 */
@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {
    @Shadow
    private boolean hasLayer(ItemStack itemStack, EquipmentClientInfo.LayerType layerType) {
        throw new AssertionError();
    }

    @Inject(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ibx_lib$hideUnderCurioWings(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
        ItemStack wings = state.getRenderData(ElytraCurioRendering.WINGS);
        if (wings != null && this.hasLayer(wings, EquipmentClientInfo.LayerType.WINGS)) {
            ci.cancel();
        }
    }
}
