package ibx.lib.summon.goal;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;

/** 召唤物的默认目标选择，召唤物实体在 {@code registerGoals} 里调。 */
public final class SummonGoals {
    private SummonGoals() {}

    /**
     * 按优先级从 {@code first} 起依次加：主人标记的目标 → 打主人的 → 打自己的（不打自己人）→ 主人是生物时抄主人的目标。
     * 召唤物不主动找怪打，跟着主人动手。
     */
    public static void addTargetGoals(PathfinderMob mob, GoalSelector targetSelector, int first) {
        targetSelector.addGoal(first, new SummonMarkedTargetGoal(mob));
        targetSelector.addGoal(first + 1, new SummonOwnerHurtByTargetGoal(mob));
        targetSelector.addGoal(first + 2, new HurtByTargetGoal(mob));
        targetSelector.addGoal(first + 3, new SummonCopyOwnerTargetGoal(mob));
    }
}
