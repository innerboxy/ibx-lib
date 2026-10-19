package ibx.lib.network;

import ibx.lib.IbxLib;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 右键施法被服务端拒绝了（服务端 → 本人）：没有目标、法术条件不满足……客户端不预测这些，右键时已经挂上了「使用物品」（举手、减速），
 * 收到后马上松手，见 {@code client.spell.SpellCastInput#onCastRejected}。处理器在客户端入口注册。
 */
public record CastRejectedPayload() implements CustomPacketPayload {
    public static final CastRejectedPayload INSTANCE = new CastRejectedPayload();

    public static final Type<CastRejectedPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(IbxLib.MODID, "cast_rejected"));

    public static final StreamCodec<ByteBuf, CastRejectedPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<CastRejectedPayload> type() {
        return TYPE;
    }
}
