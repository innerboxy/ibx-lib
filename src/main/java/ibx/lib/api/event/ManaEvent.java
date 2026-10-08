package ibx.lib.api.event;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * 法力相关事件，都发在 {@code NeoForge.EVENT_BUS} 上，只在服务端触发。
 * 其他模组和 KubeJS 可以借它们介入法力的消耗与获得。
 */
public abstract class ManaEvent extends Event {
    private final LivingEntity entity;

    protected ManaEvent(LivingEntity entity) {
        this.entity = entity;
    }

    public LivingEntity getEntity() {
        return this.entity;
    }

    /**
     * 消耗前（{@code ManaApi.tryConsume}）：可取消（这次消耗失败、不扣），也可改实际消耗量。
     * 收到的消耗量已经乘过法力消耗倍率。创造模式不扣法力，也不触发本事件。
     */
    public static final class Consume extends ManaEvent implements ICancellableEvent {
        private float cost;

        public Consume(LivingEntity entity, float cost) {
            super(entity);
            this.cost = cost;
        }

        public float getCost() {
            return this.cost;
        }

        public void setCost(float cost) {
            this.cost = cost;
        }
    }

    /**
     * 获得前（{@code ManaApi.add}，如药水、击中回蓝、退还）：可改获得量，负数表示扣减。
     * 自然回复不触发本事件（它按属性结算，要改请挂属性修饰符）。
     */
    public static final class Gain extends ManaEvent {
        private float amount;

        public Gain(LivingEntity entity, float amount) {
            super(entity);
            this.amount = amount;
        }

        public float getAmount() {
            return this.amount;
        }

        public void setAmount(float amount) {
            this.amount = amount;
        }
    }

    /**
     * 变化后：只通知，不能改。包括自然回复在内的任何变化都会触发，所以回复中每 tick 都可能来一次。
     */
    public static final class Changed extends ManaEvent {
        private final float oldValue;
        private final float newValue;

        public Changed(LivingEntity entity, float oldValue, float newValue) {
            super(entity);
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public float getOldValue() {
            return this.oldValue;
        }

        public float getNewValue() {
            return this.newValue;
        }
    }
}
