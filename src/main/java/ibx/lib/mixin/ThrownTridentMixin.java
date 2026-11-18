package ibx.lib.mixin;

import ibx.lib.api.RangedWeaponApi;
import ibx.lib.item.vanilla.VanillaRangedWeapons;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 原版三叉戟打中时的伤害：被接管的原版三叉戟掷出时把远程伤害记在 {@code ammo_ranged_damage} 附加数据上
 * （玩家见 {@code TridentItemMixin}，溺尸见 {@code DrownedMixin}），这里拿它代替原版写死的 8。
 *
 * <p>记下的是附魔前的值：原版接着按戟上的附魔（穿刺）加伤害，算好的数再写回附加数据——命中结算
 * （{@link ibx.lib.event.DamageHandler}）会用附加数据盖掉传进去的伤害，不写回的话穿刺的加成就丢了。
 * 之后的暴击倍率、难度缩放也在那里，同 {@code IbxThrownTrident}。</p>
 *
 * <p>没有附加数据的戟（关掉接管开关时掷出的、发射器射出的、指令召出的）一律照原版。判断只看附加数据、不问开关，
 * 开关改了之后还在飞的戟照它出手时的规则算。</p>
 *
 * @see VanillaRangedWeapons
 */
@Mixin(ThrownTrident.class)
public abstract class ThrownTridentMixin {
    @ModifyConstant(method = "onHitEntity", constant = @Constant(floatValue = 8.0F))
    private float ibx_lib$recordedDamage(float damage) {
        return RangedWeaponApi.getAmmoRangedDamage((Entity) (Object) this).orElse(damage);
    }

    @ModifyArg(method = "onHitEntity", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private float ibx_lib$storeEnchantedDamage(float damage) {
        Entity self = (Entity) (Object) this;
        if (self.level() instanceof ServerLevel && RangedWeaponApi.getAmmoRangedDamage(self).isPresent()) {
            RangedWeaponApi.setAmmoRangedDamage(self, damage);
        }
        return damage;
    }

    /** 暴击只管打中的这一下：标记留着的话，落下、飞回来的路上还会一直冒暴击粒子（同 {@code IbxThrownTrident}）。 */
    @Inject(method = "onHitEntity", at = @At("RETURN"))
    private void ibx_lib$clearCritical(EntityHitResult hitResult, CallbackInfo ci) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        if (RangedWeaponApi.getAmmoRangedDamage(self).isPresent()) {
            self.setCritArrow(false);
        }
    }
}
