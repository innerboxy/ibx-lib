package ibx.lib.api;

import ibx.lib.network.BlockWavePayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * 地面起伏：从一点往外推一道扇形（左右各 180 度就是整圈）的浪，浪经过的地表方块往上弹一下再落回去，像地底下有东西顶过去。
 * 只是画面：真方块一块不动、碰撞不变（不破坏地形），也不生成实体和粒子；伤害由调用的一方自己算，节奏和这里对齐就行
 * （浪离起点 {@code startRadius + speed × 过了几 tick}，每一列弹到最高的那一刻正好是浪推到它的时候）。
 *
 * <p>服务端只发一个包（{@link BlockWavePayload}）给附近的玩家，客户端照同样的节奏自己算、自己画
 * （{@code client.ground.BlockWaveRendering}）</p>
 */
public final class BlockWave {
    private BlockWave() {}

    /** 发给多远内的玩家（格）。 */
    private static final double SEND_RANGE = 64.0;

    /**
     * 扇形浪：从 from（贴在地面上）往 dir（水平单位向量）推，左右各 halfAngle 度，从离起点 startRadius 格推到 range 格，
     * 每 tick 推 speed 格；离起点最近的方块弹 height 格高，越远越矮。弹的是地上本来的方块（上面的草、花、雪层一起）。
     */
    public static void fan(ServerLevel level, Vec3 from, Vec3 dir, float halfAngle, float startRadius, float range, float speed, float height) {
        fan(level, from, dir, halfAngle, startRadius, range, speed, height, null);
    }

    /** 同上；block 不为 null 时弹起来的画成这种方块（岩浆块之类，上面的草花不跟着动），而不是地上那块本来的样子。 */
    public static void fan(ServerLevel level, Vec3 from, Vec3 dir, float halfAngle, float startRadius, float range, float speed, float height,
                           @Nullable BlockState block) {
        BlockWavePayload wave = new BlockWavePayload(from, (float) Mth.atan2(dir.z, dir.x), Mth.clamp(halfAngle, 0.0F, 180.0F),
                startRadius, range, speed, height, block);
        PacketDistributor.sendToPlayersNear(level, null, from.x, from.y, from.z, SEND_RANGE, wave);
    }

    /** 整圈往外扩的浪（参数同 {@link #fan}）。 */
    public static void ring(ServerLevel level, Vec3 center, float startRadius, float range, float speed, float height) {
        ring(level, center, startRadius, range, speed, height, null);
    }

    /** 整圈往外扩的浪，弹起来的画成 block（null = 地上本来的方块）。 */
    public static void ring(ServerLevel level, Vec3 center, float startRadius, float range, float speed, float height, @Nullable BlockState block) {
        fan(level, center, new Vec3(1.0, 0.0, 0.0), 180.0F, startRadius, range, speed, height, block);
    }
}
