package ibx.lib.summon.goal;

import ibx.lib.api.SummonApi;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * 召唤物优先打主人标记的目标（主人最近打中的那个，见 {@link SummonApi#markedTarget}）。
 * 标记换了就换过去；放在目标选择器里优先级最高的位置。
 */
public class SummonMarkedTargetGoal extends TargetGoal {
    private @Nullable LivingEntity marked;

    public SummonMarkedTargetGoal(Mob mob) {
        super(mob, false);
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        this.marked = this.currentMark();
        return this.marked != null && this.marked != this.mob.getTarget() && this.canAttack(this.marked, TargetingConditions.DEFAULT);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity mark = this.currentMark();
        return mark != null && mark == this.mob.getTarget() && super.canContinueToUse();
    }

    @Override
    public void start() {
        this.mob.setTarget(this.marked);
        super.start();
    }

    private @Nullable LivingEntity currentMark() {
        LivingEntity owner = SummonApi.getOwner(this.mob);
        return owner == null ? null : SummonApi.markedTarget(owner);
    }
}
