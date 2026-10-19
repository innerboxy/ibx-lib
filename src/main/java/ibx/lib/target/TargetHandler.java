package ibx.lib.target;

import ibx.lib.IbxLib;
import ibx.lib.network.TargetIndicatorPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 「当前锁定」的服务端事件：每 tick 校验、重选并把地上的提示发给看得见的玩家；{@link TargetingItem} 开始使用时选目标、停用后放锁。
 *
 * <p>提示只在锁定活过一 tick 之后才发（瞬发的施法当场开始又结束，不画），变了就发，没变也每 {@value #RESEND_TICKS} tick 重发一次，
 * 中途走进视野的玩家也能收到。</p>
 */
public final class TargetHandler {
    private TargetHandler() {}

    /** 没变也隔这么多 tick 重发一次提示。 */
    private static final int RESEND_TICKS = 20;
    /** 地点型没声明范围时，落点画多大的圆。 */
    private static final float POINT_MARK_RADIUS = 0.5F;

    public static final String NO_TARGET = "message." + IbxLib.MODID + ".target.no_target";
    public static final String TARGET_LOST = "message." + IbxLib.MODID + ".target.lost";

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TargetHandler::onEntityTick);
        NeoForge.EVENT_BUS.addListener(TargetHandler::onUseItemStart);
    }

    /** 一次物品使用（锁定的来源）。按物品种类认：使用中原版可能把手上的那一份换成新的。 */
    private record ItemUse(Item item) {
    }

    private static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity holder) || holder.level().isClientSide()) {
            return;
        }
        TargetLock lock = TargetApi.getLock(holder);
        if (lock == null) {
            return;
        }
        if (lock.owner instanceof ItemUse use && (!holder.isUsingItem() || !holder.getUseItem().is(use.item()))) {
            TargetApi.release(holder, lock.owner);
            return;
        }
        if (TargetApi.tick(holder, lock)) {
            sync(holder, lock);
        }
    }

    /** {@link TargetingItem} 开始使用：选、锁；没指着就不开始使用。 */
    private static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        LivingEntity user = event.getEntity();
        ItemStack stack = event.getItem();
        if (user.level().isClientSide() || !(stack.getItem() instanceof TargetingItem item)) {
            return;
        }
        Targeting targeting = item.targeting(stack, user);
        if (!targeting.needsLock()) {
            return;
        }
        ItemUse use = new ItemUse(stack.getItem());
        TargetLock lock = TargetApi.begin(user, targeting, null, use, () -> {
            if (user.isUsingItem() && user.getUseItem().is(use.item())) {
                user.stopUsingItem();
                message(user, TARGET_LOST);
            }
        });
        if (lock == null) {
            event.setCanceled(true);
            message(user, NO_TARGET);
        }
    }

    /** 玩家的动作栏提示。 */
    public static void message(LivingEntity holder, String key) {
        if (holder instanceof ServerPlayer player) {
            player.sendOverlayMessage(Component.translatable(key));
        }
    }

    // ---------------------------------------------------------------- 提示

    /** 锁定现在该画什么；不画为 null。 */
    static @Nullable TargetIndicator indicatorOf(LivingEntity holder, TargetLock lock) {
        Targeting targeting = lock.targeting;
        if (targeting.isHidden()) {
            return null;
        }
        return switch (targeting.kind()) {
            case UNIT -> lock.current != null ? TargetIndicator.footprint(lock.current.getId(), targeting.faction()) : null;
            case POINT -> lock.currentPoint == null ? null : targeting.area() == Targeting.Area.CIRCLE
                    ? TargetIndicator.circleAt(lock.currentPoint, targeting.areaRadius(), targeting.areaFaction())
                    : TargetIndicator.circleAt(lock.currentPoint, POINT_MARK_RADIUS, Faction.ALL);
            case SELF -> switch (targeting.area()) {
                case CIRCLE -> TargetIndicator.circleOn(holder.getId(), targeting.areaRadius(), targeting.areaFaction());
                case FAN -> TargetIndicator.fanOn(holder.getId(), targeting.areaRadius(), targeting.areaHalfAngle(), targeting.areaFaction());
                case RECT -> TargetIndicator.rectOn(holder.getId(), targeting.areaAhead(), targeting.areaRadius(), targeting.areaWidth(),
                        targeting.areaFaction());
                case NONE -> null;
            };
            case DIRECTION -> null;
        };
    }

    /** 提示变了、或者到了重发的时候，发给看得见的玩家。 */
    private static void sync(LivingEntity holder, TargetLock lock) {
        TargetIndicator indicator = indicatorOf(holder, lock);
        lock.sentAge++;
        if (indicator == null && lock.sent == null) {
            return;
        }
        if (indicator != null && indicator.equals(lock.sent) && lock.sentAge < RESEND_TICKS) {
            return;
        }
        send(holder, indicator);
        lock.sent = indicator;
        lock.sentAge = 0;
    }

    /** 锁定没了：发过提示的话通知客户端不画了。 */
    static void hide(LivingEntity holder, TargetLock lock) {
        if (lock.sent != null) {
            send(holder, null);
            lock.sent = null;
        }
    }

    /** 施法者对每个玩家来说是谁不一样，逐个算好再发。 */
    private static void send(LivingEntity holder, @Nullable TargetIndicator indicator) {
        if (!(holder.level() instanceof ServerLevel level)) {
            return;
        }
        List<ServerPlayer> viewers = new ArrayList<>(level.getChunkSource().chunkMap.getPlayersWatching(holder));
        if (holder instanceof ServerPlayer self && !viewers.contains(self)) {
            viewers.add(self);
        }
        boolean isPlayer = holder instanceof Player;
        for (ServerPlayer viewer : viewers) {
            TargetIndicatorPayload.Relation relation = viewer == holder ? TargetIndicatorPayload.Relation.SELF
                    : TargetApi.canHarm(viewer, holder) ? TargetIndicatorPayload.Relation.ENEMY : TargetIndicatorPayload.Relation.ALLY;
            PacketDistributor.sendToPlayer(viewer, new TargetIndicatorPayload(holder.getId(), isPlayer, relation, indicator));
        }
    }
}
