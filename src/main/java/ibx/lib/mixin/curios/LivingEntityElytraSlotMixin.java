package ibx.lib.mixin.curios;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import ibx.lib.compat.curios.ElytraCurioSlot;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 饰品栏鞘翅槽里的鞘翅也能滑翔（见 {@link ElytraCurioSlot}）。
 *
 * <ul>
 *   <li>{@code canGlide}：原版只看身上的装备，这里加上鞘翅槽。玩家的版本在外面再判「没在创造飞行」，照样生效。
 *       原版只在起飞（{@code tryToStartFallFlying}）和滑翔中每刻检查时调它。</li>
 *   <li>{@code updateFallFlying}：原版每 20 刻从身上能滑翔的装备里随机挑一件扣耐久；只靠鞘翅槽滑翔时那张表是空的，
 *       原版会直接抛异常。这里把鞘翅槽的一起算进去，挑中了就由这里扣、跳过原版那一下。</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityElytraSlotMixin {
    @ModifyReturnValue(method = "canGlide", at = @At("RETURN"))
    private boolean ibx_lib$glideWithElytraSlot(boolean original) {
        return original || ElytraCurioSlot.canGlide((LivingEntity) (Object) this);
    }

    @Inject(
            method = "updateFallFlying",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getRandom(Ljava/util/List;Lnet/minecraft/util/RandomSource;)Ljava/lang/Object;"),
            cancellable = true
    )
    private void ibx_lib$damageElytraSlot(CallbackInfo ci, @Local List<EquipmentSlot> slotsWithGliders) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (ElytraCurioSlot.damageOnGlide(self, slotsWithGliders.size())) {
            // 原版扣完耐久接着发这个事件，跳过了要补上
            self.gameEvent(GameEvent.ELYTRA_GLIDE);
            ci.cancel();
        }
    }
}
