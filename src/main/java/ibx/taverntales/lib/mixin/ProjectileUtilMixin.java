package ibx.taverntales.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import ibx.taverntales.lib.api.RangedWeaponApi;
import ibx.taverntales.lib.item.TtBowItem;
import ibx.taverntales.lib.item.TtCrossbowItem;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 让生物用 Tt 弓射出的箭也带上伤害快照，伤害口径与玩家一致。
 *
 * <p>玩家射击走的是物品自己的发射流程，在那里写快照；但生物不走物品——
 * {@code AbstractSkeleton#performRangedAttack} 与 {@code Illusioner#performRangedAttack} 都是自己
 * 造箭，只经过 {@code ProjectileUtil.getMobArrow}，箭的伤害由 {@code setBaseDamageFromMob} 按难度定。
 * 在这个唯一的汇合点补写快照，两种生物一并覆盖，别的模组调它也照顾到。</p>
 *
 * <p>{@code velocity} 就是 {@code performRangedAttack} 收到的 {@code distanceFactor}，对弓 AI 而言
 * 即蓄力百分比（已由 {@link RangedBowAttackGoalMixin} 换成线性值）。非 Tt 武器一律不碰。</p>
 */
@Mixin(ProjectileUtil.class)
public class ProjectileUtilMixin {
    @ModifyReturnValue(method = "getMobArrow", at = @At("RETURN"))
    private static AbstractArrow taverntales_l1b$applyAmmoRangedDamage(
            AbstractArrow arrow, LivingEntity shooter, ItemStack ammo, float velocity, ItemStack weapon) {
        if (weapon == null) {
            return arrow;
        }
        float damage;
        if (weapon.getItem() instanceof TtBowItem) {
            damage = RangedWeaponApi.getRangedDamage(shooter, weapon) * Mth.clamp(velocity, 0.0F, 1.0F);
        } else if (weapon.getItem() instanceof TtCrossbowItem) {
            // 原版生物的弩不经过这里（走物品的发射流程），仅为其他模组直接调用本方法时兜底。
            damage = RangedWeaponApi.getRangedDamage(shooter, weapon);
        } else {
            return arrow;
        }
        RangedWeaponApi.setAmmoRangedDamage(arrow, damage);
        return arrow;
    }
}
