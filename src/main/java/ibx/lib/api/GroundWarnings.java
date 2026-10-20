package ibx.lib.api;

import ibx.lib.network.GroundWarningPayload;
import ibx.lib.target.Faction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 地面预警：地上标一块范围（圆 {@link #circle} / 长条 {@link #line}）一段时间，提醒这里马上要挨打。只管画，到点要触发什么由调用的一方自己算。
 * 画法和目标选择读条时地上的提示一样（{@code client.ground.GroundShapes}：中间填色、边框稍深、贴着地形起伏、自发光），
 * 颜色取客户端配置里那个阵营的颜色（默认敌方红）；不跟「显示敌方生物的提示」开关走，关了也照样显示。
 *
 * <p>服务端发一个包（{@link GroundWarningPayload}）给附近的玩家，客户端按收到那一刻的世界时间到点自己删；发出去以后才走进范围的玩家
 * 看不到这一块（预警一般就亮一两秒）。返回的编号可以拿去 {@link #cancel}（比如 Boss 死了提前收掉）。</p>
 */
public final class GroundWarnings {
    private GroundWarnings() {}

    /** 发给多远内的玩家（格）。 */
    private static final double SEND_RANGE = 64.0;
    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    /** 以 center（贴在地面上）为圆心、半径 radius 的圆，标 duration tick，敌方红。返回编号。 */
    public static int circle(ServerLevel level, Vec3 center, float radius, int duration) {
        return circle(level, center, radius, duration, Faction.ENEMY);
    }

    public static int circle(ServerLevel level, Vec3 center, float radius, int duration, Faction color) {
        int id = NEXT_ID.incrementAndGet();
        send(level, center, new GroundWarningPayload(id, GroundWarningPayload.Shape.CIRCLE, center, radius, 0.0F, 0.0F, color, Math.max(1, duration)));
        return id;
    }

    /** 从 from（贴在地面上）沿 dir（水平单位向量）长 length、宽 width 的长条，标 duration tick，敌方红。返回编号。 */
    public static int line(ServerLevel level, Vec3 from, Vec3 dir, float length, float width, int duration) {
        return line(level, from, dir, length, width, duration, Faction.ENEMY);
    }

    public static int line(ServerLevel level, Vec3 from, Vec3 dir, float length, float width, int duration, Faction color) {
        int id = NEXT_ID.incrementAndGet();
        Vec3 center = from.add(dir.scale(length * 0.5));
        send(level, center, new GroundWarningPayload(id, GroundWarningPayload.Shape.LINE, center, length, width,
                (float) Mth.atan2(dir.z, dir.x), color, Math.max(1, duration)));
        return id;
    }

    /** 提前撤掉 id 那一块（已经到点没了的也没关系）。 */
    public static void cancel(ServerLevel level, int id) {
        PacketDistributor.sendToPlayersInDimension(level, GroundWarningPayload.cancel(id));
    }

    private static void send(ServerLevel level, Vec3 at, GroundWarningPayload payload) {
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, SEND_RANGE, payload);
    }
}
