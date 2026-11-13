package ibx.lib.api;

import ibx.lib.item.IbxHeavyShieldItem;
import ibx.lib.registry.AttachmentRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import org.jspecify.annotations.Nullable;

/**
 * 盾击的统一入口（状态见 {@link ShieldBash}，物品见 {@link IbxHeavyShieldItem}）。
 *
 * <p>流程：主手举盾中客户端按下攻击键（{@code client.ShieldInput} 吃掉这一下）→ 发 {@code ShieldBashPayload} →
 * {@link #tryBash} 校验 → {@link IbxHeavyShieldItem#bash}。冷却是时刻比较，不用 tick。</p>
 *
 * <p>举着双手盾时左键只能是盾击：客户端吃掉按键（{@code client.ShieldInput}），服务端再把这期间到达的普通攻击
 * （Better Combat 的攻击是客户端判定后发包、服务端调 {@code Player#attack}）一并取消，见 {@link #onAttackEntity}。</p>
 */
public final class ShieldApi {
    private ShieldApi() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ShieldApi::onAttackEntity);
    }

    public static @Nullable ShieldBash getState(LivingEntity entity) {
        return entity.getExistingDataOrNull(AttachmentRegistry.SHIELD_BASH);
    }

    /** 盾击冷却过了没。两端都能读（状态同步给自己）。 */
    public static boolean isBashReady(LivingEntity entity) {
        ShieldBash state = getState(entity);
        return state == null || entity.level().getGameTime() >= state.cooldownUntil();
    }

    /** 主手举着一面双手盾（盾击的前提；客户端据此决定吃不吃掉左键）。两端都能读。 */
    public static boolean isRaisingHeavyShield(LivingEntity entity) {
        return entity.getMainHandItem().getItem() instanceof IbxHeavyShieldItem
                && entity.isUsingItem() && entity.getUsedItemHand() == InteractionHand.MAIN_HAND;
    }

    /** 客户端在主手举盾中按了左键：还在举、不在冷却就出盾击。只在服务端调。 */
    public static void tryBash(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IbxHeavyShieldItem shield) || !isRaisingHeavyShield(player) || !isBashReady(player)) {
            return;
        }
        shield.bash(player, stack);
    }

    /** 举着双手盾时不许普通攻击（两端都拦：客户端的 {@code Player#attack} 也走这个事件）。 */
    private static void onAttackEntity(AttackEntityEvent event) {
        if (isRaisingHeavyShield(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** 记下这次盾击的时刻和冷却，同步出去（客户端据此放动作）。只在服务端调（{@link IbxHeavyShieldItem#bash}）。 */
    public static void markBash(LivingEntity entity, int cooldownTicks) {
        long now = entity.level().getGameTime();
        entity.setData(AttachmentRegistry.SHIELD_BASH, new ShieldBash(now, now + cooldownTicks));
        entity.syncData(AttachmentRegistry.SHIELD_BASH);
    }
}
