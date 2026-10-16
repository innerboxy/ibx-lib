package ibx.lib.api;

import ibx.lib.IbxLib;
import ibx.lib.api.event.SummonEvent;
import ibx.lib.registry.AttachmentRegistry;
import ibx.lib.registry.AttributeRegistry;
import ibx.lib.spell.Spell;
import ibx.lib.summon.OwnerSummons;
import ibx.lib.summon.SummonData;
import ibx.lib.summon.Summoned;
import ibx.lib.summon.UnsummonReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 召唤物框架的入口（只在服务端生效）。
 *
 * <ul>
 *   <li><b>名额</b>：主人的属性「召唤物上限」{@code ibx_lib:max_summons}（默认 1）是总名额，每只召唤物占几个由召唤时给定
 *       （法术的 {@code summonCost}，默认 1）。名额不够时最早召唤的先消散；单只就超过上限时召不出（{@link #summon} 返回 false）。</li>
 *   <li><b>寿命</b>：召唤物一律常驻，主人死亡、下线、找不到时全部消散，主人换维度时跟着搬过去。限时的接口（{@code durationTicks}、法术的 {@code summonDuration}、到点消散）留着，目前不用。</li>
 *   <li><b>强度</b>：召唤时由法术按施法者属性算好，用 {@link #setStat} 写成召唤物自己的属性，之后主人换装备不影响。</li>
 *   <li><b>友伤</b>：召唤物打不到主人和同一主人的其他召唤物，主人也打不到自己的召唤物；玩家主人的召唤物算作主人本人，按玩家打玩家判断（关了 PvP、队友且关了友伤时打不到），两个方向都管；法术友伤按
 *       {@code OwnableEntity#getRootOwner}（召唤物实现 {@link Summoned}）自动覆盖。</li>
 *   <li><b>标记目标</b>：主人打中谁就标记谁，召唤物优先打它（目标 {@code SummonMarkedTargetGoal}）；最近一次打中后 10 秒有效。</li>
 *   <li><b>击杀归属</b>：主人是玩家时，召唤物打死的生物算主人杀的（经验、玩家击杀掉落、统计）。</li>
 * </ul>
 * 生命周期、友伤、标记、击杀归属的事件监听在 {@code ibx.lib.summon.SummonHandler}。
 */
public final class SummonApi {
    private SummonApi() {}

    /** 召唤物身板快照修饰符的 id（{@link #setStat}）。 */
    public static final Identifier SNAPSHOT_MODIFIER_ID = Identifier.fromNamespaceAndPath(IbxLib.MODID, "summon_snapshot");

    /** 标记目标管多久（tick）：主人最近一次打中它之后 10 秒内有效，过了召唤物就不再追着它打。 */
    public static final int MARK_TICKS = 200;

    /** 名单里的召唤物连续找不到多久（tick）才划掉：区块刚卸载 / 刚加载时先等一等。 */
    private static final int LOST_ENTRY_TICKS = 100;

    /** 名单条目第一次找不到的世界时刻（不存盘）。 */
    private static final Map<UUID, Long> MISSING_SINCE = new HashMap<>();

    // ---------------------------------------------------------------- 查询

    /** 实体身上的召唤物数据；不是召唤物返回 {@link SummonData#NONE}。 */
    public static SummonData data(Entity entity) {
        return entity.hasData(AttachmentRegistry.SUMMON) ? entity.getData(AttachmentRegistry.SUMMON) : SummonData.NONE;
    }

    public static boolean isSummon(Entity entity) {
        return data(entity).isSummon();
    }

    public static @Nullable UUID ownerId(Entity entity) {
        return data(entity).owner();
    }

    /** 召唤物的主人（只在服务端找得到，任何维度都找）；不是召唤物或找不到为 null。 */
    public static @Nullable LivingEntity getOwner(Entity summon) {
        UUID owner = ownerId(summon);
        return owner == null ? null : find(summon.level(), owner) instanceof LivingEntity living ? living : null;
    }

    /** {@code summon} 是不是 {@code owner} 召的。 */
    public static boolean isOwnedBy(Entity summon, Entity owner) {
        return owner.getUUID().equals(ownerId(summon));
    }

    /** 主人的总名额（属性「召唤物上限」取整）。 */
    public static int capacity(LivingEntity owner) {
        AttributeInstance attr = owner.getAttribute(AttributeRegistry.MAX_SUMMONS);
        return attr == null ? 0 : Mth.floor(attr.getValue());
    }

    /** 已用名额（名单里所有召唤物，包括暂时没加载的）。 */
    public static int used(LivingEntity owner) {
        return owner.hasData(AttachmentRegistry.OWNER_SUMMONS) ? owner.getData(AttachmentRegistry.OWNER_SUMMONS).used() : 0;
    }

    /** 占 {@code cost} 个名额的召唤物能不能召（只看上限，不看已用：满了会挤掉最早的）。 */
    public static boolean fits(LivingEntity owner, int cost) {
        return cost <= capacity(owner);
    }

    /** 主人在场（已加载）的召唤物，最早召唤的在前。顺手清掉连续找不到太久的名单条目。 */
    public static List<Mob> getSummons(LivingEntity owner) {
        if (!(owner.level() instanceof ServerLevel level) || !owner.hasData(AttachmentRegistry.OWNER_SUMMONS)) {
            return List.of();
        }
        OwnerSummons list = owner.getData(AttachmentRegistry.OWNER_SUMMONS);
        long now = level.getGameTime();
        List<Mob> result = new ArrayList<>();
        boolean pruned = false;
        for (OwnerSummons.Entry entry : list.entries()) {
            Entity e = find(level, entry.id());
            if (e instanceof Mob mob && !mob.isRemoved()) {
                MISSING_SINCE.remove(entry.id());
                result.add(mob);
            } else {
                long since = MISSING_SINCE.computeIfAbsent(entry.id(), id -> now);
                if (now - since >= LOST_ENTRY_TICKS) {
                    MISSING_SINCE.remove(entry.id());
                    pruned |= list.remove(entry.id());
                }
            }
        }
        if (pruned) {
            changed(owner);
        }
        return result;
    }

    // ---------------------------------------------------------------- 召唤

    /** 按法术注册行的名额和时长召唤（{@code summonCost}、{@code summonDuration}，时长没写 = 常驻）。 */
    public static boolean summon(LivingEntity owner, Mob summon, Spell spell) {
        float seconds = spell.summonDuration();
        return summon(owner, summon, spell, seconds > 0.0F ? Math.max(1, Mth.ceil(seconds * 20.0F)) : 0, spell.summonCost());
    }

    /**
     * 把 {@code summon}（已经 {@code create}、摆好位置、算好身板）作为 {@code owner} 的召唤物放进主人所在的世界。
     *
     * @param spell         召它的法术，不是法术召的填 null
     * @param durationTicks 寿命 tick，小于等于 0 = 常驻
     * @param cost          占几个名额，最少 0
     * @return 召出来了；单只超过上限、被事件取消、放不进世界、召唤物和主人不在同一个世界时为 false
     */
    public static boolean summon(LivingEntity owner, Mob summon, @Nullable Spell spell, int durationTicks, int cost) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return false;
        }
        if (summon.level() != level) {
            // 召唤物要在主人所在的世界里 create，否则实体记的世界和放进去的世界对不上
            IbxLib.LOGGER.warn("Summon {} was created in {} but its owner {} is in {}; not summoned",
                    summon.getType(), summon.level().dimension(), owner.getName().getString(), level.dimension());
            return false;
        }
        cost = Math.max(0, cost);
        if (!fits(owner, cost)) {
            return false;
        }
        if (NeoForge.EVENT_BUS.post(new SummonEvent.Pre(owner, summon, spell)).isCanceled()) {
            return false;
        }
        makeRoom(owner, cost);
        long expires = durationTicks > 0 ? level.getGameTime() + durationTicks : -1L;
        summon.setData(AttachmentRegistry.SUMMON, new SummonData(owner.getUUID(), spell == null ? null : spell.id(), cost, expires));
        summon.setPersistenceRequired();
        if (!level.addFreshEntity(summon)) {
            summon.removeData(AttachmentRegistry.SUMMON);
            return false;
        }
        owner.getData(AttachmentRegistry.OWNER_SUMMONS).add(summon.getUUID(), cost);
        changed(owner);
        NeoForge.EVENT_BUS.post(new SummonEvent.Post(owner, summon));
        return true;
    }

    /** 名额不够放下 {@code cost} 时，从最早召唤的开始挤掉。 */
    private static void makeRoom(LivingEntity owner, int cost) {
        OwnerSummons list = owner.getData(AttachmentRegistry.OWNER_SUMMONS);
        int capacity = capacity(owner);
        while (!list.isEmpty() && list.used() + cost > capacity) {
            OwnerSummons.Entry oldest = list.entries().getFirst();
            Entity e = find(owner.level(), oldest.id());
            if (e instanceof Mob mob && !mob.isRemoved()) {
                dismiss(mob, UnsummonReason.REPLACED);
            }
            // 没加载的直接划掉；它以后加载时发现自己不在名单里会自己消散
            list.remove(oldest.id());
        }
        changed(owner);
    }

    // ---------------------------------------------------------------- 离场

    /** 让召唤物离场：从主人名单划掉、发事件，再交给它的 {@link Summoned#onUnsummon}（不是 {@link Summoned} 的冒烟移除）。 */
    public static void dismiss(Mob summon, UnsummonReason reason) {
        if (summon.level().isClientSide() || summon.isRemoved()) {
            return;
        }
        LivingEntity owner = getOwner(summon);
        if (owner != null && owner.hasData(AttachmentRegistry.OWNER_SUMMONS)
                && owner.getData(AttachmentRegistry.OWNER_SUMMONS).remove(summon.getUUID())) {
            changed(owner);
        }
        NeoForge.EVENT_BUS.post(new SummonEvent.Unsummon(owner, summon, reason));
        if (summon instanceof Summoned summoned) {
            summoned.onUnsummon(reason);
            if (!summon.isRemoved()) {
                summon.discard();
            }
        } else {
            if (summon.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.POOF, summon.getX(), summon.getY() + summon.getBbHeight() * 0.5, summon.getZ(),
                        12, summon.getBbWidth() * 0.4, summon.getBbHeight() * 0.3, summon.getBbWidth() * 0.4, 0.02);
            }
            summon.discard();
        }
    }

    /** 让主人的召唤物全部离场，返回送走了几只（没加载的直接从名单划掉，不算数）。 */
    public static int dismissAll(LivingEntity owner, UnsummonReason reason) {
        if (owner.level().isClientSide() || !owner.hasData(AttachmentRegistry.OWNER_SUMMONS)) {
            return 0;
        }
        List<Mob> summons = getSummons(owner);
        for (Mob summon : summons) {
            dismiss(summon, reason);
        }
        OwnerSummons list = owner.getData(AttachmentRegistry.OWNER_SUMMONS);
        for (OwnerSummons.Entry entry : list.entries()) {
            list.remove(entry.id());
        }
        list.setMarked(null, 0L);
        changed(owner);
        return summons.size();
    }

    /** 被打死、被别的代码移除时调：只从主人名单里划掉，划掉了才发事件（框架送走的已经划过）。 */
    public static void forget(Mob summon, UnsummonReason reason) {
        LivingEntity owner = getOwner(summon);
        if (owner != null && owner.hasData(AttachmentRegistry.OWNER_SUMMONS)
                && owner.getData(AttachmentRegistry.OWNER_SUMMONS).remove(summon.getUUID())) {
            changed(owner);
            NeoForge.EVENT_BUS.post(new SummonEvent.Unsummon(owner, summon, reason));
        }
    }

    // ---------------------------------------------------------------- 认亲、队伍

    /**
     * 召唤物把 {@code other} 当不当自己人：主人、同一主人的其他召唤物、主人的自己人（主人的宠物、队友……）。
     * 给召唤物实体的 {@code considersEntityAsAlly} 用。
     */
    public static boolean isAlly(Entity summon, Entity other) {
        UUID owner = ownerId(summon);
        if (owner == null) {
            return false;
        }
        if (owner.equals(other.getUUID()) || owner.equals(ownerId(other))) {
            return true;
        }
        if (isSummon(other)) {
            // 别人的召唤物：不往下问它的主人，免得两边召唤物互相问来问去
            return false;
        }
        LivingEntity ownerEntity = getOwner(summon);
        return ownerEntity != null && ownerEntity.isAlliedTo(other);
    }

    /** 主人的队伍（召唤物自己不在队伍里时跟主人走）。给召唤物实体的 {@code getTeam} 用。 */
    public static @Nullable PlayerTeam ownerTeam(Entity summon) {
        LivingEntity owner = getOwner(summon);
        return owner == null ? null : owner.getTeam();
    }

    // ---------------------------------------------------------------- 标记目标

    /** 主人标记一个目标，他的召唤物优先打它；每打中一次重新计时。 */
    public static void mark(LivingEntity owner, LivingEntity target) {
        owner.getData(AttachmentRegistry.OWNER_SUMMONS).setMarked(target.getUUID(), owner.level().getGameTime());
    }

    /** 主人当前的标记目标；过了 {@link #MARK_TICKS}、死了、不见了、不在同一维度的清掉，返回 null。 */
    public static @Nullable LivingEntity markedTarget(LivingEntity owner) {
        if (!(owner.level() instanceof ServerLevel level) || !owner.hasData(AttachmentRegistry.OWNER_SUMMONS)) {
            return null;
        }
        OwnerSummons list = owner.getData(AttachmentRegistry.OWNER_SUMMONS);
        UUID id = list.marked();
        if (id == null) {
            return null;
        }
        if (level.getGameTime() - list.markedAt() <= MARK_TICKS
                && level.getEntity(id) instanceof LivingEntity target && target.isAlive() && !target.isRemoved()) {
            return target;
        }
        list.setMarked(null, 0L);
        return null;
    }

    // ---------------------------------------------------------------- 身板、位置

    /**
     * 写召唤物某个属性的快照（召唤时按施法者属性算好的 {@code value}）：挂一个固定 id 的加值修饰符，补上 {@code value} 和
     * 基础值的差额，重复调用会覆盖。召唤物这个属性上没有别的修饰符时，最终值就是 {@code value}；有别的加成（装备、药水等）
     * 会叠在 {@code value} 上面。改最大生命时顺手回满血。
     */
    public static void setStat(Mob summon, Holder<Attribute> attribute, double value) {
        AttributeInstance attr = summon.getAttribute(attribute);
        if (attr == null) {
            return;
        }
        attr.removeModifier(SNAPSHOT_MODIFIER_ID);
        attr.addPermanentModifier(new AttributeModifier(SNAPSHOT_MODIFIER_ID, value - attr.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
        if (attribute.is(Attributes.MAX_HEALTH)) {
            summon.setHealth(summon.getMaxHealth());
        }
    }

    /**
     * 在主人身边找个能站的地方（离主人 {@code minRadius} ~ {@code maxRadius} 格的一圈，脚下有方块、身子不卡进方块、
     * 不泡在水或岩浆里），找不到就是主人脚下。
     * 只算位置，不摆实体。
     */
    public static Vec3 findSpawnPos(LivingEntity owner, Mob summon, double minRadius, double maxRadius) {
        return findSpawnPos(owner.level(), owner.position(), summon, minRadius, maxRadius);
    }

    public static Vec3 findSpawnPos(Level level, Vec3 center, Mob summon, double minRadius, double maxRadius) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = summon.getRandom().nextDouble() * Math.PI * 2.0D;
            double r = Mth.lerp(summon.getRandom().nextDouble(), minRadius, maxRadius);
            double x = center.x + Math.cos(angle) * r;
            double z = center.z + Math.sin(angle) * r;
            BlockPos base = BlockPos.containing(x, center.y, z);
            for (int dy = 2; dy >= -3; dy--) {
                BlockPos feet = base.above(dy);
                if (!level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) {
                    continue;
                }
                Vec3 pos = new Vec3(x, feet.getY(), z);
                AABB box = summon.getDimensions(summon.getPose()).makeBoundingBox(pos);
                if (level.noCollision(summon, box) && !level.containsAnyLiquid(box)) {
                    return pos;
                }
            }
        }
        return center;
    }

    // ---------------------------------------------------------------- 内部

    /** 名单变了：同步给主人自己的客户端（只有玩家主人收得到，生物主人没人收）。 */
    private static void changed(LivingEntity owner) {
        owner.syncData(AttachmentRegistry.OWNER_SUMMONS);
    }

    /** 在服务端所有维度里按 UUID 找实体（先找 {@code level} 自己）。 */
    private static @Nullable Entity find(Level level, UUID id) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity e = serverLevel.getEntity(id);
        if (e != null) {
            return e;
        }
        MinecraftServer server = serverLevel.getServer();
        for (ServerLevel other : server.getAllLevels()) {
            if (other != serverLevel && (e = other.getEntity(id)) != null) {
                return e;
            }
        }
        return null;
    }
}
