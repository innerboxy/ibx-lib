package ibx.lib.gearset;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 穿戴者身上的套装状态：各套装生效中的件数，和手持件离手后的保留。只在服务端用，由 {@link GearSetHandler} 维护；
 * 读件数请走 {@link GearSets#count}。
 */
public final class GearSetState {
    /** 套装 → 生效中的件数，只记大于 0 的。 */
    Map<GearSet, Integer> counts = new IdentityHashMap<>();
    /** 上一次统计时靠手上的物品算上的件位。 */
    Set<GearSet.Piece> held = Collections.newSetFromMap(new IdentityHashMap<>());
    /** 离了手、还在保留期里的件位 → 保留到哪个世界时刻。 */
    final Map<GearSet.Piece, Long> graceUntil = new IdentityHashMap<>();
    /** 最早到期的保留是哪个时刻，没有就是 {@link Long#MAX_VALUE}。 */
    long nextExpiry = Long.MAX_VALUE;
    /** 生效中的档位里有没有常驻效果（有才需要每刻补效果）。 */
    boolean hasEffects;
    /** 生效中的档位带的触发钩子，按套装注册顺序、档位从低到高；每次重数换一份新的。 */
    List<SetTriggers> triggers = List.of();

    int count(GearSet set) {
        return counts.getOrDefault(set, 0);
    }
}
