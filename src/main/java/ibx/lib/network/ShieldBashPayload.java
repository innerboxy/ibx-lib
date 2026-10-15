package ibx.lib.network;

import ibx.lib.IbxLib;
import ibx.lib.api.ShieldApi;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 主手举盾中按下了攻击键（客户端 → 服务端），没有数据。服务端校验还在举盾、不在冷却后出盾击，见 {@link ShieldApi#tryBash}。
 * 举盾本身走原版的使用物品，不用包。
 */
public record ShieldBashPayload() implements CustomPacketPayload {
    public static final ShieldBashPayload INSTANCE = new ShieldBashPayload();

    public static final Type<ShieldBashPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(IbxLib.MODID, "shield_bash"));

    public static final StreamCodec<ByteBuf, ShieldBashPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<ShieldBashPayload> type() {
        return TYPE;
    }

    /** 服务端主线程上处理。 */
    static void handle(ShieldBashPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ShieldApi.tryBash(player);
        }
    }
}
