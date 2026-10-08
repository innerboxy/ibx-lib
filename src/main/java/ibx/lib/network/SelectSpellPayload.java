package ibx.lib.network;

import ibx.lib.IbxLib;
import ibx.lib.spell.SpellCasting;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 轮盘选中了一个法术（客户端 → 服务端）。法术系统唯一的自定义包：施法开始 / 结束走原版的使用物品，不用包。
 * 服务端按该玩家的法术列表校验后才写入，见 {@link SpellCasting#select}。
 */
public record SelectSpellPayload(Identifier spell) implements CustomPacketPayload {
    public static final Type<SelectSpellPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(IbxLib.MODID, "select_spell"));

    public static final StreamCodec<ByteBuf, SelectSpellPayload> STREAM_CODEC =
            Identifier.STREAM_CODEC.map(SelectSpellPayload::new, SelectSpellPayload::spell);

    @Override
    public Type<SelectSpellPayload> type() {
        return TYPE;
    }

    /** 服务端主线程上处理。 */
    static void handle(SelectSpellPayload payload, IPayloadContext context) {
        SpellCasting.select(context.player(), payload.spell());
    }
}
