package ibx.taverntales.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import ibx.taverntales.lib.api.RangedWeaponApi;
import ibx.taverntales.lib.item.TtBowItem;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 让持模组弓的生物按武器自己的拉弓时间与蓄力曲线射击。
 *
 * <p>原版 {@code tick()} 里两处写死：松手判定是 {@code i >= 20}（20 tick），力度是
 * {@code BowItem.getPowerForTime(i)}（原版的二次曲线，除以 20）。这两处对 Tt 弓都不对：
 * 拉弓时间是属性驱动的，蓄力百分比是线性的。</p>
 *
 * <p>只改这两处表达式的值，目标 AI 的其余结构（走位、索敌、攻击间隔）原样不动；
 * 非 Tt 弓（含原版弓与其他模组的弓）一律返回原值，行为不受影响。</p>
 */
@Mixin(RangedBowAttackGoal.class)
public abstract class RangedBowAttackGoalMixin {
    /** 原版松手判定的刻度：{@code i >= 20}。 */
    private static final int VANILLA_DRAW_TICKS = 20;

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
    private int taverntales_l1b$scaleDrawTicks(int ticks) {
        ItemStack weapon = this.mob.getUseItem();
        if (!(weapon.getItem() instanceof TtBowItem)) {
            return ticks;
        }
        return ticks * VANILLA_DRAW_TICKS / RangedWeaponApi.getFullDrawTicks(this.mob, weapon);
    }

    /**
     * 力度改用武器自己的线性蓄力百分比。这个值会作为 {@code distanceFactor} 传进
     * {@code performRangedAttack}，最终决定箭的伤害（见 {@link ProjectileUtilMixin}）。
     * 这里重新读一次真实 tick：上面那个换算只改了它自己那个调用点的返回值，不影响方法本身。
     */
    @ModifyExpressionValue(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BowItem;getPowerForTime(I)F"))
    private float taverntales_l1b$linearChargePercent(float original) {
        ItemStack weapon = this.mob.getUseItem();
        if (!(weapon.getItem() instanceof TtBowItem bow)) {
            return original;
        }
        return bow.getChargePercent(this.mob.getTicksUsingItem(), this.mob, weapon);
    }
}
