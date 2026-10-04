package ibx.taverntales.lib.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import ibx.taverntales.lib.api.SpellDamageApi;
import ibx.taverntales.lib.api.SpellDamageSnapshot;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;

/**
 * 法术弹射物的基类：火球、奥术飞弹、冰锥这类"飞出去打人"的法术都继承它，只写自己不一样的部分。
 *
 * <p><b>伤害走法术伤害框架</b>：施法瞬间用 {@code ctx.damage(公式)} 算好快照，构造时存到弹射物身上
 * （{@link SpellDamageApi#setProjectileSnapshot}，附加数据，存盘、跨区块不丢）；命中时 {@link SpellDamageApi#hurtFromProjectile}
 * 结算——友伤判定、每个目标各掷暴击、法术抗性、试验假人的蓝色数字都由框架做。所以飞出去以后施法者换装备，伤害也不变。</p>
 *
 * <pre>{@code
 * // 法术里：
 * public void onCast(CastContext ctx) {
 *     FireballProjectile ball = new FireballProjectile(ctx.caster(), ctx.damage(c -> 6.0F + 1.2F * c.spellPower()));
 *     ball.shootFromCaster(1.5F, 0.0F);
 *     ctx.level().addFreshEntity(ball);
 * }
 *
 * // 弹射物：只写参数和钩子
 * public class FireballProjectile extends SpellProjectile {
 *     public FireballProjectile(EntityType<? extends FireballProjectile> type, Level level) { super(type, level); }
 *     public FireballProjectile(LivingEntity caster, SpellDamageSnapshot snapshot) { super(ENTITY_TYPE.get(), caster, snapshot); }
 *     @Override protected int getLifetime() { return 60; }
 *     @Override protected boolean isExtinguishedByWater() { return true; }
 *     @Override protected void onImpact(HitResult hit) { ... 小范围爆炸 ... }
 *     @Override protected void trailParticles() { ... 火焰尾迹 ... }
 * }
 * }</pre>
 *
 * <p>默认行为（都能在子类里改，见各个 {@code get…} / {@code is…} 方法）：</p>
 * <ul>
 *   <li>直线飞、不受重力、空气和水里都<b>不减速</b>（原版雪球那类每 tick 掉 1%，在水里掉 20%，所以不继承 {@code ThrowableProjectile}）；</li>
 *   <li>飞 {@link #getLifetime()} tick（默认 100，5 秒）后消失，消失前调 {@link #onExpire()}；</li>
 *   <li>打中第一个目标就结束（{@link #getPierce()} 为 0），设了穿透则穿过去，同一个目标只打一次；</li>
 *   <li>不打施法者自己；友伤保护开着时（快照默认）直接穿过友方（宠物、同队、关了 PvP 的玩家），不在他们身上炸；</li>
 *   <li>不追踪；{@link #getHomingStrength()} 大于 0 时追前方 60° 内最近的可伤害生物；</li>
 *   <li>进水照飞；{@link #isExtinguishedByWater()} 为 true 时一进水就熄灭（调 {@link #onExpire()}）；</li>
 *   <li>撞到方块、打中最后一个目标都算"命中结束"：调 {@link #onImpact}，客户端播 {@link #impactParticles()}，然后消失。</li>
 * </ul>
 *
 * <p>盾牌照原版：伤害来源带着弹射物的位置，正面举盾能挡（法术伤害不在 {@code #bypasses_shield} 里）。
 * 施法者下线或被卸载后，飞着的弹射物照样结算伤害，只是没有施法者：不做友伤判定。</p>
 *
 * <p>渲染：客户端用 {@code client.renderer.SpellProjectileRenderer}（一张朝向镜头的图）、
 * {@code client.renderer.GeoSpellProjectileRenderer}（GeckoLib 3D 模型，飞行中循环播 {@link #flyAnimation()}），或者自己写渲染器。
 * 实体类型由各模组自己注册（{@code DeferredRegister.Entities}），大小建议 0.25 ~ 0.5 格。</p>
 */
public abstract class SpellProjectile extends Projectile implements GeoEntity {
    /** 命中结束时发给客户端的实体事件，客户端据此播 {@link #impactParticles()}。 */
    private static final byte IMPACT_EVENT = 3;
    /** 追踪只看前方这个角度以内（cos 60°）。 */
    private static final double HOMING_CONE = 0.5;

    private int age;
    private int pierced;
    /** 已经打过的实体（穿透时不重复打）。不存盘：读档后可能再打一次同一个目标，可以接受。 */
    private final IntSet hitEntities = new IntOpenHashSet();
    private @Nullable LivingEntity homingTarget;
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /** 实体类型的工厂用（读档、客户端生成），不要在法术里用。 */
    protected SpellProjectile(EntityType<? extends SpellProjectile> type, Level level) {
        super(type, level);
    }

    /**
     * 法术里用：从施法者眼睛稍下方出发，主人是施法者，伤害快照存到弹射物上。之后调 {@link #shootFromCaster} 或 {@code shoot}，
     * 再 {@code addFreshEntity}。只在服务端用。
     */
    protected SpellProjectile(EntityType<? extends SpellProjectile> type, LivingEntity caster, SpellDamageSnapshot snapshot) {
        this(type, caster.level());
        this.setOwner(caster);
        this.setPos(caster.getX(), caster.getEyeY() - 0.1, caster.getZ());
        SpellDamageApi.setProjectileSnapshot(this, snapshot);
    }

    /** 沿主人的视线发射：{@code speed} 格 / tick，{@code inaccuracy} 同原版（0 为绝对准，弓满弦是 1）。没有主人时什么也不做。 */
    public SpellProjectile shootFromCaster(float speed, float inaccuracy) {
        Entity owner = this.getOwner();
        if (owner != null) {
            this.shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0.0F, speed, inaccuracy);
        }
        return this;
    }

    // ---------------------------------------------------------------- 参数（子类重写）

    /** 最多飞多少 tick。 */
    protected int getLifetime() {
        return 100;
    }

    /** 空气里每 tick 速度保留的比例，1 为不减速。 */
    protected double getDrag() {
        return 1.0;
    }

    /** 水里每 tick 速度保留的比例，1 为不减速。 */
    protected double getWaterDrag() {
        return 1.0;
    }

    /** 一进水就熄灭（火系）。 */
    protected boolean isExtinguishedByWater() {
        return false;
    }

    /** 能穿过几个目标：0 为打中第一个就结束。 */
    protected int getPierce() {
        return 0;
    }

    /** 追踪强度：每 tick 把速度方向往目标转多少（0 ~ 1），0 为不追踪。速度大小不变。 */
    protected float getHomingStrength() {
        return 0.0F;
    }

    /** 追踪找目标的距离（格）。 */
    protected double getHomingRange() {
        return 16.0;
    }

    // 重力：重写原版的 getDefaultGravity()，默认 0；雪球是 0.03。

    // ---------------------------------------------------------------- 钩子（子类重写，都只在服务端调，粒子除外）

    /** 打中一个目标之后（伤害已经结算）。{@code damaged} 为 false 表示被挡掉了（无敌帧、盾牌、免疫……）。 */
    protected void onHitTarget(Entity target, boolean damaged) {
    }

    /** 命中结束：撞到方块，或者打中了最后一个能打的目标。爆炸、留下范围效果放这里。之后弹射物消失。 */
    protected void onImpact(HitResult hit) {
    }

    /** 飞到寿命尽头，或者进水熄灭（{@code isInWater()} 为 true）。之后弹射物消失。 */
    protected void onExpire() {
    }

    /** 客户端每 tick：尾迹粒子。 */
    protected void trailParticles() {
    }

    /** 客户端：命中结束时的粒子（在弹射物当前位置）。 */
    protected void impactParticles() {
    }

    // ---------------------------------------------------------------- 飞行

    /**
     * 照原版 {@code ThrowableProjectile.tick} 的顺序：重力 → 阻力 → 追踪 → 沿速度找碰撞 → 移动 → 命中。
     * 不同的只有阻力（可配、默认不减速）、追踪、寿命和进水熄灭，以及起点已经在目标碰撞箱里时也算打中（见 {@link #findOverlappingEntity}）。
     */
    @Override
    public void tick() {
        this.applyGravity();
        double drag = this.isInWater() ? this.getWaterDrag() : this.getDrag();
        if (drag != 1.0) {
            this.setDeltaMovement(this.getDeltaMovement().scale(drag));
        }
        if (!this.level().isClientSide() && this.getHomingStrength() > 0.0F) {
            this.steer();
        }

        HitResult result = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (result.getType() != HitResult.Type.ENTITY) {
            Entity overlapping = this.findOverlappingEntity();
            if (overlapping != null) {
                result = new EntityHitResult(overlapping, this.position());
            }
        }
        boolean hit = result.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, result);
        this.setPos(hit ? result.getLocation() : this.position().add(this.getDeltaMovement()));
        this.updateRotation();
        this.applyEffectsFromBlocks();
        super.tick();
        if (hit && this.isAlive()) {
            this.hitTargetOrDeflectSelf(result);
        }
        if (!this.isAlive()) {
            return;
        }

        if (this.level().isClientSide()) {
            this.trailParticles();
        } else if ((this.isInWater() && this.isExtinguishedByWater()) || ++this.age >= this.getLifetime()) {
            this.onExpire();
            this.discard();
        }
    }

    /** 往目标转一点，速度大小不变；目标没了、出了范围就重新找。只在服务端，改完同步给客户端。 */
    private void steer() {
        LivingEntity target = this.homingTarget;
        double range = this.getHomingRange();
        if (target == null || !target.isAlive() || target.distanceToSqr(this) > range * range || this.hitEntities.contains(target.getId())) {
            target = this.homingTarget = this.findHomingTarget(range);
        }
        Vec3 motion = this.getDeltaMovement();
        double speed = motion.length();
        if (target == null || speed < 1.0E-4) {
            return;
        }
        Vec3 wanted = target.getBoundingBox().getCenter().subtract(this.position()).normalize().scale(speed);
        this.setDeltaMovement(motion.lerp(wanted, this.getHomingStrength()).normalize().scale(speed));
        this.needsSync = true;
    }

    /** 前方 60° 以内、能打的、最近的生物。 */
    private @Nullable LivingEntity findHomingTarget(double range) {
        Vec3 heading = this.getDeltaMovement().normalize();
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(range),
                entity -> entity.isAlive() && this.canHitEntity(entity))) {
            Vec3 toCandidate = candidate.getBoundingBox().getCenter().subtract(this.position());
            double distance = toCandidate.lengthSqr();
            if (distance <= range * range && distance < bestDistance && toCandidate.normalize().dot(heading) >= HOMING_CONE) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- 命中

    /**
     * 自己的碰撞箱已经碰到的、能打的、最近的实体。原版只找"这一 tick 从外面撞进目标碰撞箱"的：起点已经在箱子里时
     * （目标走进了弹射物；原版判定边距随飞行 tick 变大，把弹射物圈了进去；追踪急转擦着目标边过），整段路都在箱子里，
     * 找不到撞入点，弹射物会直接穿过去。
     */
    private @Nullable Entity findOverlappingEntity() {
        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : this.level().getEntities(this, this.getBoundingBox(), this::canHitEntity)) {
            double distance = entity.distanceToSqr(this);
            if (distance < nearestDistance) {
                nearest = entity;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /** 不打施法者自己、不重复打同一个目标；友伤保护开着时不打友方（直接穿过去）。 */
    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!super.canHitEntity(entity) || this.hitEntities.contains(entity.getId())) {
            return false;
        }
        Entity owner = this.getOwner();
        if (entity == owner) {
            return false;
        }
        boolean friendlyFire = SpellDamageApi.getProjectileSnapshot(this).map(SpellDamageSnapshot::friendlyFire).orElse(false);
        return friendlyFire || owner == null || SpellDamageApi.canHarm(owner, entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!(this.level() instanceof ServerLevel)) {
            return;
        }
        Entity target = hit.getEntity();
        this.hitEntities.add(target.getId());
        boolean damaged = SpellDamageApi.hurtFromProjectile(this, target);
        this.onHitTarget(target, damaged);
        if (this.pierced++ >= this.getPierce()) {
            this.impact(hit);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (this.level() instanceof ServerLevel) {
            this.impact(hit);
        }
    }

    private void impact(HitResult hit) {
        this.onImpact(hit);
        this.level().broadcastEntityEvent(this, IMPACT_EVENT);
        this.discard();
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == IMPACT_EVENT) {
            this.impactParticles();
        } else {
            super.handleEntityEvent(id);
        }
    }

    // ---------------------------------------------------------------- 其他

    /** 同原版 {@code ThrowableProjectile}：刚出手两 tick 内离镜头太近不画（不挡视线），远处按大小算。 */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        if (this.tickCount < 2 && distance < 12.25) {
            return false;
        }
        double size = this.getBoundingBox().getSize() * 4.0;
        if (Double.isNaN(size)) {
            size = 4.0;
        }
        size *= 64.0;
        return distance < size * size;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Age", this.age);
        output.putInt("Pierced", this.pierced);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.age = input.getIntOr("Age", 0);
        this.pierced = input.getIntOr("Pierced", 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    // ---------------------------------------------------------------- 3D 模型（GeckoLib）

    /** 用 3D 模型渲染（{@code client.renderer.GeoSpellProjectileRenderer}）时，飞行中一直循环播的动画；null 为不播。 */
    protected @Nullable RawAnimation flyAnimation() {
        return null;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        RawAnimation fly = this.flyAnimation();
        if (fly != null) {
            controllers.add(new AnimationController<SpellProjectile>("fly", test -> test.setAndContinue(fly)));
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
