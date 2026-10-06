package ibx.taverntales.lib.entity;

import ibx.taverntales.lib.api.HitTracker;
import ibx.taverntales.lib.api.SpellDamageApi;
import ibx.taverntales.lib.api.SpellDamageSnapshot;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 法术射线的基类：从施法者胸口射出、跟着视线转的一道光束，持续作用于线上的生物——冰霜射线、吸血光束、治疗光线都继承它，
 * 只写自己不一样的部分。和 {@link SpellProjectile}、{@link SpellAoe} 是兄弟：一个飞、一个留在原地、一个跟着施法者转；
 * 伤害都来自施法瞬间存下的快照。
 *
 * <p><b>几何</b>：起点在施法者的胸口（身体中线上、离脚 {@value #CHEST_HEIGHT} 倍身高处，玩家约 1.17 格；实现了 {@link SpellOrigin} 的
 * 施法者从它给的点出发。弹射物是从下巴出发的，射线更低一点），第一人称里射线从屏幕下方射出、不挡准星。方向对准准星指着的地方
 * （起点不在眼睛上，顺着视线平行射会打不到准星），生物有攻击目标时对准目标身体正中（{@link #direction}）。
 * 最远 {@link #getRange()} 格；撞到方块停在方块上（{@link #stopsAtBlocks()}）；不穿透时（{@link #isPiercing()} 为 false，默认）
 * 停在第一个能作用的生物上，不能作用的（施法者自己、友伤保护下的友方）直接穿过去，和弹射物一样。
 * 粗细 {@link #getWidth()}：判定时把目标碰撞箱按半宽撑开再和射线求交。</p>
 *
 * <p><b>多久打一下</b>：同 {@link SpellAoe}，每 tick 找线上的目标，同一目标两次作用至少隔 {@link #getHitInterval()} tick
 * （{@link HitTracker}），默认打完不留无敌帧。引导法术把 {@code tickInterval} 和这里设成一样，就是扣一次蓝打一下。</p>
 *
 * <p><b>生命周期</b>：主要给引导法术用——{@code onCastStart} 建、放进 {@code ctx.setData}，{@code onCastEnd} 调 {@link #end()}。
 * 也可以给 {@code durationTicks} 让它自己到点消失（0 为不限时）。施法者死了、下线、换维度它自己消失。不存盘。</p>
 *
 * <pre>{@code
 * // 注册行：castType(CHANNEL).tickInterval(10).manaCost(2)
 * public final class FrostRaySpell extends Spell {
 *     @Override public void onCastStart(CastContext ctx) {
 *         if (!ctx.level().isClientSide()) {
 *             FrostBeam beam = new FrostBeam(ctx.caster(), ctx.damage(c -> 1.0F + 0.2F * c.spellPower()));
 *             ctx.level().addFreshEntity(beam);
 *             ctx.setData(beam);
 *         }
 *     }
 *     @Override public void onCast(CastContext ctx) { }   // 扣蓝由框架做，伤害射线自己打
 *     @Override public void onCastEnd(CastContext ctx, CastEndReason reason) {
 *         SpellBeam beam = ctx.data();
 *         if (beam != null) beam.end();
 *     }
 * }
 *
 * public class FrostBeam extends SpellBeam {
 *     public FrostBeam(EntityType<? extends FrostBeam> type, Level level) { super(type, level); }
 *     public FrostBeam(LivingEntity caster, SpellDamageSnapshot snapshot) { super(ENTITY_TYPE.get(), caster, snapshot.withFreeze(60), 0); }
 *     @Override protected double getRange() { return 12.0; }
 *     @Override protected void beamParticles(Vec3 start, Vec3 end) { ... 沿线撒雪花 ... }
 * }
 *
 * // 实体类型由各模组自己注册：不存盘、不能 /summon、防火；碰撞箱随便填，判定用的是射线不是碰撞箱
 * ENTITIES.registerEntityType("frost_beam", FrostBeam::new, MobCategory.MISC,
 *         b -> b.noSave().noSummon().noLootTable().fireImmune().sized(0.1F, 0.1F).clientTrackingRange(10).updateInterval(1));
 * }</pre>
 *
 * <p><b>客户端</b>：本机玩家自己的射线，起点和方向在本地按插值后的位置和视线算（和服务端同一套公式），只有长度用服务端同步的，
 * 所以跟着准星走、不抖不拖；别人的、生物的射线用服务端同步的两端，在两次同步之间插值。
 * 渲染器用 {@code client.renderer.SpellBeamRenderer}（一根或几根同轴的贴图方管，比如外层辉光 + 细芯），或者自己写。</p>
 *
 * <p>伤害的来源位置是起点：正面朝施法者举盾能挡。施法者没了射线就结束，不会出现"没有施法者"的结算。</p>
 */
public abstract class SpellBeam extends Entity implements TraceableEntity {
    /** 射线停在了什么上，同步给客户端（命中粒子用）。 */
    public enum EndKind {
        /** 什么都没碰到，打满射程。 */
        NONE,
        BLOCK,
        /** 不穿透时停在第一个目标上。 */
        ENTITY
    }

    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(SpellBeam.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3fc> DATA_START = SynchedEntityData.defineId(SpellBeam.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3fc> DATA_END = SynchedEntityData.defineId(SpellBeam.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> DATA_LENGTH = SynchedEntityData.defineId(SpellBeam.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> DATA_END_KIND = SynchedEntityData.defineId(SpellBeam.class, EntityDataSerializers.BYTE);

    /** 默认起点离脚多高：身高的这个比例（胸口）。 */
    private static final double CHEST_HEIGHT = 0.65;
    /** 找准星指着的地方最远看多少格，同 {@code SpellProjectile}。 */
    private static final double AIM_RANGE = 64.0;
    /** 渲染距离的平方（格）。按实体自己那个小碰撞箱算太近，起点离镜头稍远整条就不画了。 */
    private static final double RENDER_DISTANCE_SQR = 128.0 * 128.0;

    /** 服务端构造时记下；客户端（以及服务端施法者没了之后）按同步的 id 找，见 {@link #getOwner}。 */
    private @Nullable LivingEntity owner;
    private int duration;
    private @Nullable HitTracker tracker;
    /** 客户端：同步过来的两端，画别人的射线时在两次同步之间插值。 */
    private final LerpedPoint clientStart = new LerpedPoint();
    private final LerpedPoint clientEnd = new LerpedPoint();

    /** 实体类型的工厂用（客户端生成），不要在法术里用。 */
    protected SpellBeam(EntityType<? extends SpellBeam> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * 法术里用：主人是施法者，伤害快照存在实体上，起点、方向、长度已按施法者此刻的视线算好（还不打人）。之后 {@code addFreshEntity}。只在服务端用。
     *
     * @param durationTicks 最多存在多少 tick；0 为不限时，由法术在 {@code onCastEnd} 里调 {@link #end()}
     */
    protected SpellBeam(EntityType<? extends SpellBeam> type, LivingEntity caster, SpellDamageSnapshot snapshot, int durationTicks) {
        this(type, caster.level());
        this.owner = caster;
        this.entityData.set(DATA_OWNER, caster.getId());
        this.duration = Math.max(0, durationTicks);
        SpellDamageApi.setProjectileSnapshot(this, snapshot);
        this.trace(caster, false);
    }

    // ---------------------------------------------------------------- 参数（子类重写）

    /** 最远射程（格）。 */
    protected double getRange() {
        return 16.0;
    }

    /** 粗细（格）：判定时目标碰撞箱按它的一半撑开；画出来的方管边长也是它。渲染器要读，所以是 public。 */
    public float getWidth() {
        return 0.3F;
    }

    /** 是否穿透：false 停在第一个能作用的目标上，true 打中线上所有能作用的目标。 */
    protected boolean isPiercing() {
        return false;
    }

    /** 撞到方块是否停下；false 为穿墙。 */
    protected boolean stopsAtBlocks() {
        return true;
    }

    /** 同一目标两次作用之间至少隔多少 tick。 */
    protected int getHitInterval() {
        return 10;
    }

    /** 打完是否不留无敌帧（见类说明）。 */
    protected boolean leavesNoIFrames() {
        return true;
    }

    // ---------------------------------------------------------------- 几何（子类重写；服务端每 tick 调，本机玩家的客户端每帧调并传 partialTick 插值）

    /**
     * 起点：实现了 {@link SpellOrigin} 的施法者用它给的点；否则是胸口——身体中线上、离脚 {@value #CHEST_HEIGHT} 倍身高处
     * （玩家约 1.17 格，随蹲下、游泳的身高变化）。想从手上发出就在这里偏移。
     */
    protected Vec3 origin(LivingEntity owner, float partialTick) {
        if (owner instanceof SpellOrigin custom) {
            return custom.getSpellOrigin();
        }
        return owner.getPosition(partialTick).add(0.0, owner.getBbHeight() * CHEST_HEIGHT, 0.0);
    }

    /**
     * 方向（不必归一化）：默认从起点对准施法者准星指着的地方（视线 {@value #AIM_RANGE} 格内先碰到的方块或生物，见 {@link #aimPoint}），
     * 远近都打在准星上；生物有攻击目标时对准目标身体正中；施法者实现了 {@link SpellOrigin} 并给了瞄准点时对准那里。
     * 起点和准星重合、算不出方向时退回视线。
     */
    protected Vec3 direction(LivingEntity owner, Vec3 start, float partialTick) {
        Vec3 custom = owner instanceof SpellOrigin origin ? origin.getSpellAimPoint() : null;
        LivingEntity target = owner instanceof Mob mob ? mob.getTarget() : null;
        Vec3 point = custom != null ? custom
                : target != null ? target.getBoundingBox().getCenter() : this.aimPoint(owner, partialTick);
        Vec3 aim = point.subtract(start);
        return aim.lengthSqr() > 1.0E-8 ? aim : owner.getViewVector(partialTick);
    }

    /** 准星指着的地方：视线 {@value #AIM_RANGE} 格内先碰到的方块或生物；什么也没指着时是视线上 {@value #AIM_RANGE} 格远的那一点。 */
    protected Vec3 aimPoint(LivingEntity owner, float partialTick) {
        Vec3 eye = owner.getEyePosition(partialTick);
        Vec3 end = eye.add(owner.getViewVector(partialTick).scale(AIM_RANGE));
        HitResult block = this.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 reach = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        AABB area = owner.getBoundingBox().expandTowards(reach.subtract(eye)).inflate(1.0);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(owner, eye, reach, area,
                candidate -> candidate != this && !candidate.isSpectator() && candidate.isPickable(), eye.distanceToSqr(reach));
        return entity != null ? entity.getLocation() : reach;
    }

    // ---------------------------------------------------------------- 钩子（子类重写，都只在服务端调，粒子除外）

    /** 这个目标该不该作用：默认不打施法者，友伤保护开着时只打能伤害的。治疗光线改成只选友方。 */
    protected boolean canAffect(LivingEntity target) {
        LivingEntity owner = this.getOwner();
        if (target == owner) {
            return false;
        }
        SpellDamageSnapshot snapshot = this.snapshot();
        boolean friendlyFire = snapshot != null && snapshot.friendlyFire();
        return friendlyFire || owner == null || SpellDamageApi.canHarm(owner, target);
    }

    /** 作用一次：默认造成快照伤害，返回是否打中。治疗光线改成 {@link SpellDamageApi#heal}。 */
    protected boolean affect(LivingEntity target) {
        SpellDamageSnapshot snapshot = this.snapshot();
        if (snapshot == null) {
            return false;
        }
        if (this.leavesNoIFrames()) {
            snapshot = snapshot.withLeaveNoIFrames(true);
        }
        return SpellDamageApi.hurt(target, snapshot, this, this.getOwner(), this.getStart(1.0F));
    }

    /** 作用一次之后（附带效果：减速、拉近、击退……）。{@code hit} 为 false 表示被挡掉了。 */
    protected void onAffect(LivingEntity target, boolean hit) {
    }

    /** 每 tick 射线停在方块上时：在墙上结霜、点燃方块放这里。 */
    protected void onBlockHit(BlockHitResult hit) {
    }

    /** 到时间了、施法者没了，或者法术调了 {@link #end()}。之后实体消失。 */
    protected void onExpire() {
    }

    /** 客户端每 tick：沿射线放粒子，{@code start}、{@code end} 是这一刻的两端。 */
    protected void beamParticles(Vec3 start, Vec3 end) {
    }

    // ---------------------------------------------------------------- 读

    /** 伤害快照（施法时存的）；没有时为 null。 */
    protected @Nullable SpellDamageSnapshot snapshot() {
        return SpellDamageApi.getProjectileSnapshot(this).orElse(null);
    }

    /** 施法者。服务端直接给；客户端按同步的 id 在世界里找，施法者出了追踪范围时为 null。 */
    @Override
    public @Nullable LivingEntity getOwner() {
        if (this.owner == null || this.owner.isRemoved()) {
            int id = this.entityData.get(DATA_OWNER);
            this.owner = id >= 0 && this.level().getEntity(id) instanceof LivingEntity living ? living : null;
        }
        return this.owner;
    }

    /** 这一刻的长度（格），服务端算好同步过来。 */
    public float getLength() {
        return this.entityData.get(DATA_LENGTH);
    }

    public EndKind getEndKind() {
        EndKind[] kinds = EndKind.values();
        return kinds[Mth.clamp(this.entityData.get(DATA_END_KIND), 0, kinds.length - 1)];
    }

    /** 这一刻的起点：本机玩家自己的在本地算（{@link #origin}），其他的用服务端同步的值（客户端在两次同步之间插值）。 */
    public Vec3 getStart(float partialTick) {
        LivingEntity owner = this.getOwner();
        if (owner != null && isLocalPlayer(owner)) {
            return this.origin(owner, partialTick);
        }
        Vec3 synced = new Vec3(this.entityData.get(DATA_START));
        return this.level().isClientSide() ? this.clientStart.get(partialTick, synced) : synced;
    }

    /** 这一刻的终点：本机玩家自己的 = 本地起点 + 本地方向 × 同步的长度，其他的用服务端同步的值（客户端插值）。 */
    public Vec3 getEnd(float partialTick) {
        LivingEntity owner = this.getOwner();
        if (owner != null && isLocalPlayer(owner)) {
            Vec3 start = this.origin(owner, partialTick);
            return start.add(this.direction(owner, start, partialTick).normalize().scale(this.getLength()));
        }
        Vec3 synced = new Vec3(this.entityData.get(DATA_END));
        return this.level().isClientSide() ? this.clientEnd.get(partialTick, synced) : synced;
    }

    /** 结束：调 {@link #onExpire()} 再消失。引导法术在 {@code onCastEnd} 里调它；直接 {@code discard()} 会跳过收尾钩子。只在服务端。 */
    public void end() {
        if (!this.isRemoved() && !this.level().isClientSide()) {
            this.onExpire();
            this.discard();
        }
    }

    private static boolean isLocalPlayer(LivingEntity entity) {
        return entity instanceof Player player && player.isLocalPlayer();
    }

    // ---------------------------------------------------------------- 每 tick

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.clientStart.tick();
            this.clientEnd.tick();
            if (this.getLength() > 0.0F) {
                this.beamParticles(this.getStart(1.0F), this.getEnd(1.0F));
            }
            return;
        }
        LivingEntity owner = this.getOwner();
        if (owner == null || !owner.isAlive() || owner.level() != this.level()
                || (this.duration > 0 && this.tickCount > this.duration)) {
            this.end();
            return;
        }
        this.trace(owner, true);
    }

    /**
     * 按施法者此刻的位置和视线重算射线：先撞方块，再找线上的目标，不穿透时终点挪到第一个目标上；把两端、长度、终点类型同步出去；
     * {@code affect} 为 true 时再对目标结算（构造时只算几何，不找目标也不打人，子类字段那时还没初始化）。
     */
    private void trace(LivingEntity owner, boolean affect) {
        Vec3 start = this.origin(owner, 1.0F);
        Vec3 direction = this.direction(owner, start, 1.0F).normalize();
        Vec3 end = start.add(direction.scale(this.getRange()));
        EndKind kind = EndKind.NONE;
        BlockHitResult blockHit = null;
        if (this.stopsAtBlocks()) {
            BlockHitResult hit = this.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            if (hit.getType() != HitResult.Type.MISS) {
                end = hit.getLocation();
                kind = EndKind.BLOCK;
                blockHit = hit;
            }
        }

        List<Hit> hits = List.of();
        if (affect) {
            hits = this.findTargets(owner, start, end);
            if (!hits.isEmpty() && !this.isPiercing()) {
                Hit first = hits.getFirst();
                hits = List.of(first);
                end = first.point();
                kind = EndKind.ENTITY;
                blockHit = null;
            }
        }

        // 先同步几何再结算：钩子里可能要读 getEnd()
        this.setPos(start);
        this.entityData.set(DATA_START, vector(start));
        this.entityData.set(DATA_END, vector(end));
        this.entityData.set(DATA_LENGTH, (float) start.distanceTo(end));
        this.entityData.set(DATA_END_KIND, (byte) kind.ordinal());
        if (!affect) {
            return;
        }

        if (this.tracker == null) {
            this.tracker = new HitTracker(this.getHitInterval());
        }
        for (Hit hit : hits) {
            if (this.tracker.tryHit(hit.target())) {
                this.onAffect(hit.target(), this.affect(hit.target()));
            }
        }
        if (blockHit != null) {
            this.onBlockHit(blockHit);
        }
    }

    /**
     * 线上能作用的生物，按离起点的距离从近到远。{@code point} 是射线进入它（撑开半宽后的）碰撞箱的点；
     * 起点本来就在箱子里（目标贴着施法者的脸）时就是起点。
     */
    private List<Hit> findTargets(LivingEntity owner, Vec3 start, Vec3 end) {
        double halfWidth = this.getWidth() / 2.0;
        List<Hit> hits = new ArrayList<>();
        for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(halfWidth),
                entity -> entity != owner && entity.isAlive() && !entity.isSpectator() && this.canAffect(entity))) {
            AABB box = target.getBoundingBox().inflate(halfWidth);
            Vec3 point = box.contains(start) ? start : box.clip(start, end).orElse(null);
            if (point != null) {
                hits.add(new Hit(target, point, point.distanceToSqr(start)));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::distanceSqr));
        return hits;
    }

    private record Hit(LivingEntity target, Vec3 point, double distanceSqr) {
    }

    private static Vector3f vector(Vec3 vec) {
        return new Vector3f((float) vec.x, (float) vec.y, (float) vec.z);
    }

    /**
     * 客户端上一个同步过来的点：记着上一次和这一次的值，画的时候在两者之间插值，别人的射线才不会按 tick 一跳一跳。
     * 同步包在实体 tick 之前处理，所以 {@link #tick} 里看到这一 tick 没来新值就把上一次推平，画面停在最新值上。
     */
    private static final class LerpedPoint {
        private @Nullable Vec3 previous;
        private @Nullable Vec3 current;
        private boolean fresh;

        void accept(Vec3 value) {
            this.previous = this.current != null ? this.current : value;
            this.current = value;
            this.fresh = true;
        }

        void tick() {
            if (!this.fresh) {
                this.previous = this.current;
            }
            this.fresh = false;
        }

        Vec3 get(float partialTick, Vec3 fallback) {
            if (this.current == null) {
                return fallback;
            }
            return this.previous == null ? this.current : this.previous.lerp(this.current, partialTick);
        }
    }

    // ---------------------------------------------------------------- 实体杂项

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER, -1);
        builder.define(DATA_START, new Vector3f());
        builder.define(DATA_END, new Vector3f());
        builder.define(DATA_LENGTH, 0.0F);
        builder.define(DATA_END_KIND, (byte) 0);
    }

    /** 两端同步过来时记下，给客户端插值用。 */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_START.equals(accessor)) {
            this.clientStart.accept(new Vec3(this.entityData.get(DATA_START)));
        } else if (DATA_END.equals(accessor)) {
            this.clientEnd.accept(new Vec3(this.entityData.get(DATA_END)));
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < RENDER_DISTANCE_SQR;
    }

    /** 不存盘：施法状态本来就不存，读档后也没有施法者在放它。 */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    /** 施法者站在传送门里时，起点落在门里也不能把射线传走。 */
    @Override
    public boolean canUsePortal(boolean allowVehicles) {
        return false;
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
        // 不存盘，见 shouldBeSaved
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }
}
