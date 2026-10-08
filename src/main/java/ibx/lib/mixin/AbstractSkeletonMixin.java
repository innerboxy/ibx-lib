package ibx.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 让骷髅系（骷髅、流浪者、沼骸、凋灵骷髅）认可任意模组弓，而不只是标签里登记过的弓。
 *
 * <p>26.1 起这个查询口改名为 {@code canUseNonMeleeWeapon(ItemStack)}，NeoForge 让它查物品标签
 * {@code neoforge:skeleton_usable_bows}（默认即 {@code #c:tools/bow}），没进标签的模组弓会被判为不能用。
 * 骷髅自身的目标 AI 仍是 {@code instanceof BowItem}，所以这个方法对它自己的射击流程没影响；
 * 但它是"这只生物能不能用这把远程武器"的公共查询口，Brain AI（{@code BehaviorUtils#isWithinAttackRange}、
 * {@code MeleeAttack}）和其他模组都读它，所以一并放开到 instanceof，保证判断口径一致。</p>
 */
@Mixin(AbstractSkeleton.class)
public class AbstractSkeletonMixin {
    @ModifyReturnValue(method = "canUseNonMeleeWeapon", at = @At("RETURN"))
    private boolean ibx_lib$allowModdedBows(boolean original, ItemStack item) {
        return original || item.getItem() instanceof BowItem;
    }
}
