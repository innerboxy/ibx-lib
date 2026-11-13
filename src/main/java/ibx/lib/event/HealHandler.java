package ibx.lib.event;

import ibx.lib.registry.AttributeRegistry;
import ibx.lib.util.AttributeUtil;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * 本模组统一的治疗处理入口，定位同 {@link DamageHandler}：治疗相关的结算都写在这里。
 */
public final class HealHandler {
    private HealHandler() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(HealHandler::onHeal);
        NeoForge.EVENT_BUS.addListener(HealHandler::onEntityTick);
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

    /**
     * 生命回复：每秒（按实体自己的 {@code tickCount}，每 20 tick）回复 {@code health_regeneration} 点生命。
     * 走 {@code heal}，所以再经过上面的回复效率，也能被别的模组的 {@code LivingHealEvent} 改。
     * 只在服务端；属性为 0（默认）、满血或已经在死的时候不结算，普通生物每秒只多读一次属性。
     */
    private static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide()
                || entity.tickCount % 20 != 0 || entity.isDeadOrDying()) {
            return;
        }
        float amount = (float) AttributeUtil.read(entity, AttributeRegistry.HEALTH_REGENERATION);
        if (amount > 0.0F && entity.getHealth() < entity.getMaxHealth()) {
            entity.heal(amount);
        }
    }
}
