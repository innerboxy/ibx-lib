package ibx.taverntales.lib.event;

import ibx.taverntales.lib.registry.AttributeRegistry;
import net.minecraft.world.entity.LivingEntity;
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
        LivingEntity entity = event.getEntity();
        // 属性已挂到所有生物上，这里只防万一：没挂的实体直接问会经 AttributeSupplier 抛异常。
        if (entity.getAttributes().hasAttribute(AttributeRegistry.HEAL_EFFICIENCY)) {
            event.setAmount(event.getAmount() * (float) entity.getAttributeValue(AttributeRegistry.HEAL_EFFICIENCY));
        }
    }
}
