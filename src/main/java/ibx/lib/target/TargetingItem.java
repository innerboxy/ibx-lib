package ibx.lib.target;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 要选目标的物品（按住使用、松手生效那类）实现它，lib 替它选、锁、校验、画提示：
 *
 * <ul>
 *   <li>开始使用：按声明选、写进当前锁定；没指着就不开始使用，提示没有目标（或按声明对自己）。</li>
 *   <li>使用中：lib 每 tick 校验；开始时锁定的目标失效就停止使用，提示目标丢失。</li>
 *   <li>生效（物品自己的 {@code releaseUsing} / {@code finishUsingItem} 里）：调 {@link TargetApi#resolve(LivingEntity)}
 *       拿这次作用到的目标 / 落点，返回 null 就当没放出来。</li>
 *   <li>停止使用后 lib 自己放掉锁定。</li>
 * </ul>
 *
 * <p>瞬间生效的物品（{@code use} 里就生效）不用实现它，直接调 {@link TargetApi#pickEntity} / {@link TargetApi#pickPoint}。</p>
 */
public interface TargetingItem {
    /** 这件物品给这个使用者用时怎么瞄。 */
    Targeting targeting(ItemStack stack, LivingEntity user);
}
