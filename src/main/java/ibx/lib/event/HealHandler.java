package ibx.lib.event;

import ibx.lib.registry.AttributeRegistry;
import ibx.lib.util.AttributeUtil;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

/**
 * 本模组统一的治疗处理入口，定位同 {@link DamageHandler}：治疗相关的结算都写在这里。
 */
public final class HealHandler {
    private HealHandler() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(HealHandler::onHeal);
    }

    /**
     * 回复效率：实际回复量 = 回复量 × {@code heal_efficiency}。
     *
     * <p>原版走 {@code LivingEntity#heal} 的回复（饱食度自然回复、生命恢复、瞬间治疗等）都经过这里；
     * 直接调 {@code setHealth} 的（如不死图腾）不经过，不受影响。</p>
     */
    private static void onHeal(LivingHealEvent event) {
        event.setAmount(event.getAmount() * (float) AttributeUtil.read(event.getEntity(), AttributeRegistry.HEAL_EFFICIENCY));
    }
}
