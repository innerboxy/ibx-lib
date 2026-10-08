package ibx.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import ibx.lib.api.RangedWeaponApi;
import ibx.lib.item.IbxBowItem;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 让持弓生物（骷髅、流髑、沼骸、幻术师）按武器自己的拉弓时间与蓄力曲线射击。
 *
 * <p>原版 {@code tick()} 里两处写死：松手判定是 {@code i >= 20}（20 tick），力度是
 * {@code BowItem.getPowerForTime(i)}（原版的二次曲线，除以 20）。这两处对本模组的弓都不对：
 * 拉弓时间是属性驱动的，蓄力百分比是线性的。</p>
 *
 * <p>只改这两处表达式的值，目标 AI 的其余结构（走位、索敌、攻击间隔）原样不动。
 * 只对 {@link RangedWeaponApi#isManagedBow} 认的弓生效（Ibx 弓、被接管的原版弓），
 * 其他模组的弓与关掉接管的原版弓一律返回原值，行为不受影响。</p>
 */
@Mixin(RangedBowAttackGoal.class)
public abstract class RangedBowAttackGoalMixin {
    /** 目标类里是 {@code private final T mob}，泛型 {@code T extends Mob & RangedAttackMob} 擦除后就是 Mob。 */
    @Shadow
    @Final
    private Mob mob;

    /**
     * 把已拉弓 tick 换算到原版的 20 tick 刻度上，这样 {@code i >= 20} 就等价于"拉满武器自己的拉弓时间"。
     * 换算而不是改常量，是因为那个 20 是字面量，同方法里还有另外两个 20（视线计时、走位计时），
     * 按序号改常量太脆。
     */
    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "getTicksUsingItem()I"))
    private int ibx_lib$scaleDrawTicks(int ticks) {
        ItemStack weapon = this.mob.getUseItem();
        if (!RangedWeaponApi.isManagedBow(weapon)) {
            return ticks;
        }
        return ticks * BowItem.MAX_DRAW_DURATION / RangedWeaponApi.getFullDrawTicks(this.mob, weapon);
    }

    /**
     * 力度改用线性蓄力百分比。这个值会作为 {@code power} 传进 {@code performRangedAttack}，
     * 最终决定箭的伤害（见 {@link ProjectileUtilMixin}）。Ibx 弓走它自己可重写的 {@link IbxBowItem#getChargePercent}。
     * 这里重新读一次真实 tick：上面那个换算只改了它自己那个调用点的返回值，不影响方法本身。
     */
    @ModifyExpressionValue(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BowItem;getPowerForTime(I)F"))
    private float ibx_lib$linearChargePercent(float original) {
        ItemStack weapon = this.mob.getUseItem();
        if (!RangedWeaponApi.isManagedBow(weapon)) {
            return original;
        }
        int ticks = this.mob.getTicksUsingItem();
        return weapon.getItem() instanceof IbxBowItem bow
                ? bow.getChargePercent(ticks, this.mob, weapon)
                : RangedWeaponApi.getChargePercent(ticks, this.mob, weapon);
    }
}
