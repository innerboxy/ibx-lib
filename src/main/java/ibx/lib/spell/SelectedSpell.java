package ibx.lib.spell;

import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * 玩家当前选中的法术（附加数据 {@code ibx_lib:selected_spell}）。读写请走 {@link PlayerSpells}。
 *
 * <p>存<b>法术 id</b> 不存下标：换一件装备列表顺序就变。不可变，改选用 {@code setData} 换一份（会自动同步）。
 * 存盘（重登还选着）、只同步给自己、死亡后保留。</p>
 */
public record SelectedSpell(@Nullable Identifier spell) {
    public static final SelectedSpell NONE = new SelectedSpell(null);

    /** 存盘格式：{@code {spell: "命名空间:路径"}}，没选过时为空对象。 */
    public static final MapCodec<SelectedSpell> CODEC = Identifier.CODEC.optionalFieldOf("spell")
            .xmap(id -> new SelectedSpell(id.orElse(null)), selected -> Optional.ofNullable(selected.spell));

    public static final StreamCodec<ByteBuf, SelectedSpell> STREAM_CODEC = ByteBufCodecs.optional(Identifier.STREAM_CODEC)
            .map(id -> new SelectedSpell(id.orElse(null)), selected -> Optional.ofNullable(selected.spell));
}
