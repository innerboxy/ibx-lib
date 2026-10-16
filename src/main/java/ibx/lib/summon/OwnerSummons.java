package ibx.lib.summon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 主人身上的召唤物名单（附加数据 {@code ibx_lib:owner_summons}）：按召唤顺序排，最早的在前，超上限时从前面挤掉。
 * 每条记着召唤物的 UUID 和它占几个名额（召唤物没加载时也能算已用名额）。
 *
 * <ul>
 *   <li>名单存盘（生物当主人时和召唤物一起存进世界）；标记目标不存盘。名单只同步给主人自己（轮盘按它决定显不显示「解散召唤物」），
 *       名单一变由 {@link ibx.lib.api.SummonApi} 发同步。</li>
 *   <li>名单里有、世界里找不到的条目在查询时顺手清掉；召唤物发现自己不在主人名单里就消散。</li>
 *   <li>读写请走 {@link ibx.lib.api.SummonApi}。</li>
 * </ul>
 */
public final class OwnerSummons {
    /** 名单里的一条：召唤物 UUID、占几个名额。 */
    public record Entry(UUID id, int cost) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Entry::id),
                Codec.INT.optionalFieldOf("cost", 1).forGetter(Entry::cost)
        ).apply(i, Entry::new));

        static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Entry::id,
                ByteBufCodecs.VAR_INT, Entry::cost,
                Entry::new);
    }

    public static final MapCodec<OwnerSummons> CODEC = Entry.CODEC.listOf()
            .optionalFieldOf("summons", List.of())
            .xmap(OwnerSummons::new, summons -> summons.entries);

    /** 同步给主人自己的客户端：只带名单（轮盘要知道有没有召唤物），不带标记目标。 */
    public static final StreamCodec<ByteBuf, OwnerSummons> STREAM_CODEC = Entry.STREAM_CODEC.apply(ByteBufCodecs.list())
            .map(OwnerSummons::new, summons -> summons.entries);

    private final List<Entry> entries;
    /** 主人最近打中的生物（召唤物优先打它）和打中的世界时刻，不存盘。 */
    private @Nullable UUID marked;
    private long markedAt;

    public OwnerSummons() {
        this(List.of());
    }

    private OwnerSummons(List<Entry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    /** 名单的只读副本，最早召唤的在前。 */
    public List<Entry> entries() {
        return List.copyOf(this.entries);
    }

    /** 名单里所有召唤物占的名额之和。 */
    public int used() {
        int sum = 0;
        for (Entry e : this.entries) {
            sum += e.cost();
        }
        return sum;
    }

    public boolean contains(UUID id) {
        for (Entry e : this.entries) {
            if (e.id().equals(id)) return true;
        }
        return false;
    }

    /** 加到名单末尾。只给 {@link ibx.lib.api.SummonApi} 用。 */
    public void add(UUID id, int cost) {
        this.entries.add(new Entry(id, cost));
    }

    /** 从名单里划掉，返回原来在不在。只给 {@link ibx.lib.api.SummonApi} 用。 */
    public boolean remove(UUID id) {
        return this.entries.removeIf(e -> e.id().equals(id));
    }

    public @Nullable UUID marked() {
        return this.marked;
    }

    /** 标记的世界时刻（最近一次打中）。 */
    public long markedAt() {
        return this.markedAt;
    }

    /** 只给 {@link ibx.lib.api.SummonApi} 用。 */
    public void setMarked(@Nullable UUID marked, long time) {
        this.marked = marked;
        this.markedAt = time;
    }
}
