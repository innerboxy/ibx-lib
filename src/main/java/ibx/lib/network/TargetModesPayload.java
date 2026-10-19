package ibx.lib.network;

import ibx.lib.IbxLib;
import ibx.lib.registry.AttachmentRegistry;
import ibx.lib.target.TargetMode;
import ibx.lib.target.TargetModes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 玩家的两个选取时机设置（客户端 → 服务端），进服时和在 选项 → 控制 里改了之后发。服务端存进 {@link TargetModes}。
 *
 * @param unitAtStart  指向型是否开始时锁定
 * @param pointAtStart 地点型是否开始时锁定
 */
public record TargetModesPayload(boolean unitAtStart, boolean pointAtStart) implements CustomPacketPayload {
    public static final Type<TargetModesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(IbxLib.MODID, "target_modes"));

    public static final StreamCodec<ByteBuf, TargetModesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TargetModesPayload::unitAtStart,
            ByteBufCodecs.BOOL, TargetModesPayload::pointAtStart,
            TargetModesPayload::new);

    @Override
    public Type<TargetModesPayload> type() {
        return TYPE;
    }

    /** 服务端主线程上处理。 */
    static void handle(TargetModesPayload payload, IPayloadContext context) {
        context.player().setData(AttachmentRegistry.TARGET_MODES, new TargetModes(
                payload.unitAtStart ? TargetMode.START : TargetMode.EFFECT,
                payload.pointAtStart ? TargetMode.START : TargetMode.EFFECT));
    }
}
