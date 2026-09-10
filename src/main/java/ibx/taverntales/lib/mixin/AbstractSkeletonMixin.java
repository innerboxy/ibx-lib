package ibx.taverntales.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 让骷髅系（骷髅、流浪者、沼骸、凋灵骷髅）认可任意模组弓，而不只是 {@code minecraft:bow}。
 *
 * <p>原版 {@code canFireProjectileWeapon} 写死 {@code projectileWeapon == Items.BOW}。骷髅自身的
 * 目标 AI 已被 NeoForge 改成 {@code instanceof BowItem}，所以这个方法对它自己的射击流程没影响；
 * 但它是"这只生物能不能用这把远程武器"的公共查询口，Brain AI（{@code BehaviorUtils#isWithinAttackRange}、
 * {@code MeleeAttack}）和其他模组都读它，所以一并放开，保证判断口径一致。</p>
 */
@Mixin(AbstractSkeleton.class)
public class AbstractSkeletonMixin {
    @ModifyReturnValue(method = "canFireProjectileWeapon", at = @At("RETURN"))
    private boolean taverntales_l1b$allowModdedBows(boolean original, ProjectileWeaponItem projectileWeapon) {
        return original || projectileWeapon instanceof BowItem;
    }
}
