package ibx.taverntales.lib.api;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * 多段命中的间隔：记每个目标下次能被打中的时刻，同一目标在间隔内只算一次。
 *
 * <p>范围、引导法术每 tick 都在找目标，开了 {@code leaveNoIFrames}（打完不留无敌帧）以后原版无敌帧不再限流，
 * 就靠它控制每个目标多久挨一下：法术实体持有一个，命中前问 {@link #tryHit}。
 * 不开 {@code leaveNoIFrames} 时原版无敌帧本身就限 10 tick 一下，用不着它。</p>
 *
 * <p>不存盘：区块重新加载后最多让目标早挨一下，换来实现简单。</p>
 */
public final class HitTracker {
    /** 记录超过这个数时顺手清掉过期的，避免长时间存在的法术实体越记越多。 */
    private static final int PRUNE_THRESHOLD = 32;

    private final int intervalTicks;
    private final Object2LongMap<UUID> nextHitTick = new Object2LongOpenHashMap<>();

    /** @param intervalTicks 同一目标两次命中之间至少隔多少 tick，最少 1 */
    public HitTracker(int intervalTicks) {
        this.intervalTicks = Math.max(1, intervalTicks);
        this.nextHitTick.defaultReturnValue(Long.MIN_VALUE);
    }

    /** 现在能不能打中这个目标；能就记下这一次并返回 true。 */
    public boolean tryHit(Entity target) {
        long now = target.level().getGameTime();
        UUID id = target.getUUID();
        if (now < this.nextHitTick.getLong(id)) {
            return false;
        }
        if (this.nextHitTick.size() >= PRUNE_THRESHOLD) {
            this.nextHitTick.object2LongEntrySet().removeIf(entry -> entry.getLongValue() <= now);
        }
        this.nextHitTick.put(id, now + this.intervalTicks);
        return true;
    }

    /** 清空记录，所有目标都能立刻再被打中。 */
    public void clear() {
        this.nextHitTick.clear();
    }
}
