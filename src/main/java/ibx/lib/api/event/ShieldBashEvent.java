package ibx.lib.api.event;

import ibx.lib.api.ShieldBash;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

/**
 * 客户端：某个生物出了一次盾击，发在 {@code NeoForge.EVENT_BUS} 上，<b>只在客户端触发</b>（盾击状态同步到达时）。
 * 自己和周围玩家的都会收到，盾击动作靠它。
 */
public final class ShieldBashEvent extends Event {
    private final LivingEntity entity;
    private final ShieldBash state;

    public ShieldBashEvent(LivingEntity entity, ShieldBash state) {
        this.entity = entity;
        this.state = state;
    }

    public LivingEntity getEntity() {
        return this.entity;
    }

    public ShieldBash getState() {
        return this.state;
    }
}
