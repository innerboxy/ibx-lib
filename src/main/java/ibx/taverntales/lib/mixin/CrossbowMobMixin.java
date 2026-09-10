package ibx.taverntales.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 让掠夺者与疣猪兽认可任意模组弩，而不只是 {@code minecraft:crossbow}。
 *
 * <p>两者的原版实现都是 {@code projectileWeapon == Items.CROSSBOW}，逻辑一致，故合并成一个多目标 mixin。</p>
 *
 * <p><b>疣猪兽这里是真正的卡点</b>：它走 Brain AI，{@code CrossbowAttack} 行为的可用性判断经
 * {@code BehaviorUtils#isWithinAttackRange} 查这个方法，返回 false 就永远不会开始装填；同时
 * {@code MeleeAttack} 也靠它判断"手里是不是能用的远程武器"来决定要不要改打近战。掠夺者走目标 AI，
 * 它的 {@code RangedCrossbowAttackGoal} 已经是 {@code instanceof CrossbowItem}，这里同样是为了统一判断口径。</p>
 */
@Mixin({Pillager.class, Piglin.class})
public class CrossbowMobMixin {
    @ModifyReturnValue(method = "canFireProjectileWeapon", at = @At("RETURN"))
    private boolean taverntales_l1b$allowModdedCrossbows(boolean original, ProjectileWeaponItem projectileWeapon) {
        return original || projectileWeapon instanceof CrossbowItem;
    }
}
