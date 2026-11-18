package ibx.lib.gearset;

import ibx.lib.api.SummonApi;
import ibx.lib.api.event.SpellCastEvent;
import ibx.lib.api.event.SummonEvent;
import ibx.lib.registry.AttachmentRegistry;
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

import java.util.List;

/**
 * {@link SetTriggers} 在战斗里的分发：监听各个时机，逐个调用穿戴者身上生效中的档位带的钩子。只在服务端。
 * 生效、失效、每刻这三个时机由 {@link GearSetHandler} 调。
 *
 * <ul>
 *   <li>改伤害（{@code onDealDamage} / {@code onTakeDamage}）在 {@link LivingIncomingDamageEvent} 的 {@code HIGH}：
 *       排在本模组定基础伤害（{@code HIGHEST}）之后、护甲和法术抗性之前；先攻击方、再挨打的一方。</li>
 *   <li>其余的都在 {@code LOWEST}：别的模组取消了的（格挡、免死）就不触发。</li>
 *   <li>优先级和饰品的钩子一样，注册在它后面，所以同一个时机先调饰品的、再调套装的。</li>
 *   <li>盾击没有服务端事件，由 {@code IbxHeavyShieldItem#bash} 直接调 {@link #onShieldBash}。</li>
 * </ul>
 */
public final class SetTriggerHandler {
    private SetTriggerHandler() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SetTriggerHandler::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SetTriggerHandler::onDamagePost);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SetTriggerHandler::onDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SetTriggerHandler::onSpellCastPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SetTriggerHandler::onSpellCastPost);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SetTriggerHandler::onSummonPost);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SetTriggerHandler::onShieldBlock);
    }

    /** 这个实体身上生效中的钩子。名单每次重数都换一份新的，回调里换了装备也不影响这一轮。 */
    private static List<SetTriggers> active(@Nullable Entity entity) {
        if (!(entity instanceof LivingEntity living) || living.level().isClientSide()) {
            return List.of();
        }
        GearSetState state = living.getExistingDataOrNull(AttachmentRegistry.GEAR_SETS);
        return state == null ? List.of() : state.triggers;
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
        if (source.getEntity() instanceof LivingEntity attacker) {
            for (SetTriggers triggers : active(attacker)) {
                amount = triggers.onDealDamage(attacker, target, source, amount);
            }
        }
        for (SetTriggers triggers : active(target)) {
            amount = triggers.onTakeDamage(target, source, amount);
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
        if (source.getEntity() instanceof LivingEntity attacker) {
            for (SetTriggers triggers : active(attacker)) {
                triggers.onDamageDealt(attacker, target, source, amount);
            }
        }
        for (SetTriggers triggers : active(target)) {
            triggers.onDamageTaken(target, source, amount);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (creditedKiller(event.getSource()) instanceof LivingEntity killer) {
            for (SetTriggers triggers : active(killer)) {
                triggers.onKill(killer, event.getEntity(), event.getSource());
            }
        }
    }

    // ---------------------------------------------------------------- 施法和召唤

    private static void onSpellCastPre(SpellCastEvent.Pre event) {
        for (SetTriggers triggers : active(event.getCaster())) {
            triggers.onSpellCastStart(event.getCaster(), event);
        }
    }

    private static void onSpellCastPost(SpellCastEvent.Post event) {
        for (SetTriggers triggers : active(event.getCaster())) {
            triggers.onSpellCast(event.getCaster(), event);
        }
    }

    private static void onSummonPost(SummonEvent.Post event) {
        LivingEntity owner = event.getOwner();
        if (owner == null) {
            return;
        }
        for (SetTriggers triggers : active(owner)) {
            triggers.onSummon(owner, event.getSummon());
        }
    }

    // ---------------------------------------------------------------- 格挡和盾击

    private static void onShieldBlock(LivingShieldBlockEvent event) {
        if (!event.getBlocked() || event.getBlockedDamage() <= 0.0F) {
            return;
        }
        for (SetTriggers triggers : active(event.getEntity())) {
            triggers.onBlock(event.getEntity(), event.getDamageSource(), event.getBlockedDamage());
        }
    }

    /** 盾击之后，由盾的服务端流程调用。 */
    public static void onShieldBash(LivingEntity wearer, ItemStack shield, List<LivingEntity> hits) {
        List<LivingEntity> view = List.copyOf(hits);
        for (SetTriggers triggers : active(wearer)) {
            triggers.onShieldBash(wearer, shield, view);
        }
    }
}
