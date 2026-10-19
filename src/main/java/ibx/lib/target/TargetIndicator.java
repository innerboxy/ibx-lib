package ibx.lib.target;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 读条时地上画的提示：锁定的目标脚下一块碰撞箱大小的方形，或者范围的圆 / 扇形 / 长条。服务端从「当前锁定」算出来发给看得见的玩家，
 * 客户端照着画（{@code client.target.TargetIndicatorRendering}）。
 *
 * @param shape     形状
 * @param entityId  方形画在它脚下；圆 / 扇形 / 长条跟着它走（自身范围）；-1 = 圆固定在 {@code point}
 * @param point     固定的圆心（地点型）；跟着实体走时不用
 * @param radius    圆半径、扇形长度、长条长度（格）
 * @param halfAngle 扇形左右各几度
 * @param ahead     长条中心在面前几格
 * @param width     长条的宽（沿面朝方向）
 * @param color     按哪个阵营上色
 */
public record TargetIndicator(Shape shape, int entityId, Vec3 point, float radius, float halfAngle, float ahead, float width,
                              Faction color) {
    public enum Shape {
        /** 目标脚下按碰撞箱底面的方形。 */
        FOOTPRINT,
        CIRCLE,
        FAN,
        /** 跟着实体朝向转的长条：面前 {@code ahead} 格处、横在左右。 */
        RECT
    }

    /** 固定圆心按 1/16 格取整：每 tick 准星微动不至于每 tick 重发。 */
    public TargetIndicator {
        point = new Vec3(Math.round(point.x * 16.0) / 16.0, Math.round(point.y * 16.0) / 16.0, Math.round(point.z * 16.0) / 16.0);
    }

    public static TargetIndicator footprint(int entityId, Faction color) {
        return new TargetIndicator(Shape.FOOTPRINT, entityId, Vec3.ZERO, 0.0F, 0.0F, 0.0F, 0.0F, color);
    }

    public static TargetIndicator circleAt(Vec3 point, float radius, Faction color) {
        return new TargetIndicator(Shape.CIRCLE, -1, point, radius, 0.0F, 0.0F, 0.0F, color);
    }

    public static TargetIndicator circleOn(int entityId, float radius, Faction color) {
        return new TargetIndicator(Shape.CIRCLE, entityId, Vec3.ZERO, radius, 0.0F, 0.0F, 0.0F, color);
    }

    public static TargetIndicator fanOn(int entityId, float range, float halfAngle, Faction color) {
        return new TargetIndicator(Shape.FAN, entityId, Vec3.ZERO, range, Mth.clamp(halfAngle, 0.0F, 180.0F), 0.0F, 0.0F, color);
    }

    public static TargetIndicator rectOn(int entityId, float ahead, float length, float width, Faction color) {
        return new TargetIndicator(Shape.RECT, entityId, Vec3.ZERO, length, 0.0F, ahead, width, color);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(this.shape);
        buf.writeVarInt(this.entityId + 1);
        Vec3.STREAM_CODEC.encode(buf, this.point);
        buf.writeFloat(this.radius);
        buf.writeFloat(this.halfAngle);
        buf.writeFloat(this.ahead);
        buf.writeFloat(this.width);
        buf.writeEnum(this.color);
    }

    public static TargetIndicator read(FriendlyByteBuf buf) {
        return new TargetIndicator(buf.readEnum(Shape.class), buf.readVarInt() - 1, Vec3.STREAM_CODEC.decode(buf), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readEnum(Faction.class));
    }
}
