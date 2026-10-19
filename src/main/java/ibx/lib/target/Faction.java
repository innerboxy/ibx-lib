package ibx.lib.target;

import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * 阵营：一个效果作用于谁。选人（{@link TargetApi#pickEntity}）、法术实体的命中筛选、范围找人都用它，全模组一套。
 *
 * <p>敌方 = 能伤害（{@link TargetApi#canHarm}），友方 = 不能伤害，两者互补。注意友方包括自己（不能伤害自己），
 * 要不要算上自己由用的地方决定。</p>
 */
public enum Faction {
    /** 能伤害的。 */
    ENEMY,
    /** 不能伤害的（宠物、召唤物、同队关了友伤、关了 PvP 的玩家……）。 */
    ALLY,
    /** 不分敌我。 */
    ALL,
    /** 谁也不作用：只借范围实体的外壳、自己不结算的效果用。 */
    NONE;

    /**
     * {@code from} 这一方的效果作用不作用于 {@code target}。没有来源（施法者下线、被卸载）时：敌方、全部照样作用，友方一律不作用。
     */
    public boolean test(@Nullable Entity from, Entity target) {
        return switch (this) {
            case ENEMY -> TargetApi.canHarm(from, target);
            case ALLY -> from != null && !TargetApi.canHarm(from, target);
            case ALL -> true;
            case NONE -> false;
        };
    }
}
