package ibx.taverntales.lib.event;

import ibx.taverntales.lib.api.ManaApi;
import ibx.taverntales.lib.registry.AttachmentRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * 法力的回复与流失只在这里结算（服务端，每 tick 一次），不要另开监听。
 *
 * <p>玩家始终参与：第一次 tick 时创建法力数据，从 0 开始回复。其他生物只有带着法力数据
 * （用过法力）时才参与，普通生物零开销。</p>
 */
public final class ManaHandler {
    private ManaHandler() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ManaHandler::onEntityTick);
    }

    private static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide() || entity.isDeadOrDying()) {
            return;
        }
        if (entity instanceof Player || entity.hasData(AttachmentRegistry.MANA)) {
            ManaApi.tickRegeneration(entity);
        }
    }
}
