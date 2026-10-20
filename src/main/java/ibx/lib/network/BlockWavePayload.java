package ibx.lib.network;

import ibx.lib.IbxLib;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 一道地面起伏（服务端 → 附近的玩家，{@link ibx.lib.api.BlockWave} 发）：从 {@code from} 朝 {@code yaw} 往外推的扇形浪。
 * 客户端收到后按收到那一刻的世界时间开始，照同样的节奏自己算、自己画（{@code client.ground.BlockWaveRendering}）。
 *
 * @param from        浪的起点（贴在地面上）
 * @param yaw         朝哪边推（水平面上的角度，弧度，atan2(z, x)）
 * @param halfAngle   扇形左右各几度（180 = 整圈）
 * @param startRadius 浪从离起点多远开始（格）
 * @param range       推到多远为止（格）
 * @param speed       每 tick 往外推几格
 * @param height      离起点最近的方块弹多高（格），越远越矮
 * @param block       弹起来的画成这种方块（岩浆块之类）；null = 画地上那块本来的样子
 */
public record BlockWavePayload(Vec3 from, float yaw, float halfAngle, float startRadius, float range, float speed, float height,
                                @Nullable BlockState block) implements CustomPacketPayload {
    public static final Type<BlockWavePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(IbxLib.MODID, "block_wave"));

    public static final StreamCodec<FriendlyByteBuf, BlockWavePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeDouble(p.from.x);
                buf.writeDouble(p.from.y);
                buf.writeDouble(p.from.z);
                buf.writeFloat(p.yaw);
                buf.writeFloat(p.halfAngle);
                buf.writeFloat(p.startRadius);
                buf.writeFloat(p.range);
                buf.writeFloat(p.speed);
                buf.writeFloat(p.height);
                buf.writeVarInt(p.block == null ? 0 : Block.getId(p.block) + 1);
            },
            buf -> {
                Vec3 from = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
                float yaw = buf.readFloat(), halfAngle = buf.readFloat(), startRadius = buf.readFloat();
                float range = buf.readFloat(), speed = buf.readFloat(), height = buf.readFloat();
                int block = buf.readVarInt();
                return new BlockWavePayload(from, yaw, halfAngle, startRadius, range, speed, height, block == 0 ? null : Block.stateById(block - 1));
            });

    @Override
    public Type<BlockWavePayload> type() {
        return TYPE;
    }
}
