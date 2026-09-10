package ibx.taverntales.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import ibx.taverntales.lib.api.RangedWeaponApi;
import ibx.taverntales.lib.item.TtCrossbowItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 让原版那个静态的"弩装填用时"对 Tt 弩返回属性算出来的时长。
 *
 * <p>{@code CrossbowItem.getChargeDuration} 是 static，子类重写不了，而它是所有 AI 与渲染判断装填
 * 进度的唯一入口。改这一处，下面三处一次性对齐（都对 Tt 弩生效，原版弩返回原值不受影响）：</p>
 * <ul>
 *   <li>{@code RangedCrossbowAttackGoal}——掠夺者等目标 AI 的松手时机；</li>
 *   <li>{@code CrossbowAttack}——疣猪兽 Brain AI 的松手时机；</li>
 *   <li>{@code ItemInHandRenderer}——玩家第一人称装填时的抖动与缩放动画。</li>
 * </ul>
 *
 * <p>注意 {@link TtCrossbowItem#getUseDuration} 走的是 {@code RangedWeaponApi}，不经过本方法，无递归。</p>
 */
@Mixin(CrossbowItem.class)
public class CrossbowItemMixin {
    @ModifyReturnValue(method = "getChargeDuration", at = @At("RETURN"))
    private static int taverntales_l1b$useWeaponDrawTime(int original, ItemStack stack, LivingEntity shooter) {
        if (!(stack.getItem() instanceof TtCrossbowItem)) {
            return original;
        }
        return RangedWeaponApi.getFullDrawTicks(shooter, stack);
    }
}
