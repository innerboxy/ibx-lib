package ibx.lib.summon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * 召唤物身上的框架数据（附加数据 {@code ibx_lib:summon}）：谁召的、哪个法术召的、占几个名额、什么时候到期。
 * 没有这份数据（默认值 {@link #NONE}）的实体不是召唤物。
 *
 * <ul>
 *   <li>到期时刻是绝对世界时刻（同法术冷却），-1 = 常驻。</li>
 *   <li>存盘：跨维度搬运时随实体数据一起复制过去，读档后照常认主人。不同步客户端。</li>
 *   <li>读写请走 {@link ibx.lib.api.SummonApi}。</li>
 * </ul>
 */
public final class SummonData {
    public static final SummonData NONE = new SummonData(null, null, 0, -1L);

    public static final MapCodec<SummonData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(d -> Optional.ofNullable(d.owner)),
            Identifier.CODEC.optionalFieldOf("spell").forGetter(d -> Optional.ofNullable(d.spell)),
            Codec.INT.optionalFieldOf("cost", 1).forGetter(d -> d.cost),
            Codec.LONG.optionalFieldOf("expires", -1L).forGetter(d -> d.expiresAt)
    ).apply(i, (owner, spell, cost, expires) -> new SummonData(owner.orElse(null), spell.orElse(null), cost, expires)));

    private final @Nullable UUID owner;
    private final @Nullable Identifier spell;
    private final int cost;
    private final long expiresAt;
    /** 连续几次查不到主人（不存盘）：读档时主人可能比召唤物晚加载，连着几次查不到才当主人没了。 */
    private int ownerMisses;

    public SummonData(@Nullable UUID owner, @Nullable Identifier spell, int cost, long expiresAt) {
        this.owner = owner;
        this.spell = spell;
        this.cost = Math.max(0, cost);
        this.expiresAt = expiresAt;
    }

    public boolean isSummon() {
        return this.owner != null;
    }

    public @Nullable UUID owner() {
        return this.owner;
    }

    /** 召它的法术 id；不是法术召的（比如 Boss 招式直接召）为 null。 */
    public @Nullable Identifier spell() {
        return this.spell;
    }

    /** 占主人几个召唤物名额。 */
    public int cost() {
        return this.cost;
    }

    /** 到期的世界时刻，-1 = 常驻。 */
    public long expiresAt() {
        return this.expiresAt;
    }

    public boolean isPermanent() {
        return this.expiresAt < 0L;
    }

    /** 记一次查不到主人，返回连续次数；查到了调 {@link #ownerFound}。 */
    public int ownerMissed() {
        return ++this.ownerMisses;
    }

    public void ownerFound() {
        this.ownerMisses = 0;
    }
}
