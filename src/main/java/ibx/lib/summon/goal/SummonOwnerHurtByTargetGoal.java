package ibx.lib.summon.goal;

import ibx.lib.api.SummonApi;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/** 召唤物去打打主人的（保护主人）。照原版 {@code OwnerHurtByTargetGoal}，主人取自召唤物框架。 */
public class SummonOwnerHurtByTargetGoal extends TargetGoal {
    private @Nullable LivingEntity ownerLastHurtBy;
    private int timestamp;

    public SummonOwnerHurtByTargetGoal(Mob mob) {
        super(mob, false);
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        LivingEntity owner = SummonApi.getOwner(this.mob);
        if (owner == null) {
            return false;
        }
        this.ownerLastHurtBy = owner.getLastHurtByMob();
        return owner.getLastHurtByMobTimestamp() != this.timestamp && this.canAttack(this.ownerLastHurtBy, TargetingConditions.DEFAULT);
    }

    @Override
    public void start() {
        this.mob.setTarget(this.ownerLastHurtBy);
        LivingEntity owner = SummonApi.getOwner(this.mob);
        if (owner != null) {
            this.timestamp = owner.getLastHurtByMobTimestamp();
        }
        super.start();
    }
}
