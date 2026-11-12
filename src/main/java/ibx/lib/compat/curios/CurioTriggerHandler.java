package ibx.lib.compat.curios;

import ibx.lib.api.SummonApi;
import ibx.lib.api.event.SpellCastEvent;
import ibx.lib.api.event.SummonEvent;
import ibx.lib.item.general.CurioTriggers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import org.jspecify.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link CurioTriggers} 的分发：监听各个时机，按饰品栏的槽位顺序逐件调用穿戴者身上实现了它的饰品。只在服务端。
 *
 * <ul>
 *   <li>改伤害（{@code onDealDamage} / {@code onTakeDamage}）在 {@link LivingIncomingDamageEvent} 的 {@code HIGH}：
 *       排在本模组定基础伤害（{@code HIGHEST}）之后、护甲和法术抗性之前；先攻击方、再挨打的一方。</li>
 *   <li>其余的都在 {@code LOWEST}：别的模组取消了的（格挡、免死）就不触发。</li>
 *   <li>盾击没有服务端事件，由 {@code IbxTwoHandedShieldItem#bash} 直接调 {@link #onShieldBash}。</li>
 * </ul>
 *
 * 引用了 Curios 的类，只在装了 Curios 时由本模组注册、调用（先过 {@code CuriosCompat.isLoaded()}）。
 */
public final class CurioTriggerHandler {
    private CurioTriggerHandler() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, CurioTriggerHandler::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CurioTriggerHandler::onDamagePost);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CurioTriggerHandler::onDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CurioTriggerHandler::onSpellCastPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CurioTriggerHandler::onSpellCastPost);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CurioTriggerHandler::onSummonPost);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CurioTriggerHandler::onShieldBlock);
    }

    /** 饰品栏里的一件：所在格子 + 物品。 */
    private record Worn(SlotContext slot, ItemStack stack, CurioTriggers triggers) {}

    /** 穿戴者饰品栏里实现了 {@link CurioTriggers} 的饰品，按槽位顺序；先拍一份快照，回调里换了饰品也不影响这一轮。 */
    private static List<Worn> worn(@Nullable Entity entity) {
        if (!(entity instanceof LivingEntity living) || living.level().isClientSide()) {
            return List.of();
        }
        List<Worn> out = new ArrayList<>();
        CuriosApi.getCuriosInventory(living).ifPresent(inventory -> {
            for (Map.Entry<String, ICurioStacksHandler> entry : inventory.getCurios().entrySet()) {
                IDynamicStackHandler stacks = entry.getValue().getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (!stack.isEmpty() && stack.getItem() instanceof CurioTriggers triggers) {
                        boolean visible = i < entry.getValue().getRenders().size() && entry.getValue().getRenders().get(i);
                        out.add(new Worn(new SlotContext(entry.getKey(), living, i, false, visible), stack, triggers));
                    }
                }
            }
        });
        return out;
    }

    /** 记在谁头上：伤害来源的实体，召唤物换成它的主人。 */
    private static @Nullable Entity creditedKiller(DamageSource source) {
        Entity killer = source.getEntity();
        if (killer != null && SummonApi.isSummon(killer)) {
            LivingEntity owner = SummonApi.getOwner(killer);
            return owner != null ? owner : killer;
        }
        return killer;
    }

    // ---------------------------------------------------------------- 伤害

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        float amount = event.getAmount();
        for (Worn w : worn(source.getEntity())) {
            amount = w.triggers.onDealDamage(w.slot, w.stack, target, source, amount);
        }
        for (Worn w : worn(target)) {
            amount = w.triggers.onTakeDamage(w.slot, w.stack, source, amount);
        }
        if (amount != event.getAmount()) {
            event.setAmount(Math.max(0.0F, amount));
        }
    }

    private static void onDamagePost(LivingDamageEvent.Post event) {
        float amount = event.getInflictedDamage();
        if (amount <= 0.0F) {
            return;
        }
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        for (Worn w : worn(source.getEntity())) {
            w.triggers.onDamageDealt(w.slot, w.stack, target, source, amount);
        }
        for (Worn w : worn(target)) {
            w.triggers.onDamageTaken(w.slot, w.stack, source, amount);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        for (Worn w : worn(creditedKiller(event.getSource()))) {
            w.triggers.onKill(w.slot, w.stack, event.getEntity(), event.getSource());
        }
    }

    // ---------------------------------------------------------------- 施法和召唤

    private static void onSpellCastPre(SpellCastEvent.Pre event) {
        for (Worn w : worn(event.getCaster())) {
            w.triggers.onSpellCastStart(w.slot, w.stack, event);
        }
    }

    private static void onSpellCastPost(SpellCastEvent.Post event) {
        for (Worn w : worn(event.getCaster())) {
            w.triggers.onSpellCast(w.slot, w.stack, event);
        }
    }

    private static void onSummonPost(SummonEvent.Post event) {
        for (Worn w : worn(event.getOwner())) {
            w.triggers.onSummon(w.slot, w.stack, event.getSummon());
        }
    }

    // ---------------------------------------------------------------- 格挡和盾击

    private static void onShieldBlock(LivingShieldBlockEvent event) {
        if (!event.getBlocked() || event.getBlockedDamage() <= 0.0F) {
            return;
        }
        for (Worn w : worn(event.getEntity())) {
            w.triggers.onBlock(w.slot, w.stack, event.getDamageSource(), event.getBlockedDamage());
        }
    }

    /** 盾击之后，由盾的服务端流程调用。 */
    public static void onShieldBash(LivingEntity wearer, ItemStack shield, List<LivingEntity> hits) {
        List<LivingEntity> view = List.copyOf(hits);
        for (Worn w : worn(wearer)) {
            w.triggers.onShieldBash(w.slot, w.stack, shield, view);
        }
    }
}
