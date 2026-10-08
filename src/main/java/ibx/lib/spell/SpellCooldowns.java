package ibx.lib.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ibx.lib.registry.AttachmentRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 法术冷却（附加数据 {@code ibx_lib:spell_cooldowns}）：法术 id → 冷却结束的世界时刻。
 *
 * <ul>
 *   <li>记的是绝对时刻（{@code level.getGameTime()}）：各维度共用同一个世界时间，换维度、存盘读档都不乱；
 *       {@code /time set} 改的是昼夜时间，不影响它。</li>
 *   <li>存盘；只同步给实体自己（玩家的 HUD、轮盘要读），生物的不同步。</li>
 *   <li>不标 copyOnDeath：死亡后冷却清零，和法力一致。</li>
 *   <li>过期的条目在开始新冷却时顺手清掉。</li>
 * </ul>
 * 读写都走本类的静态方法，两端都能读（客户端只能读本地玩家）。
 */
public final class SpellCooldowns {
    /** 一条冷却：结束时刻、总长（给冷却遮罩算进度用）。 */
    record Entry(long endTick, int totalTicks) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("end").forGetter(Entry::endTick),
                Codec.INT.fieldOf("total").forGetter(Entry::totalTicks)
        ).apply(i, Entry::new));

        static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Entry::endTick,
                ByteBufCodecs.VAR_INT, Entry::totalTicks,
                Entry::new);
    }

    /** 存盘格式：{@code {cooldowns: {法术id: {end, total}}}}（26.1 起附加数据只接受 MapCodec）。 */
    public static final MapCodec<SpellCooldowns> CODEC = Codec.unboundedMap(Identifier.CODEC, Entry.CODEC)
            .fieldOf("cooldowns").xmap(SpellCooldowns::new, cooldowns -> cooldowns.entries);

    public static final StreamCodec<ByteBuf, SpellCooldowns> STREAM_CODEC =
            ByteBufCodecs.<ByteBuf, Identifier, Entry, Map<Identifier, Entry>>map(HashMap::new, Identifier.STREAM_CODEC, Entry.STREAM_CODEC)
                    .map(SpellCooldowns::new, cooldowns -> cooldowns.entries);

    private final Map<Identifier, Entry> entries;

    public SpellCooldowns() {
        this(Map.of());
    }

    private SpellCooldowns(Map<Identifier, Entry> entries) {
        this.entries = new HashMap<>(entries);
    }

    /** 这个法术现在能不能放（冷却好了）。 */
    public static boolean isReady(LivingEntity entity, Spell spell) {
        return getRemaining(entity, spell) <= 0;
    }

    /** 剩余冷却 tick，0 为已好。 */
    public static int getRemaining(LivingEntity entity, Spell spell) {
        Entry entry = entry(entity, spell);
        return entry == null ? 0 : (int) Math.max(0L, entry.endTick() - entity.level().getGameTime());
    }

    /** 剩余比例 1 → 0（冷却遮罩用），0 为已好。 */
    public static float getRemainingFraction(LivingEntity entity, Spell spell) {
        Entry entry = entry(entity, spell);
        if (entry == null || entry.totalTicks() <= 0) {
            return 0.0F;
        }
        return Math.clamp((float) getRemaining(entity, spell) / entry.totalTicks(), 0.0F, 1.0F);
    }

    /** 开始一段冷却，覆盖原有的。只在服务端生效；{@code ticks} 小于等于 0 时什么也不做。 */
    public static void start(LivingEntity entity, Spell spell, int ticks) {
        Identifier id = spell.id();
        if (entity.level().isClientSide() || ticks <= 0 || id == null) {
            return;
        }
        long now = entity.level().getGameTime();
        SpellCooldowns cooldowns = entity.getData(AttachmentRegistry.SPELL_COOLDOWNS);
        cooldowns.entries.values().removeIf(entry -> entry.endTick() <= now);
        cooldowns.entries.put(id, new Entry(now + ticks, ticks));
        entity.syncData(AttachmentRegistry.SPELL_COOLDOWNS);
    }

    /** 清掉所有冷却。只在服务端生效。 */
    public static void clear(LivingEntity entity) {
        SpellCooldowns cooldowns = entity.getExistingDataOrNull(AttachmentRegistry.SPELL_COOLDOWNS);
        if (entity.level().isClientSide() || cooldowns == null) {
            return;
        }
        cooldowns.entries.clear();
        entity.syncData(AttachmentRegistry.SPELL_COOLDOWNS);
    }

    private static @Nullable Entry entry(LivingEntity entity, Spell spell) {
        SpellCooldowns cooldowns = entity.getExistingDataOrNull(AttachmentRegistry.SPELL_COOLDOWNS);
        Identifier id = spell.id();
        return cooldowns == null || id == null ? null : cooldowns.entries.get(id);
    }
}
