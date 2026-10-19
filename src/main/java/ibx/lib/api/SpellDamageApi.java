package ibx.lib.api;

import ibx.lib.IbxLib;
import ibx.lib.api.event.SpellDamageEvent;
import ibx.lib.compat.DummmmmmyCompat;
import ibx.lib.mixin.LivingEntityAccessor;
import ibx.lib.registry.AttachmentRegistry;
import ibx.lib.registry.DamageTypeRegistry;
import ibx.lib.registry.TagRegistry;
import ibx.lib.target.TargetApi;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 法术伤害的统一入口：施法时生成快照，命中时结算。法术、法术弹射物、Boss 招式都走这里。
 *
 * <ul>
 *   <li>施法时：{@link #snapshot} 按施法者此刻的属性跑一遍法术公式，得到 {@link SpellDamageSnapshot}；
 *       弹射物用 {@link #setProjectileSnapshot} 带上它。</li>
 *   <li>命中时：{@link #hurt} / {@link #hurtFromProjectile} 结算伤害，{@link #heal} 结算治疗。</li>
 *   <li>法术抗性在 {@link ibx.lib.event.DamageHandler} 里按伤害类型标签减免，附带效果（点燃、冰冻、吸血）
 *       也在那里，所以不经过本类、但进了 {@code #is_spell} 的伤害同样吃法术抗性。</li>
 * </ul>
 *
 * <p>只在服务端结算；客户端调用 {@link #hurt} / {@link #heal} 直接返回。</p>
 */
public final class SpellDamageApi {
    private SpellDamageApi() {}

    /** 法术自带的伤害 / 治疗公式：拿施法瞬间的输入，返回基础值（不含暴击）。 */
    @FunctionalInterface
    public interface Formula {
        float apply(SpellDamageContext ctx);
    }

    /** 已经警告过的伤害类型，每个只警告一次。 */
    private static final Set<ResourceKey<DamageType>> WARNED_TYPES = ConcurrentHashMap.newKeySet();

    /** 用默认伤害类型 {@code spell_general} 生成快照。 */
    public static SpellDamageSnapshot snapshot(LivingEntity caster, Formula formula) {
        return snapshot(caster, DamageTypeRegistry.SPELL_GENERAL, formula);
    }

    /**
     * 按施法者此刻的属性跑一遍公式，生成快照。两端都能调用（客户端可用来算 tooltip 的预估值）。
     *
     * @param damageType 伤害类型，应在 {@code #ibx_lib:is_spell} 里
     */
    public static SpellDamageSnapshot snapshot(LivingEntity caster, ResourceKey<DamageType> damageType, Formula formula) {
        SpellDamageContext ctx = SpellDamageContext.of(caster);
        return SpellDamageSnapshot.of(ctx, damageType, formula.apply(ctx));
    }

    /** 把快照写到弹射物上（任何实体都行），跨区块、存盘不丢。命中时用 {@link #hurtFromProjectile} 结算。 */
    public static void setProjectileSnapshot(Entity projectile, SpellDamageSnapshot snapshot) {
        projectile.setData(AttachmentRegistry.SPELL_SNAPSHOT, snapshot);
    }

    /** 弹射物上的快照；没有则为空。不会顺手创建默认值。 */
    public static Optional<SpellDamageSnapshot> getProjectileSnapshot(Entity projectile) {
        return projectile.getExistingData(AttachmentRegistry.SPELL_SNAPSHOT);
    }

    /**
     * 弹射物命中结算：取它身上的快照，施法者取弹射物的主人（{@link Projectile#getOwner()}，主人已下线或卸载时为空）。
     *
     * @return 是否造成了伤害；弹射物上没有快照时返回 false
     */
    public static boolean hurtFromProjectile(Entity projectile, Entity target) {
        Optional<SpellDamageSnapshot> snapshot = getProjectileSnapshot(projectile);
        if (snapshot.isEmpty()) {
            return false;
        }
        Entity owner = projectile instanceof Projectile p ? p.getOwner() : null;
        return hurt(target, snapshot.get(), projectile, owner, null);
    }

    /**
     * 结算一次法术伤害。顺序：友伤判定 → 掷暴击 → {@link SpellDamageEvent} → 无敌帧开关 → 原版受伤流程
     * （其中 {@code DamageHandler} 按法术抗性减免）→ 暴击表现。
     *
     * <p>不会触发荆棘、火焰附加这类"攻击后"附魔效果：26.1 里它们由攻击方代码在命中后自己调用
     * （{@code EnchantmentHelper.doPostAttackEffects}），不在受伤流程里，本方法不调。</p>
     *
     * @param target         目标。非生物（末影水晶、船……）只走原版受伤，不做友伤、暴击、事件、无敌帧处理
     * @param directEntity   直接造成伤害的实体：弹射物、范围实体；瞬发法术填施法者自己或留空
     * @param caster         施法者，可空（弹射物的主人已不在）。没有施法者时不做友伤判定
     * @param sourcePosition 伤害来源位置，盾牌格挡朝向用；留空时原版取 {@code directEntity} 的位置
     * @return 是否造成了伤害
     */
    public static boolean hurt(Entity target, SpellDamageSnapshot snapshot, @Nullable Entity directEntity,
                               @Nullable Entity caster, @Nullable Vec3 sourcePosition) {
        if (!(target.level() instanceof ServerLevel level)) {
            return false;
        }
        Holder<DamageType> type = resolveDamageType(level, snapshot.damageType());

        if (!(target instanceof LivingEntity living)) {
            return target.hurtServer(level, new SpellDamageSource(type, directEntity, caster, sourcePosition, snapshot, false),
                    snapshot.baseAmount());
        }
        if (!snapshot.friendlyFire() && !TargetApi.canHarm(caster, living)) {
            return false;
        }

        boolean critical = rollCritical(living.getRandom(), snapshot);
        float amount = critical ? snapshot.baseAmount() * snapshot.critMultiplier() : snapshot.baseAmount();
        SpellDamageSource source = new SpellDamageSource(type, directEntity, caster, sourcePosition, snapshot, critical);

        SpellDamageEvent event = NeoForge.EVENT_BUS.post(new SpellDamageEvent(living, source, amount));
        if (event.isCanceled()) {
            return false;
        }
        amount = event.getAmount();

        // 试验假人在自己的 hurt 里把登记过的暴击和伤害来源配对，所以必须在 hurtServer 之前登记；
        // 它按来源的 getEntity() 或 getDirectEntity() 配对，施法者不在时登记弹射物也能对上。
        Entity attacker = caster != null ? caster : directEntity;
        if (critical && attacker != null && DummmmmmyCompat.isLoaded()) {
            DummmmmmyCompat.notifyCrit(living, attacker, snapshot.critMultiplier());
        }

        LivingEntityAccessor accessor = (LivingEntityAccessor) living;
        int invulnerableTimeBefore = living.invulnerableTime;
        float lastHurtBefore = accessor.ibx_lib$getLastHurt();
        if (snapshot.ignoreIFrames()) {
            living.invulnerableTime = 0;
        }
        boolean hit = living.hurtServer(level, source, amount);
        // 没打中时也还原：穿透无敌帧清零了无敌时间，伤害却被取消（免疫、事件取消），不能白白让目标丢掉无敌帧。
        if (snapshot.leaveNoIFrames() || !hit) {
            living.invulnerableTime = invulnerableTimeBefore;
            accessor.ibx_lib$setLastHurt(lastHurtBefore);
        }

        if (hit) {
            if (critical) {
                CriticalStrikeApi.spawnEffects(living);
            }
            if (caster instanceof LivingEntity livingCaster) {
                livingCaster.setLastHurtMob(living);
            }
        }
        return hit;
    }

    /**
     * 结算一次法术治疗：按快照掷暴击，再走原版 {@code heal}（{@code heal_efficiency} 由
     * {@link ibx.lib.event.HealHandler} 自动乘上）。不做友伤判定，不播暴击表现。
     *
     * @return 实际回复的生命值
     */
    public static float heal(LivingEntity target, SpellDamageSnapshot snapshot) {
        if (target.level().isClientSide()) {
            return 0.0F;
        }
        float amount = rollCritical(target.getRandom(), snapshot)
                ? snapshot.baseAmount() * snapshot.critMultiplier()
                : snapshot.baseAmount();
        float before = target.getHealth();
        target.heal(amount);
        return target.getHealth() - before;
    }

    /**
     * 击退工具：法术伤害默认不击退（伤害类型都在 {@code no_knockback} 里），要击退的法术命中后自己调。
     * 水平方向走原版 {@code knockback}（吃击退抗性、触发 NeoForge 的击退事件），再按击退抗性加一点向上的速度。
     *
     * @param from     冲击来源位置（爆炸中心、施法者……），目标被推离这里
     * @param strength 水平力度，原版近战是 0.4
     * @param up       额外的向上速度，0 为不加
     */
    public static void knockback(LivingEntity target, Vec3 from, double strength, double up) {
        target.knockback(strength, from.x - target.getX(), from.z - target.getZ());
        if (up > 0.0D) {
            double resistance = 1.0D - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            if (resistance > 0.0D) {
                target.setDeltaMovement(target.getDeltaMovement().add(0.0D, up * resistance, 0.0D));
            }
        }
        // 让服务端把新速度同步给客户端（玩家的移动由客户端模拟，不标记就推不动）。
        target.hurtMarked = true;
    }

    private static boolean rollCritical(RandomSource random, SpellDamageSnapshot snapshot) {
        // 暴击率大于等于 1 必定暴击（随机数取值范围是 [0,1)），与 CriticalStrikeApi 口径一致。
        return snapshot.canCrit() && random.nextFloat() < snapshot.critChance();
    }

    /**
     * 取伤害类型。类型不存在（提供它的数据包被删了，旧存档里的弹射物还带着）时退回 {@code spell_general} 并警告；
     * 类型不在 {@code #is_spell} 里时，开发环境每个类型警告一次——它会被护甲减免、却不吃法术抗性，不报错很难发现。
     */
    private static Holder<DamageType> resolveDamageType(ServerLevel level, ResourceKey<DamageType> key) {
        Registry<DamageType> registry = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
        Optional<Holder.Reference<DamageType>> type = registry.get(key);
        if (type.isEmpty()) {
            if (WARNED_TYPES.add(key)) {
                IbxLib.LOGGER.warn("法术伤害类型 {} 不存在，改用 {}", key.identifier(), DamageTypeRegistry.SPELL_GENERAL.identifier());
            }
            return registry.getOrThrow(DamageTypeRegistry.SPELL_GENERAL);
        }
        if (!FMLEnvironment.isProduction() && !type.get().is(TagRegistry.IS_SPELL) && WARNED_TYPES.add(key)) {
            IbxLib.LOGGER.warn("法术伤害类型 {} 不在 #{} 里：它会被护甲减免、不吃法术抗性",
                    key.identifier(), TagRegistry.IS_SPELL.location());
        }
        return type.get();
    }
}
