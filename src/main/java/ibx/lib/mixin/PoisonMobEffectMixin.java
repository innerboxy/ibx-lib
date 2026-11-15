package ibx.lib.mixin;

import ibx.lib.effect.VanillaPoison;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.PoisonMobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 服务端配置打开「修改原版中毒」时，中毒的扣血间隔和扣血交给 {@link VanillaPoison}；关闭时完全走原版。 */
@Mixin(PoisonMobEffect.class)
public abstract class PoisonMobEffectMixin {
    @Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true)
    private void ibx_lib$modifiedPoison(ServerLevel level, LivingEntity mob, int amplification, CallbackInfoReturnable<Boolean> cir) {
        if (VanillaPoison.enabled()) {
            VanillaPoison.hurt(level, mob, amplification);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "shouldApplyEffectTickThisTick", at = @At("HEAD"), cancellable = true)
    private void ibx_lib$fixedInterval(int tickCount, int amplification, CallbackInfoReturnable<Boolean> cir) {
        if (VanillaPoison.enabled()) {
            cir.setReturnValue(VanillaPoison.shouldHurt(tickCount));
        }
    }
}
