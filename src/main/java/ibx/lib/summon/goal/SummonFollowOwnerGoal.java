package ibx.lib.summon.goal;

import ibx.lib.api.SummonApi;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * 召唤物跟着主人走，离得太远（同一维度 12 格以上）就瞬移到主人身边。照原版驯服动物的 {@code FollowOwnerGoal}，
 * 主人取自召唤物框架，不要求是驯服动物。有目标时不跟（让打架的目标先跑）。
 */
public class SummonFollowOwnerGoal extends Goal {
    private static final double TELEPORT_DISTANCE_SQR = 144.0D;

    private final Mob mob;
    private final double speedModifier;
    private final float startDistance;
    private final float stopDistance;
    private @Nullable LivingEntity owner;
    private int timeToRecalcPath;
    private float oldWaterCost;

    public SummonFollowOwnerGoal(Mob mob, double speedModifier, float startDistance, float stopDistance) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity owner = SummonApi.getOwner(this.mob);
        if (owner == null || owner.isSpectator() || owner.level() != this.mob.level() || this.mob.getTarget() != null
                || this.mob.distanceToSqr(owner) < this.startDistance * this.startDistance) {
            return false;
        }
        this.owner = owner;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.owner != null && !this.mob.getNavigation().isDone() && this.owner.level() == this.mob.level()
                && this.mob.getTarget() == null && this.mob.distanceToSqr(this.owner) > this.stopDistance * this.stopDistance;
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
        this.oldWaterCost = this.mob.getPathfindingMalus(PathType.WATER);
        this.mob.setPathfindingMalus(PathType.WATER, 0.0F);
    }

    @Override
    public void stop() {
        this.owner = null;
        this.mob.getNavigation().stop();
        this.mob.setPathfindingMalus(PathType.WATER, this.oldWaterCost);
    }

    @Override
    public void tick() {
        if (this.owner == null) {
            return;
        }
        boolean far = this.mob.distanceToSqr(this.owner) >= TELEPORT_DISTANCE_SQR;
        if (!far) {
            this.mob.getLookControl().setLookAt(this.owner, 10.0F, this.mob.getMaxHeadXRot());
        }
        if (--this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = this.adjustedTickDelay(10);
            if (far) {
                this.teleportNear(this.owner.blockPosition());
            } else {
                this.mob.getNavigation().moveTo(this.owner, this.speedModifier);
            }
        }
    }

    /** 在主人周围 2 ~ 3 格找个能站的地方瞬移过去，试 10 次，同原版。 */
    private void teleportNear(BlockPos center) {
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = this.mob.getRandom().nextIntBetweenInclusive(-3, 3);
            int dz = this.mob.getRandom().nextIntBetweenInclusive(-3, 3);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
                continue;
            }
            int dy = this.mob.getRandom().nextIntBetweenInclusive(-1, 1);
            BlockPos pos = center.offset(dx, dy, dz);
            if (this.canTeleportTo(pos)) {
                this.mob.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, this.mob.getYRot(), this.mob.getXRot());
                this.mob.getNavigation().stop();
                return;
            }
        }
    }

    private boolean canTeleportTo(BlockPos pos) {
        if (WalkNodeEvaluator.getPathTypeStatic(this.mob, pos) != PathType.WALKABLE) {
            return false;
        }
        if (this.mob.level().getBlockState(pos.below()).getBlock() instanceof LeavesBlock) {
            return false;
        }
        BlockPos delta = pos.subtract(this.mob.blockPosition());
        return this.mob.level().noCollision(this.mob, this.mob.getBoundingBox().move(delta));
    }
}
