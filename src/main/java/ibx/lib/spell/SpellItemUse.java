package ibx.lib.spell;

import ibx.lib.network.CastRejectedPayload;
import ibx.lib.registry.TagRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 右键施法物品：手持 {@code #ibx_lib:can_cast_spell} 物品右键 = 放当前选中的法术（{@link PlayerSpells#castSelected}），
 * 并挂上原版"使用物品"——按住读条、松手结束。施法物品自己不用写任何代码。
 *
 * <p>挂着使用物品的那次施法在 {@link CastContext} 上有标记：物品离手（换了物品、打开容器）时取消、施法结束时自动停用，
 * 这两件事由 {@link SpellCasting} 在推进读条和结束时处理。</p>
 */
public final class SpellItemUse {
    private SpellItemUse() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SpellItemUse::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(SpellItemUse::onUseItemStop);
    }

    /**
     * 右键手持 {@code #can_cast_spell} 物品 = 放当前选中的法术，不走物品自己的 {@code use}。两端都会触发：
     * 客户端那次只做判断，用来同步挂上"使用物品"（手臂姿势、减速）；瞬发不挂，只挥手。
     */
    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getItemStack().is(TagRegistry.CAN_CAST_SPELL)) {
            return;
        }
        Player player = event.getEntity();
        InteractionHand hand = event.getHand();
        event.setCanceled(true);
        CastResult result = PlayerSpells.castSelected(player, hand);
        SpellEntry entry = result.isSuccess() ? PlayerSpells.getSelectedSpell(player) : null;
        event.setCancellationResult(entry == null ? InteractionResult.FAIL : holdCast(player, hand, entry.value(), result));
        // 客户端只判断了冷却、法力，没判断目标和法术条件，可能已经举起手了：服务端拒绝时让它马上松手
        if (entry == null && player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, CastRejectedPayload.INSTANCE);
        }
    }

    /**
     * 放出法术后的收尾：成功且不是瞬发时挂上原版"使用物品"——按住读条、松手结束、物品离手取消、结束时自动停用——
     * 返回 {@code CONSUME}；瞬发成功返回 {@code SUCCESS}（挥手）；失败返回 {@code FAIL}。
     *
     * <p>两端都调：客户端那次只判断（{@code spell} 用来看是不是瞬发），同步挂上使用物品（手臂姿势、减速）。
     * 使用时长由 mixin 统一给施法物品 72000。</p>
     */
    private static InteractionResult holdCast(Player player, InteractionHand hand, Spell spell, CastResult result) {
        if (!result.isSuccess()) {
            return InteractionResult.FAIL;
        }
        boolean held;
        if (player.level().isClientSide()) {
            held = spell.castType() != CastType.INSTANT;
        } else {
            CastContext ctx = SpellCasting.getCurrentCast(player);
            held = ctx != null;
            if (held) {
                ctx.heldByUse = true;
            }
        }
        if (held) {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 松手：挂着使用物品的施法在这里结束，蓄力在这里结算。
     * 这时物品自己的 {@code releaseUsing} 不再调用（弓进了标签也不会顺手射箭）；施法物品标签里的物品两端都这样处理。
     */
    private static void onUseItemStop(LivingEntityUseItemEvent.Stop event) {
        CastContext ctx = SpellCasting.getCurrentCast(event.getEntity());
        boolean held = ctx != null && ctx.heldByUse;   // 只有服务端知道
        if (held || event.getItem().is(TagRegistry.CAN_CAST_SPELL)) {
            event.setCanceled(true);
        }
        if (held) {
            SpellCasting.stop(event.getEntity(), false);
        }
    }
}
