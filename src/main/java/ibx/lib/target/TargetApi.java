package ibx.lib.target;

import ibx.lib.registry.AttachmentRegistry;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.entity.PartEntity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 选目标的通用入口：敌我判断、准星选取、范围找人、「当前锁定」。法术（{@code SpellCasting} 按法术的声明调）、
 * 物品（{@link TargetingItem}）、法术实体的命中筛选、盾击、生物 AI 都用这一套。
 *
 * <p><b>选取规则</b>（准星选人 / 选落点 / 瞄准点都一样）：不穿墙，方块按碰撞箱算、流体不挡。</p>
 * <ul>
 *   <li>选人：目标碰撞箱放大 {@value #AIM_ASSIST} 格再判断（瞄准辅助）；排除死的、旁观者、盔甲架、不可选中的；
 *       打中多部件实体的部件时算到本体上；挡在中间阵营不对的默认穿过去，找后面阵营对的第一个。</li>
 *   <li>选落点：指着生物取它脚下（不分敌我）；指着方块从方块面上退出来一点再往下找地面；指着天、超出射程时取视线上射程末端那一点
 *       往下找地面。往下最多找射程那么长，找不到就是没有落点。</li>
 *   <li>瞄准点：视线 {@value #AIM_RANGE} 格内先碰到的方块或生物；什么也没碰到时是视线上 {@value #AIM_RANGE} 格那一点。</li>
 * </ul>
 *
 * <p><b>生物没被 AI 直接给对象时</b>：敌方的指向型、地点型先用攻击目标（要在射程内、看得见），没有再按视线；
 * 友方的按视线。方向型瞄攻击目标（见 {@link #aimEntity}）。</p>
 */
public final class TargetApi {
    private TargetApi() {}

    /** 瞄准辅助：选人时目标碰撞箱放大多少格。 */
    public static final double AIM_ASSIST = 0.3;
    /** 瞄准点最远看多少格。 */
    public static final double AIM_RANGE = 64.0;
    /** 指着墙、天花板时先从方块面上退出来多少格，再往下找地面。 */
    private static final double STEP_OUT = 0.3;

    // ---------------------------------------------------------------- 敌我

    /**
     * {@code from} 能不能伤到 {@code target}（友伤保护）。以下情况不能：
     * <ul>
     *   <li>目标是自己；两者同乘一个载具；</li>
     *   <li>两者"归属"相同：自己的宠物、宠物打主人、同一主人的宠物 / 召唤物（按 {@link OwnableEntity#getRootOwner()} 归到主人）；</li>
     *   <li>两个玩家之间：按 {@link Player#canHarmPlayer}（服务器的 {@code pvp} 规则、队伍友伤）；</li>
     *   <li>同队且队伍关了友伤；不在队伍里时按原版 {@code isAlliedTo}（驯服动物、袭击者互认等）。</li>
     * </ul>
     * 没有 {@code from} 时一律能伤。客户端的玩家不知道 {@code pvp} 规则，玩家之间的结果只在服务端准。
     */
    public static boolean canHarm(@Nullable Entity from, Entity target) {
        if (from == null) {
            return true;
        }
        if (from == target || from.isPassengerOfSameVehicle(target)) {
            return false;
        }
        Entity fromSide = rootOwner(from);
        Entity targetSide = rootOwner(target);
        if (fromSide == targetSide) {
            return false;
        }
        if (fromSide instanceof Player fromPlayer && targetSide instanceof Player targetPlayer) {
            return fromPlayer.canHarmPlayer(targetPlayer);
        }
        PlayerTeam team = fromSide.getTeam();
        if (team != null && team.isAlliedTo(targetSide.getTeam())) {
            return team.isAllowFriendlyFire();
        }
        return !fromSide.isAlliedTo(targetSide);
    }

    /** 宠物、召唤物归到它的最终主人；没有主人的就是它自己。 */
    private static Entity rootOwner(Entity entity) {
        if (entity instanceof OwnableEntity ownable) {
            LivingEntity owner = ownable.getRootOwner();
            if (owner != null) {
                return owner;
            }
        }
        return entity;
    }

    // ---------------------------------------------------------------- 准星选取

    /**
     * 准星 {@code range} 格内选一个 {@code faction} 的生物（规则见类说明）。
     *
     * @param blockedByOthers true 时挡在中间阵营不对的生物也算挡住（当没指着）；false 穿过去
     * @return 没有为 null
     */
    public static @Nullable LivingEntity pickEntity(LivingEntity seeker, double range, Faction faction, boolean blockedByOthers) {
        Vec3 eye = seeker.getEyePosition();
        Vec3 reach = clipBlocks(seeker, eye, eye.add(seeker.getViewVector(1.0F).scale(range))).getLocation();
        Map<LivingEntity, Double> hits = new LinkedHashMap<>();
        for (Entity entity : seeker.level().getEntities(seeker, new AABB(eye, reach).inflate(1.0 + AIM_ASSIST), Entity::isPickable)) {
            LivingEntity living = asLiving(entity);
            if (living == null || living == seeker || !isSelectable(living) || entity.getRootVehicle() == seeker.getRootVehicle()) {
                continue;
            }
            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius() + AIM_ASSIST);
            Optional<Vec3> clip = box.contains(eye) ? Optional.of(eye) : box.clip(eye, reach);
            if (clip.isPresent()) {
                hits.merge(living, clip.get().distanceToSqr(eye), Math::min);
            }
        }
        List<Map.Entry<LivingEntity, Double>> sorted = new ArrayList<>(hits.entrySet());
        sorted.sort(Map.Entry.comparingByValue());
        for (Map.Entry<LivingEntity, Double> hit : sorted) {
            if (faction.test(seeker, hit.getKey())) {
                return hit.getKey();
            }
            if (blockedByOthers) {
                return null;
            }
        }
        return null;
    }

    /** 准星 {@code range} 格内选一个落点（规则见类说明）；没有为 null。 */
    public static @Nullable Vec3 pickPoint(LivingEntity seeker, double range) {
        Level level = seeker.level();
        Vec3 eye = seeker.getEyePosition();
        Vec3 end = eye.add(seeker.getViewVector(1.0F).scale(range));
        BlockHitResult block = clipBlocks(seeker, eye, end);
        Vec3 reach = block.getLocation();
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(seeker, eye, reach, new AABB(eye, reach).inflate(1.0),
                candidate -> {
                    LivingEntity living = asLiving(candidate);
                    return living != null && living != seeker && isSelectable(living) && candidate.isPickable();
                }, eye.distanceToSqr(reach));
        if (entity != null) {
            LivingEntity living = asLiving(entity.getEntity());
            return (living != null ? living : entity.getEntity()).position();
        }
        Vec3 from = reach;
        if (block.getType() != HitResult.Type.MISS) {
            // 指着墙、天花板时先从方块面上退出来一点，再往下找
            Direction face = block.getDirection();
            from = reach.add(face.getStepX() * STEP_OUT, face.getStepY() * STEP_OUT, face.getStepZ() * STEP_OUT);
        }
        BlockHitResult ground = level.clip(new ClipContext(from, from.subtract(0.0, range, 0.0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, seeker));
        return ground.getType() == HitResult.Type.MISS ? null : ground.getLocation();
    }

    /**
     * 瞄准点：视线 {@value #AIM_RANGE} 格内先碰到的方块或生物（{@code ignore} 除外，一般是弹射物 / 射线自己）；
     * 什么也没碰到时是视线上 {@value #AIM_RANGE} 格那一点。两端都能调，本机玩家的射线每帧按插值后的视线算。
     */
    public static Vec3 aimPoint(Entity seeker, float partialTick, @Nullable Entity ignore) {
        Vec3 eye = seeker.getEyePosition(partialTick);
        Vec3 reach = clipBlocks(seeker, eye, eye.add(seeker.getViewVector(partialTick).scale(AIM_RANGE))).getLocation();
        AABB area = seeker.getBoundingBox().expandTowards(reach.subtract(eye)).inflate(1.0);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(seeker, eye, reach, area,
                candidate -> candidate != ignore && !candidate.isSpectator() && candidate.isPickable(), eye.distanceToSqr(reach));
        return entity != null ? entity.getLocation() : reach;
    }

    /** 还够不够得着：活着、没被移除、同维度、离 {@code holder} 不超过 {@code range} 格（0 = 不看距离）。 */
    public static boolean inReach(LivingEntity holder, Entity target, double range) {
        return target.isAlive() && !target.isRemoved() && target.level() == holder.level()
                && (range <= 0.0 || target.distanceToSqr(holder) <= range * range);
    }

    private static BlockHitResult clipBlocks(Entity seeker, Vec3 from, Vec3 to) {
        return seeker.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, seeker));
    }

    /** 生物本身，或者多部件实体的部件所属的本体；都不是为 null。 */
    private static @Nullable LivingEntity asLiving(Entity entity) {
        if (entity instanceof PartEntity<?> part) {
            return part.getParent() instanceof LivingEntity parent ? parent : null;
        }
        return entity instanceof LivingEntity living ? living : null;
    }

    /** 能被准星选中：活着、不是旁观者、不是盔甲架。 */
    private static boolean isSelectable(LivingEntity entity) {
        return entity.isAlive() && !entity.isSpectator() && !(entity instanceof ArmorStand);
    }

    // ---------------------------------------------------------------- 范围找人

    /** 常用的筛选：{@code from} 这一方的 {@code faction}（{@link Faction#test}），{@code includeSelf} 为 false 时排除 {@code from} 自己。 */
    public static Predicate<LivingEntity> affects(@Nullable Entity from, Faction faction, boolean includeSelf) {
        return target -> (includeSelf || target != from) && faction.test(from, target);
    }

    /**
     * 竖圆柱里的生物，近的排前面：底面中心 {@code base}、半径 {@code radius}、高 {@code height}（从 {@code base} 往上）。
     * 脚（位置）在圆里、碰撞箱和圆柱有重叠才算。排除死的、旁观者、标记用的盔甲架；普通盔甲架不排除。
     */
    public static List<LivingEntity> inCylinder(Level level, Vec3 base, double radius, double height, Predicate<? super LivingEntity> filter) {
        AABB box = new AABB(base.x - radius, base.y, base.z - radius, base.x + radius, base.y + height, base.z + radius);
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box, entity -> {
            double dx = entity.getX() - base.x;
            double dz = entity.getZ() - base.z;
            return isInArea(entity) && dx * dx + dz * dz <= radius * radius && filter.test(entity);
        });
        found.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(base)));
        return found;
    }

    /**
     * {@code user} 前方的扇形里的生物，近的排前面：水平距离不超过 {@code range}（加目标半宽）、在面朝方向左右各 {@code halfAngle} 度以内
     * （贴身的不看角度），上下比 {@code user} 的碰撞箱多 1 格。排除规则同 {@link #inCylinder}，也排除 {@code user} 自己。
     *
     * @param needSight 要不要 {@code user} 看得见（中间没有墙）
     */
    public static List<LivingEntity> inFan(LivingEntity user, double range, double halfAngle, boolean needSight,
                                           Predicate<? super LivingEntity> filter) {
        float yRot = user.getYRot() * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yRot), 0.0, Mth.cos(yRot));
        double cosHalfAngle = Math.cos(halfAngle * Mth.DEG_TO_RAD);
        List<LivingEntity> found = user.level().getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(range, 1.0, range), target -> {
            if (target == user || !isInArea(target) || !filter.test(target)) {
                return false;
            }
            Vec3 offset = target.position().subtract(user.position()).multiply(1.0, 0.0, 1.0);
            double distance = offset.length();
            if (distance > range + target.getBbWidth() * 0.5) {
                return false;
            }
            boolean inCone = distance < 1.0E-4 || offset.normalize().dot(forward) >= cosHalfAngle;
            return inCone && (!needSight || user.hasLineOfSight(target));
        });
        found.sort(Comparator.comparingDouble(user::distanceToSqr));
        return found;
    }

    private static boolean isInArea(LivingEntity entity) {
        return entity.isAlive() && !entity.isSpectator() && !(entity instanceof ArmorStand stand && stand.isMarker());
    }

    // ---------------------------------------------------------------- 当前锁定

    /** 实体身上的当前锁定；没有为 null。只在服务端有。 */
    public static @Nullable TargetLock getLock(LivingEntity holder) {
        return holder.getExistingDataOrNull(AttachmentRegistry.TARGET_LOCK);
    }

    /**
     * 按声明开始一次锁定（施法开始、开始使用物品时），写进当前锁定，顶掉旧的（旧的来源收到 {@code onLost}）。只在服务端用。
     *
     * @param given  AI 直接给的对象：只查够不够得着（活着、同维度、射程内），不查阵营；之后按开始时锁定处理
     * @param owner  这次锁定的来源，放锁时对得上才放（{@link #release}）
     * @param onLost 开始时锁定的目标失效、或者被新的锁定顶掉时调；来源在这里取消自己
     * @return 没有目标（指向型没指着且没声明对自己、地点型没有落点、AI 给的对象够不着）时为 null，不写进当前锁定
     */
    public static @Nullable TargetLock begin(LivingEntity holder, Targeting targeting, @Nullable LivingEntity given, Object owner,
                                             @Nullable Runnable onLost) {
        TargetLock lock;
        if (given != null) {
            if (!inReach(holder, given, targeting.range())) {
                return null;
            }
            lock = new TargetLock(targeting, TargetMode.START, owner, onLost, true);
            lock.locked = given;
            if (targeting.kind() == Targeting.Kind.POINT) {
                lock.lockedPoint = given.position();
            }
        } else {
            lock = new TargetLock(targeting, TargetMode.of(holder, targeting.kind()), owner, onLost, false);
            switch (targeting.kind()) {
                case UNIT -> {
                    LivingEntity picked = pickUnit(holder, targeting);
                    if (picked == null && targeting.fallsBackToSelf()) {
                        picked = holder;
                    }
                    if (picked == null) {
                        return null;
                    }
                    lock.locked = picked;
                }
                case POINT -> {
                    Vec3 point = pickPointFor(holder, targeting);
                    if (point == null) {
                        return null;
                    }
                    lock.lockedPoint = point;
                }
                case SELF, DIRECTION -> {
                }
            }
        }
        lock.current = lock.locked;
        lock.currentPoint = lock.lockedPoint;
        TargetLock old = getLock(holder);
        holder.setData(AttachmentRegistry.TARGET_LOCK, lock);
        if (old != null && old.owner != owner) {
            TargetHandler.hide(holder, old);
            if (old.onLost != null) {
                old.onLost.run();
            }
        }
        return lock;
    }

    /**
     * 生效时（读完、松手、引导每次生效）定下这次作用到谁：开始时锁定的查锁定的还有没有效；生效时选取的按准星重选，
     * 没指着用后备。定下来的写回 {@link TargetLock#target()} / {@link TargetLock#point()}。只在服务端用。
     *
     * @return 没有可作用的（锁定失效、重选和后备都没有）时为 false，调用方取消这次生效
     */
    public static boolean resolve(LivingEntity holder, TargetLock lock) {
        Targeting targeting = lock.targeting;
        switch (targeting.kind()) {
            case UNIT, DIRECTION -> {
                if (lock.locksAtStart() || targeting.kind() == Targeting.Kind.DIRECTION) {
                    if (lock.locked != null && !isValid(holder, lock, lock.locked)) {
                        return false;
                    }
                    lock.current = lock.locked;
                    return targeting.kind() == Targeting.Kind.DIRECTION || lock.current != null;
                }
                LivingEntity picked = pickUnit(holder, targeting);
                lock.current = picked != null ? picked : lock.locked != null && isValid(holder, lock, lock.locked) ? lock.locked : null;
                return lock.current != null;
            }
            case POINT -> {
                if (!lock.locksAtStart()) {
                    Vec3 picked = pickPointFor(holder, targeting);
                    lock.currentPoint = picked != null ? picked : lock.lockedPoint;
                }
                return lock.currentPoint != null;
            }
            default -> {
                return true;
            }
        }
    }

    /** 物品生效时用：当前锁定按 {@link #resolve(LivingEntity, TargetLock)} 定下来后返回；没有锁定、或者没有可作用的为 null。 */
    public static @Nullable TargetLock resolve(LivingEntity holder) {
        TargetLock lock = getLock(holder);
        return lock != null && resolve(holder, lock) ? lock : null;
    }

    /** 放掉 {@code owner} 的锁定（施法结束、物品停用时）；当前锁定不是它的就不动。只在服务端用。 */
    public static void release(LivingEntity holder, Object owner) {
        TargetLock lock = getLock(holder);
        if (lock != null && lock.owner == owner) {
            holder.removeData(AttachmentRegistry.TARGET_LOCK);
            TargetHandler.hide(holder, lock);
        }
    }

    /** AI 直接给的对象（还在当前锁定里）；没有为 null。 */
    public static @Nullable LivingEntity givenTarget(LivingEntity holder) {
        TargetLock lock = getLock(holder);
        return lock != null && lock.given ? lock.locked : null;
    }

    /** 方向型要瞄的生物：AI 直接给的对象，没有时生物瞄自己的攻击目标；玩家为 null（按准星）。 */
    public static @Nullable LivingEntity aimEntity(LivingEntity holder) {
        LivingEntity given = givenTarget(holder);
        if (given != null) {
            return given;
        }
        return holder instanceof Mob mob ? mob.getTarget() : null;
    }

    // ---------------------------------------------------------------- 内部

    /** 指向型选一个（不含对自己的退路）：生物的敌方法术先看攻击目标，其余按准星。 */
    private static @Nullable LivingEntity pickUnit(LivingEntity holder, Targeting targeting) {
        if (holder instanceof Mob mob && targeting.faction() == Faction.ENEMY) {
            LivingEntity target = usableAttackTarget(mob, targeting.range());
            if (target != null && canHarm(mob, target)) {
                return target;
            }
        }
        return pickEntity(holder, targeting.range(), targeting.faction(), targeting.isBlockedByOthers());
    }

    /** 地点型选一个：生物先看攻击目标的脚下，其余按准星。 */
    private static @Nullable Vec3 pickPointFor(LivingEntity holder, Targeting targeting) {
        if (holder instanceof Mob mob) {
            LivingEntity target = usableAttackTarget(mob, targeting.range());
            if (target != null) {
                return target.position();
            }
        }
        return pickPoint(holder, targeting.range());
    }

    /** 生物的攻击目标，要在射程内、看得见；不满足为 null。 */
    private static @Nullable LivingEntity usableAttackTarget(Mob mob, double range) {
        LivingEntity target = mob.getTarget();
        return target != null && inReach(mob, target, range) && mob.hasLineOfSight(target) ? target : null;
    }

    /** 锁定 / 后备还有没有效：自己一直有效；别人要够得着（保持距离）。 */
    static boolean isValid(LivingEntity holder, TargetLock lock, LivingEntity target) {
        return target == holder ? holder.isAlive() : inReach(holder, target, lock.targeting.keepRange());
    }

    /**
     * 每 tick（{@link TargetHandler}）：开始时锁定的查锁定，失效就移除并通知来源，返回 false；
     * 生效时选取的丢掉失效的后备，再按准星重选「现在生效会作用到的」那个。
     */
    static boolean tick(LivingEntity holder, TargetLock lock) {
        Targeting targeting = lock.targeting;
        if (lock.locksAtStart() || targeting.kind() == Targeting.Kind.DIRECTION) {
            if (lock.locked != null && !isValid(holder, lock, lock.locked)) {
                holder.removeData(AttachmentRegistry.TARGET_LOCK);
                TargetHandler.hide(holder, lock);
                if (lock.onLost != null) {
                    lock.onLost.run();
                }
                return false;
            }
            return true;
        }
        switch (targeting.kind()) {
            case UNIT -> {
                if (lock.locked != null && !isValid(holder, lock, lock.locked)) {
                    lock.locked = null;
                }
                LivingEntity picked = pickUnit(holder, targeting);
                lock.current = picked != null ? picked : lock.locked;
            }
            case POINT -> {
                Vec3 picked = pickPointFor(holder, targeting);
                lock.currentPoint = picked != null ? picked : lock.lockedPoint;
            }
            default -> {
            }
        }
        return true;
    }
}
