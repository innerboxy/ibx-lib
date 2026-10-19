package ibx.lib.network;

import ibx.lib.IbxLib;
import ibx.lib.target.TargetIndicator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * 某个实体读条时地上的提示变了（服务端 → 看得见它的玩家），由 {@link ibx.lib.target.TargetHandler} 发。
 * 每个玩家收到的 {@code relation} 不一样：施法者对这个玩家来说是自己、友方还是敌方，服务端按每个玩家算好（客户端不知道 {@code pvp} 规则）。
 *
 * @param holderId       施法者 / 使用者
 * @param holderIsPlayer 是不是玩家（客户端按「玩家 / 生物」分开开关）
 * @param relation       施法者对收包的玩家来说是谁
 * @param indicator      现在画什么；null = 不画了
 */
public record TargetIndicatorPayload(int holderId, boolean holderIsPlayer, Relation relation, @Nullable TargetIndicator indicator)
        implements CustomPacketPayload {
    public enum Relation {
        SELF,
        ALLY,
        ENEMY
    }

    public static final Type<TargetIndicatorPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(IbxLib.MODID, "target_indicator"));

    public static final StreamCodec<FriendlyByteBuf, TargetIndicatorPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.holderId);
                buf.writeBoolean(payload.holderIsPlayer);
                buf.writeEnum(payload.relation);
                buf.writeBoolean(payload.indicator != null);
                if (payload.indicator != null) {
                    payload.indicator.write(buf);
                }
            },
            buf -> new TargetIndicatorPayload(buf.readVarInt(), buf.readBoolean(), buf.readEnum(Relation.class),
                    buf.readBoolean() ? TargetIndicator.read(buf) : null));

    @Override
    public Type<TargetIndicatorPayload> type() {
        return TYPE;
    }
}
