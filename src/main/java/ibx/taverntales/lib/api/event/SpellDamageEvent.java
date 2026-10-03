package ibx.taverntales.lib.api.event;

import ibx.taverntales.lib.api.SpellDamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * 法术伤害命中前（{@code SpellDamageApi.hurt}），发在 {@code NeoForge.EVENT_BUS} 上，只在服务端触发。
 * 其他模组和 KubeJS 可以借它取消这次伤害或改伤害量。
 *
 * <p>此时友伤判定已通过、暴击已掷完（伤害量已乘暴击倍率，结果见 {@link SpellDamageSource#isCritical()}），
 * 还没进原版受伤流程：之后还有法术抗性、抗性药水、保护附魔、伤害吸收等减免。</p>
 */
public final class SpellDamageEvent extends Event implements ICancellableEvent {
    private final LivingEntity entity;
    private final SpellDamageSource source;
    private float amount;

    public SpellDamageEvent(LivingEntity entity, SpellDamageSource source, float amount) {
        this.entity = entity;
        this.source = source;
        this.amount = amount;
    }

    /** 受到伤害的目标。 */
    public LivingEntity getEntity() {
        return this.entity;
    }

    public SpellDamageSource getSource() {
        return this.source;
    }

    public float getAmount() {
        return this.amount;
    }

    public void setAmount(float amount) {
        this.amount = amount;
    }
}
