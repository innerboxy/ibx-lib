package ibx.lib.entity;

import ibx.lib.api.HitTracker;
import ibx.lib.api.SpellDamageApi;
import ibx.lib.api.SpellDamageSnapshot;
import ibx.lib.target.Faction;
import ibx.lib.target.TargetApi;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 法术范围效果的基类：留在原地一段时间、持续作用于范围里的生物——火墙、毒云、治疗圈都继承它，只写自己不一样的部分。
 * 和 {@link SpellProjectile}、{@link SpellBeam} 是兄弟：一个飞、一个留在原地、一个跟着施法者的视线转，伤害都来自施法瞬间存下的快照。
 *
 * <p><b>形状</b>是个竖着的圆柱：中心在实体位置，水平半径 {@link #getRadius()}（同步给客户端，可随时间变化），
 * 高 {@link #getHeight()}，从实体位置往上算。生物的脚（位置）在圆里、碰撞箱和圆柱有重叠就算在范围里。</p>
 *
 * <p><b>多久打一下</b>：每 tick 找范围里的目标，同一目标两次作用之间至少隔 {@link #getHitInterval()} tick（{@link HitTracker}）。
 * 默认打完不留无敌帧（{@link #leavesNoIFrames()}）：节奏由这个间隔决定，持续伤害也不会挡掉玩家别的攻击。</p>
 *
 * <pre>{@code
 * // 法术里：在准星落点放一片 3 格半径、持续 5 秒的火
 * FireFieldAoe fire = new FireFieldAoe(ctx.caster(), ctx.damage(c -> 2.0F + 0.3F * c.spellPower()), pos, 3.0F, 100);
 * ctx.level().addFreshEntity(fire);
 *
 * public class FireFieldAoe extends SpellAoe {
 *     public FireFieldAoe(EntityType<? extends FireFieldAoe> type, Level level) { super(type, level); }
 *     public FireFieldAoe(LivingEntity caster, SpellDamageSnapshot snapshot, Vec3 pos, float radius, int duration) {
 *         super(ENTITY_TYPE.get(), caster, snapshot, pos, radius, duration);
 *     }
 *     @Override protected ParticleOptions getParticle() { return ParticleTypes.FLAME; }
 *     @Override protected void onAffect(LivingEntity target, boolean hit) { if (hit) target.igniteForSeconds(2.0F); }
 * }
 * }</pre>
 *
 * <p>默认行为（都能在子类里改）：</p>
 * <ul>
 *   <li>对范围里<b>能伤害的</b>生物造成快照伤害（{@link #affects} + {@link #affect}）：不打施法者，友伤保护开着时（快照默认）不打友方；
 *       治疗圈之类重写这两个方法（友方、{@link SpellDamageApi#heal}）；</li>
 *   <li>同一目标 10 tick 一下（每秒两下），打完不留无敌帧；</li>
 *   <li>高 1 格，半径不变；</li>
 *   <li>可以先"预警"一段时间再生效（{@link #setWaitTime}，默认 0）：预警期间不作用，粒子稀疏；</li>
 *   <li>持续时间到了调 {@link #onExpire()} 后消失；</li>
 *   <li>客户端每 tick 在圆盘上随机冒 {@link #getParticle()}（默认女巫的紫色粒子），多少按面积算。没有模型，
 *       实体渲染器注册原版的 {@code NoopRenderer::new} 即可。</li>
 * </ul>
 *
 * <p>伤害的来源位置是圆心：正面朝圆心举盾能挡，不想被挡就用打了 {@code #bypasses_shield} 的伤害类型。
 * 施法者下线或被卸载后照样生效，只是没有施法者：不做友伤判定。实体类型由各模组自己注册（{@code DeferredRegister.Entities}），
 * 尺寸随半径变，注册时的 {@code sized} 随便填。</p>
 */
public abstract class SpellAoe extends Entity implements TraceableEntity {
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(SpellAoe.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_WAITING = SynchedEntityData.defineId(SpellAoe.class, EntityDataSerializers.BOOLEAN);
    private static final float MAX_RADIUS = 32.0F;

    private @Nullable EntityReference<LivingEntity> owner;
    private int duration = 100;
    private int waitTime;
    private int age;
    private @Nullable HitTracker hits;

    /** 实体类型的工厂用（读档、客户端生成），不要在法术里用。 */
    protected SpellAoe(EntityType<? extends SpellAoe> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * 法术里用：放在 {@code pos}（圆柱底面中心），主人是施法者，伤害快照存在实体上。之后 {@code addFreshEntity}。只在服务端用。
     *
     * @param durationTicks 生效多久（tick），不含预警
     */
    protected SpellAoe(EntityType<? extends SpellAoe> type, LivingEntity caster, SpellDamageSnapshot snapshot, Vec3 pos,
                       float radius, int durationTicks) {
        this(type, caster, pos, radius, durationTicks);
        SpellDamageApi.setProjectileSnapshot(this, snapshot);
    }

    /** 不带伤害快照：只借外壳（预警、时长、半径同步）、自己不结算的范围效果用，配合 {@link #affects} 返回 {@link Faction#NONE}。 */
    protected SpellAoe(EntityType<? extends SpellAoe> type, LivingEntity caster, Vec3 pos, float radius, int durationTicks) {
        this(type, caster.level());
        this.owner = EntityReference.of(caster);
        this.setPos(pos);
        this.setRadius(radius);
        this.duration = Math.max(1, durationTicks);
    }

    // ---------------------------------------------------------------- 参数（子类重写）

    /** 同一目标两次作用之间至少隔多少 tick。 */
    protected int getHitInterval() {
        return 10;
    }

    /** 圆柱高度（格），从实体位置往上算。 */
    protected float getHeight() {
        return 1.0F;
    }

    /** 半径每 tick 变化多少（扩散为正、收缩为负）；缩到 0.5 格以下就提前结束。 */
    protected float getRadiusPerTick() {
        return 0.0F;
    }

    /** 打完是否不留无敌帧（见类说明）。 */
    protected boolean leavesNoIFrames() {
        return true;
    }

    /** 客户端粒子的种类。 */
    protected ParticleOptions getParticle() {
        return ParticleTypes.WITCH;
    }

    // ---------------------------------------------------------------- 钩子（子类重写，都只在服务端调，粒子除外）

    /**
     * 作用于谁：默认敌方，快照开了友伤（{@code friendlyFire}）就不分敌我。治疗圈改成友方；
     * 只借外壳（预警、时长、半径同步）、自己不结算的改成 {@link Faction#NONE}，就不再每 tick 找人。
     */
    protected Faction affects() {
        SpellDamageSnapshot snapshot = this.snapshot();
        return snapshot != null && snapshot.friendlyFire() ? Faction.ALL : Faction.ENEMY;
    }

    /** 这个目标该不该作用：不打施法者，其余看 {@link #affects}。 */
    protected boolean canAffect(LivingEntity target) {
        Entity owner = this.getOwner();
        return target != owner && this.affects().test(owner, target);
    }

    /** 作用一次：默认造成快照伤害，返回是否打中。治疗圈改成 {@link SpellDamageApi#heal}。 */
    protected boolean affect(LivingEntity target) {
        SpellDamageSnapshot snapshot = this.snapshot();
        if (snapshot == null) {
            return false;
        }
        if (this.leavesNoIFrames()) {
            snapshot = snapshot.withLeaveNoIFrames(true);
        }
        return SpellDamageApi.hurt(target, snapshot, this, this.getOwner(), this.position());
    }

    /** 作用一次之后（附带效果：点燃、减速、击退……）。{@code hit} 为 false 表示被挡掉了。 */
    protected void onAffect(LivingEntity target, boolean hit) {
    }

    /** 持续时间到了，或者半径缩没了。之后实体消失。 */
    protected void onExpire() {
    }

    /** 客户端每 tick：在圆盘上随机冒粒子，生效时每平方格约 0.3 个，预警时只有零星几个。 */
    protected void ambientParticles() {
        float radius = this.getRadius();
        int count = this.isWaiting() ? (this.random.nextInt(4) == 0 ? 1 : 0) : Mth.ceil(Mth.PI * radius * radius * 0.3F);
        for (int i = 0; i < count; i++) {
            float angle = this.random.nextFloat() * Mth.TWO_PI;
            float distance = Mth.sqrt(this.random.nextFloat()) * radius;
            this.level().addParticle(this.getParticle(),
                    this.getX() + Mth.cos(angle) * distance, this.getY() + 0.1, this.getZ() + Mth.sin(angle) * distance,
                    0.0, 0.02, 0.0);
        }
    }

    // ---------------------------------------------------------------- 读写

    /** 伤害快照（施法时存的）；没有时为 null。 */
    protected @Nullable SpellDamageSnapshot snapshot() {
        return SpellDamageApi.getProjectileSnapshot(this).orElse(null);
    }

    public float getRadius() {
        return this.entityData.get(DATA_RADIUS);
    }

    public void setRadius(float radius) {
        if (!this.level().isClientSide()) {
            this.entityData.set(DATA_RADIUS, Mth.clamp(radius, 0.0F, MAX_RADIUS));
        }
    }

    /** 预警多久（tick）再生效，放进世界之前设。 */
    public void setWaitTime(int ticks) {
        this.waitTime = Math.max(0, ticks);
    }

    public boolean isWaiting() {
        return this.entityData.get(DATA_WAITING);
    }

    @Override
    public @Nullable LivingEntity getOwner() {
        return EntityReference.getLivingEntity(this.owner, this.level());
    }

    // ---------------------------------------------------------------- 每 tick

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.ambientParticles();
            return;
        }
        if (this.age++ >= this.waitTime + this.duration) {
            this.expire();
            return;
        }
        boolean waiting = this.age <= this.waitTime;
        if (waiting != this.isWaiting()) {
            this.entityData.set(DATA_WAITING, waiting);
        }
        if (waiting) {
            return;
        }
        float perTick = this.getRadiusPerTick();
        if (perTick != 0.0F) {
            float radius = this.getRadius() + perTick;
            if (radius < 0.5F) {
                this.expire();
                return;
            }
            this.setRadius(radius);
        }

        if (this.affects() == Faction.NONE) {
            return;
        }
        if (this.hits == null) {
            this.hits = new HitTracker(this.getHitInterval());
        }
        for (LivingEntity target : TargetApi.inCylinder(this.level(), this.position(), this.getRadius(), this.getHeight(), this::canAffect)) {
            if (this.hits.tryHit(target)) {
                this.onAffect(target, this.affect(target));
            }
        }
    }

    private void expire() {
        this.onExpire();
        this.discard();
    }

    // ---------------------------------------------------------------- 实体杂项

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 1.0F);
        builder.define(DATA_WAITING, false);
    }

    /** 半径变了跟着改碰撞箱（找目标用它）。 */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (DATA_RADIUS.equals(accessor)) {
            this.refreshDimensions();
        }
        super.onSyncedDataUpdated(accessor);
    }

    /** 改碰撞箱时保持位置不动（同原版药水云）。 */
    @Override
    public void refreshDimensions() {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        super.refreshDimensions();
        this.setPos(x, y, z);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(this.getRadius() * 2.0F, this.getHeight());
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public final boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Age", this.age);
        output.putInt("Duration", this.duration);
        output.putInt("WaitTime", this.waitTime);
        output.putFloat("Radius", this.getRadius());
        EntityReference.store(this.owner, output, "Owner");
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.age = input.getIntOr("Age", 0);
        this.duration = input.getIntOr("Duration", 100);
        this.waitTime = input.getIntOr("WaitTime", 0);
        this.setRadius(input.getFloatOr("Radius", 1.0F));
        this.owner = EntityReference.read(input, "Owner");
    }
}
