package ibx.lib.summon.goal;

import ibx.lib.api.SummonApi;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/** 主人是生物（Boss 召的小怪）时，召唤物抄主人的目标。主人是玩家时不起作用。 */
public class SummonCopyOwnerTargetGoal extends TargetGoal {
    private @Nullable LivingEntity ownerTarget;

    public SummonCopyOwnerTargetGoal(Mob mob) {
        super(mob, false);
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!(SummonApi.getOwner(this.mob) instanceof Mob owner)) {
            return false;
        }
        this.ownerTarget = owner.getTarget();
        return this.ownerTarget != null && this.ownerTarget != this.mob.getTarget()
                && this.canAttack(this.ownerTarget, TargetingConditions.DEFAULT);
    }

    @Override
    public void start() {
        this.mob.setTarget(this.ownerTarget);
        super.start();
    }
}
