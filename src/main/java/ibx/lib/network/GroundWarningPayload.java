package ibx.lib.network;

import ibx.lib.IbxLib;
import ibx.lib.target.Faction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * 地面预警（服务端 → 附近的玩家，{@link ibx.lib.api.GroundWarnings} 发）：地上标一块范围 {@code duration} tick，
 * 客户端按收到那一刻的世界时间到点自己删（{@code client.ground.GroundWarningRendering}）。{@code duration} ≤ 0 是「把这个 id 撤掉」。
 *
 * @param id       这一块预警的编号（撤掉时用）
 * @param shape    圆 / 长条
 * @param center   范围正中（贴在地面上）
 * @param size     圆：半径；长条：长度（格）
 * @param width    长条的宽（格）
 * @param yaw      长条的方向（水平面上的角度，弧度，atan2(z, x)）
 * @param color    按哪个阵营上色
 * @param duration 标多久（tick）
 */
public record GroundWarningPayload(int id, Shape shape, Vec3 center, float size, float width, float yaw, Faction color, int duration)
        implements CustomPacketPayload {
    public enum Shape {
        CIRCLE,
        LINE
    }

    public static final Type<GroundWarningPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(IbxLib.MODID, "ground_warning"));

    public static final StreamCodec<FriendlyByteBuf, GroundWarningPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.id);
                buf.writeEnum(p.shape);
                Vec3.STREAM_CODEC.encode(buf, p.center);
                buf.writeFloat(p.size);
                buf.writeFloat(p.width);
                buf.writeFloat(p.yaw);
                buf.writeEnum(p.color);
                buf.writeVarInt(p.duration);
            },
            buf -> new GroundWarningPayload(buf.readVarInt(), buf.readEnum(Shape.class), Vec3.STREAM_CODEC.decode(buf), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readEnum(Faction.class), buf.readVarInt()));

    /** 把 id 这一块撤掉。 */
    public static GroundWarningPayload cancel(int id) {
        return new GroundWarningPayload(id, Shape.CIRCLE, Vec3.ZERO, 0.0F, 0.0F, 0.0F, Faction.ENEMY, 0);
    }

    @Override
    public Type<GroundWarningPayload> type() {
        return TYPE;
    }
}
