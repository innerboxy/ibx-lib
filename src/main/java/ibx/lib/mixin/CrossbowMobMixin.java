package ibx.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 让掠夺者与疣猪兽认可任意模组弩，而不只是标签里登记过的弩。
 *
 * <p>26.1 起这个查询口改名为 {@code canUseNonMeleeWeapon(ItemStack)}，NeoForge 让两者分别查物品标签
 * {@code neoforge:pillager_usable_crossbows} / {@code neoforge:piglin_usable_crossbows}（默认都是
 * {@code #c:tools/crossbow}；疣猪兽另外还认带 {@code kinetic_weapon} 组件的武器）。逻辑一致，故合并成一个多目标 mixin。</p>
 *
 * <p><b>疣猪兽这里是真正的卡点</b>：它走 Brain AI，{@code CrossbowAttack} 行为的可用性判断经
 * {@code BehaviorUtils#isWithinAttackRange} 查这个方法，返回 false 就永远不会开始装填；同时
 * {@code MeleeAttack} 也靠它判断"手里是不是能用的远程武器"来决定要不要改打近战。掠夺者走目标 AI，
 * 它的 {@code RangedCrossbowAttackGoal} 已经是 {@code instanceof CrossbowItem}，这里同样是为了统一判断口径。</p>
 */
@Mixin({Pillager.class, Piglin.class})
public class CrossbowMobMixin {
    @ModifyReturnValue(method = "canUseNonMeleeWeapon", at = @At("RETURN"))
    private boolean ibx_lib$allowModdedCrossbows(boolean original, ItemStack item) {
        return original || item.getItem() instanceof CrossbowItem;
    }
}
