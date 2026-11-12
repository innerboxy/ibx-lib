package ibx.lib.summon;

import ibx.lib.api.SummonApi;
import ibx.lib.api.event.SpellListEvent;
import ibx.lib.registry.AttachmentRegistry;
import ibx.lib.registry.SpellRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * 召唤物框架的事件监听（都只在服务端起作用）：
 * <ul>
 *   <li>召唤物每 tick 查到期；每秒查一次主人（找不到、名单里没有自己 → 离场；主人在别的维度 → 搬过去）；</li>
 *   <li>主人死亡、下线 → 全部离场；玩家主人换维度 → 召唤物跟着搬；召唤物自己不走传送门；</li>
 *   <li>召唤物被打死（死亡那一刻，不等倒地动画）/ 被别的代码移除 → 从主人名单里划掉；</li>
 *   <li>友伤：召唤物打主人和同一主人的召唤物、主人打自己的召唤物，一律取消；玩家主人的召唤物算作主人本人，按玩家打玩家判断（关了 PvP、队友且关了友伤时打不到），两个方向都管：别人打不到主人就打不到他的召唤物，主人打不到的人他的召唤物也打不到；</li>
 *   <li>主人打中谁就标记谁；召唤物打的伤害，主人是玩家时算玩家打的（经验、玩家击杀掉落、统计）；</li>
 *   <li>玩家身上有召唤物时，法术列表里自动多一个「解散召唤物」。</li>
 * </ul>
 */
public final class SummonHandler {
    private SummonHandler() {}

    /** 多久查一次主人（tick）。 */
    private static final int OWNER_CHECK = 20;
    /** 连续几次查不到主人才当主人没了。 */
    private static final int OWNER_MISSES = 3;
    /** 召唤物打中目标后，目标记住"被这个玩家打过"多久（tick），同原版。 */
    private static final int PLAYER_CREDIT_TICKS = 100;

    /** 框架自己在搬召唤物换维度（这时不拦传送）。 */
    private static boolean moving;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SummonHandler::onEntityTick);
        NeoForge.EVENT_BUS.addListener(SummonHandler::onLeaveLevel);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SummonHandler::onDeath);
        NeoForge.EVENT_BUS.addListener(SummonHandler::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(SummonHandler::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(SummonHandler::onTravelToDimension);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SummonHandler::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(SummonHandler::onDamagePost);
        NeoForge.EVENT_BUS.addListener(SummonHandler::onSpellList);
    }

    // ---------------------------------------------------------------- 解散法术

    /**
     * 玩家身上有召唤物时，法术列表末尾自动多一个「解散召唤物」，没有就不出现。两端都按同步给主人自己的召唤物名单判断，
     * 轮盘和服务端校验算出来一样。
     */
    private static void onSpellList(SpellListEvent event) {
        Player player = event.getPlayer();
        if (player.hasData(AttachmentRegistry.OWNER_SUMMONS) && !player.getData(AttachmentRegistry.OWNER_SUMMONS).isEmpty()) {
            event.add(SpellRegistry.DISMISS_SUMMONS, ItemStack.EMPTY);
        }
    }

    // ---------------------------------------------------------------- 召唤物自己

    private static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob summon) || !(summon.level() instanceof ServerLevel level)
                || !summon.hasData(AttachmentRegistry.SUMMON) || summon.isRemoved()) {
            return;
        }
        SummonData data = summon.getData(AttachmentRegistry.SUMMON);
        if (!data.isSummon()) {
            return;
        }
        if (!data.isPermanent() && level.getGameTime() >= data.expiresAt()) {
            SummonApi.dismiss(summon, UnsummonReason.EXPIRED);
            return;
        }
        if (summon.tickCount % OWNER_CHECK != 0) {
            return;
        }
        LivingEntity owner = SummonApi.getOwner(summon);
        if (owner == null) {
            if (data.ownerMissed() >= OWNER_MISSES) {
                SummonApi.dismiss(summon, UnsummonReason.OWNER_GONE);
            }
            return;
        }
        data.ownerFound();
        if (!owner.hasData(AttachmentRegistry.OWNER_SUMMONS) || !owner.getData(AttachmentRegistry.OWNER_SUMMONS).contains(summon.getUUID())) {
            SummonApi.dismiss(summon, UnsummonReason.OWNER_GONE);
        } else if (!owner.isAlive()) {
            SummonApi.dismiss(summon, UnsummonReason.OWNER_DIED);
        } else if (owner.level() != summon.level() && owner.level() instanceof ServerLevel ownerLevel) {
            moveTo(summon, ownerLevel, owner.position());
        }
    }

    /**
     * 被别的代码移除（不是卸载、不是换维度）：从主人名单里划掉。被打死的在死亡那一刻已经划掉了（{@link #onDeath}），
     * 这里再划一次也没有副作用。
     */
    private static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof Mob summon) || event.getLevel().isClientSide() || !SummonApi.isSummon(summon)) {
            return;
        }
        Entity.RemovalReason reason = summon.getRemovalReason();
        if (reason == null || !reason.shouldDestroy()) {
            return;
        }
        SummonApi.forget(summon, reason == Entity.RemovalReason.KILLED ? UnsummonReason.KILLED : UnsummonReason.DISMISSED);
    }

    // ---------------------------------------------------------------- 死亡

    /**
     * 召唤物死了：当场从主人名单里划掉（不等倒地动画播完、实体移除），名额马上让出来。
     * 主人死了：他的召唤物全部离场。
     */
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (event.isCanceled() || dead.level().isClientSide()) {
            return;
        }
        if (dead instanceof Mob summon && SummonApi.isSummon(summon)) {
            SummonApi.forget(summon, UnsummonReason.KILLED);
        }
        if (dead.hasData(AttachmentRegistry.OWNER_SUMMONS)) {
            SummonApi.dismissAll(dead, UnsummonReason.OWNER_DIED);
        }
    }

    // ---------------------------------------------------------------- 主人

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SummonApi.dismissAll(event.getEntity(), UnsummonReason.OWNER_LEFT);
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        for (Mob summon : SummonApi.getSummons(player)) {
            if (summon.level() != level) {
                moveTo(summon, level, player.position());
            }
        }
    }

    /** 召唤物自己不走传送门（踩进下界门不会自己过去），框架搬运除外。 */
    private static void onTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!moving && SummonApi.isSummon(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** 把召唤物搬到另一个维度的主人身边。跨维度后是新的实体对象，UUID 和附加数据照旧。 */
    private static void moveTo(Mob summon, ServerLevel level, Vec3 ownerPos) {
        Vec3 pos = SummonApi.findSpawnPos(level, ownerPos, summon, 1.5D, 3.0D);
        moving = true;
        try {
            summon.teleport(new TeleportTransition(level, pos, Vec3.ZERO, summon.getYRot(), summon.getXRot(), TeleportTransition.DO_NOTHING));
        } finally {
            moving = false;
        }
    }

    // ---------------------------------------------------------------- 伤害

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        if (attacker == null || victim.level().isClientSide()) {
            return;
        }
        UUID attackerOwner = SummonApi.ownerId(attacker);
        UUID victimOwner = SummonApi.ownerId(victim);
        boolean friendly = attackerOwner != null && (attackerOwner.equals(victim.getUUID()) || attackerOwner.equals(victimOwner))
                || victimOwner != null && victimOwner.equals(attacker.getUUID());
        if (friendly || blockedAsPlayers(victim, attacker, attackerOwner, victimOwner)) {
            event.setCanceled(true);
            return;
        }
        if (attackerOwner != null && SummonApi.getOwner(attacker) instanceof Player player) {
            victim.setLastHurtByPlayer(player, PLAYER_CREDIT_TICKS);
        }
    }

    /**
     * 召唤物算作它的主人，按玩家打玩家判断（两个方向都管）：攻击方是召唤物就换成它的主人，被打方是召唤物也换成它的主人，
     * 两边都是玩家而前者打不到后者时（服务器关了 PvP、同队且队伍关了友伤）就不让打。
     * 所以别人打不到你时也打不到你的召唤物，你打不到的人你的召唤物也打不到。两边都不是召唤物时交给原版；生物主人（比如会召唤的 Boss）不受影响。
     */
    private static boolean blockedAsPlayers(LivingEntity victim, Entity attacker, @Nullable UUID attackerOwner, @Nullable UUID victimOwner) {
        if (attackerOwner == null && victimOwner == null) {
            return false;
        }
        Entity attackerSide = attackerOwner != null ? SummonApi.getOwner(attacker) : attacker;
        Entity victimSide = victimOwner != null ? SummonApi.getOwner(victim) : victim;
        return attackerSide instanceof Player a && victimSide instanceof Player v && a != v && !a.canHarmPlayer(v);
    }

    /** 主人打中谁就标记谁（真的扣到血了才算）。 */
    private static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || event.getInflictedDamage() <= 0.0F
                || !(event.getSource().getEntity() instanceof LivingEntity owner) || owner == victim
                || !owner.hasData(AttachmentRegistry.OWNER_SUMMONS) || owner.getData(AttachmentRegistry.OWNER_SUMMONS).isEmpty()
                || SummonApi.isOwnedBy(victim, owner)) {
            return;
        }
        SummonApi.mark(owner, victim);
    }
}
