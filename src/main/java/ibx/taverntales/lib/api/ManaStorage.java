package ibx.taverntales.lib.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 实体身上的当前法力值（附加数据 {@code taverntales_l1b:mana} 的内容）。读写请走 {@link ManaApi}。
 *
 * <p>做成可变对象而不是直接存 Float：回复每 tick 都在变，{@code setData} 每次都会自动同步，
 * 那样每秒要发 20 个包。原地修改后由 {@link ManaApi} 决定什么时候手动同步。</p>
 */
public final class ManaStorage {
    /** 存盘格式：{@code {value: 法力}}（26.1 起附加数据只接受 MapCodec）。 */
    public static final MapCodec<ManaStorage> CODEC =
            Codec.FLOAT.fieldOf("value").xmap(ManaStorage::new, storage -> storage.mana);

    /** 同步格式：一个 float。 */
    public static final StreamCodec<ByteBuf, ManaStorage> STREAM_CODEC =
            ByteBufCodecs.FLOAT.map(ManaStorage::new, storage -> storage.mana);

    float mana;
    /** 上次同步给客户端的值，只在服务端用，不存盘。 */
    float lastSynced;

    public ManaStorage() {
        this(0.0F);
    }

    public ManaStorage(float mana) {
        this.mana = mana;
        this.lastSynced = mana;
    }

    public float get() {
        return this.mana;
    }
}
