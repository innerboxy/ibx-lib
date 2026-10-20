package ibx.lib.client.ground;

import com.mojang.blaze3d.vertex.PoseStack;
import ibx.lib.IbxLib;
import ibx.lib.network.BlockWavePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 客户端：地面起伏（{@link ibx.lib.api.BlockWave}）。收到一道浪就把扇形里每一列的地表方块（连同上面的草、花、雪层；指定了方块就画成那种方块）记下来，
 * 浪推到那一列时它正好弹到最高、前后按重力起落；画的是那一列方块的一份副本（同原版下落方块的画法），真方块不动。
 * 浪的节奏和服务端一样：离起点 {@code startRadius + speed × 过了几 tick}，从收到那一刻起算。
 *
 * <p>不挂在实体上，整个世界一起画：抽取世界渲染状态时算好这一帧哪些方块抬了多高，提交时画出来（同目标选择的提示）。</p>
 */
public final class BlockWaveRendering {
    private BlockWaveRendering() {}

    private static final ContextKey<List<Pop>> POPS = new ContextKey<>(Identifier.fromNamespaceAndPath(IbxLib.MODID, "block_wave"));
    /** 弹起来以后往下落的重力（格 / tick²）：弹 0.5 格约 3 tick 落回，浪是一道三四格宽的坎，不是一大片一起抬着。 */
    private static final float GRAVITY = 0.44F;
    /** 最远那圈比最近那圈矮多少（比例）。 */
    private static final float FAR_FALLOFF = 0.4F;
    /** 每块的高度在 1 ± 这么多之间随机，看着不像一排台阶。 */
    private static final float JITTER = 0.2F;

    private static final List<Wave> WAVES = new ArrayList<>();

    /** 一列：地表方块、上面跟着一起抬的装饰（草、花、雪层，没有为 null）、浪推到它的时刻（tick）、弹起的初速度。 */
    private record Column(BlockPos pos, BlockState ground, @Nullable BlockState top, float start, float speed) {
    }

    private record Wave(long born, List<Column> columns, float end) {
    }

    /** 这一帧画的一块：相对镜头的位置（方块的最小角）和它的渲染状态。 */
    private record Pop(float x, float y, float z, MovingBlockRenderState state) {
    }

    /** MOD 总线的收包注册、GAME 总线的渲染和清理。 */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((RegisterClientPayloadHandlersEvent e) -> e.register(BlockWavePayload.TYPE, BlockWaveRendering::handle));
        NeoForge.EVENT_BUS.addListener(BlockWaveRendering::onExtract);
        NeoForge.EVENT_BUS.addListener(BlockWaveRendering::onSubmit);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> WAVES.clear());
    }

    // ---------------------------------------------------------------- 收到一道浪：记下扇形里的每一列

    private static void handle(BlockWavePayload payload, IPayloadContext context) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (WAVES.size() > 64) WAVES.clear();
        WAVES.add(build(level, payload, level.getGameTime()));
    }

    private static Wave build(ClientLevel level, BlockWavePayload p, long born) {
        List<Column> columns = new ArrayList<>();
        Vec3 from = p.from();
        double dx = Mth.cos(p.yaw()), dz = Mth.sin(p.yaw());
        double half = Math.toRadians(p.halfAngle());
        int r = Mth.ceil(p.range()) + 1;
        float end = 0.0F;
        for (int bx = Mth.floor(from.x) - r; bx <= Mth.floor(from.x) + r; bx++) {
            for (int bz = Mth.floor(from.z) - r; bz <= Mth.floor(from.z) + r; bz++) {
                double ox = bx + 0.5 - from.x, oz = bz + 0.5 - from.z;
                double d = Math.sqrt(ox * ox + oz * oz);
                if (d < p.startRadius() - 0.5 || d > p.range() + 0.3) continue;
                if (half < Math.PI - 1.0E-3 && d > 1.0E-3) {
                    // 方块中心在扇形边上半格以内也算（不然扇形两边是一排锯齿）
                    double slack = Math.asin(Math.min(1.0, 0.7 / d));
                    if ((ox * dx + oz * dz) / d < Math.cos(Math.min(Math.PI, half + slack))) continue;
                }
                BlockPos pos = GroundShapes.surfaceBlock(level, bx, bz, from.y);
                if (pos == null) continue;
                BlockState ground, top;
                if (p.block() != null) {                          // 指定了方块：地上弹起来的都画成它，上面的草花不动
                    ground = p.block();
                    top = null;
                } else {
                    ground = level.getBlockState(pos);
                    if (!drawable(ground)) continue;
                    BlockState above = level.getBlockState(pos.above());
                    top = !above.isAir() && drawable(above) && above.getCollisionShape(level, pos.above()).isEmpty() ? above : null;
                }
                float far = (float) Mth.clamp((d - p.startRadius()) / Math.max(0.1, p.range() - p.startRadius()), 0.0, 1.0);
                float peak = p.height() * (1.0F - FAR_FALLOFF * far) * (1.0F + JITTER * (hash(bx, bz) * 2.0F - 1.0F));
                float speed = (float) Math.sqrt(2.0F * GRAVITY * Math.max(0.01F, peak));
                // 浪推到这一列的那一刻正好弹到最高（提前半段起跳）
                float start = (float) Math.max(0.0, (d - p.startRadius()) / Math.max(0.05F, p.speed()) - speed / GRAVITY);
                columns.add(new Column(pos, ground, top, start, speed));
                end = Math.max(end, start + 2.0F * speed / GRAVITY);
            }
        }
        return new Wave(born, columns, end);
    }

    /** 能按方块模型画、不是流体、没有方块实体（箱子之类画不出来）的。 */
    private static boolean drawable(BlockState state) {
        return state.getRenderShape() == RenderShape.MODEL && state.getFluidState().isEmpty() && !state.hasBlockEntity();
    }

    /** 按位置的 0 ~ 1 伪随机（同一块每次一样）。 */
    private static float hash(int x, int z) {
        int h = x * 73856093 ^ z * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535.0F;
    }

    // ---------------------------------------------------------------- 抽取：这一帧哪些方块抬了多高

    private static void onExtract(ExtractLevelRenderStateEvent event) {
        ClientLevel level = event.getLevel();
        if (WAVES.isEmpty()) {
            event.getRenderState().setRenderData(POPS, List.of());
            return;
        }
        float now = level.getGameTime() + event.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 camera = event.getCamera().position();
        List<Pop> pops = new ArrayList<>();
        Iterator<Wave> it = WAVES.iterator();
        while (it.hasNext()) {
            Wave wave = it.next();
            float t = now - wave.born();
            if (t > wave.end() + 1.0F) {
                it.remove();
                continue;
            }
            for (Column c : wave.columns()) {
                float tau = t - c.start();
                if (tau <= 0.0F || tau >= 2.0F * c.speed() / GRAVITY) continue;
                float h = c.speed() * tau - 0.5F * GRAVITY * tau * tau;
                if (h < 0.005F) continue;
                pops.add(pop(level, c.pos(), c.pos(), c.ground(), h, camera));
                if (c.top() != null) pops.add(pop(level, c.pos(), c.pos().above(), c.top(), h, camera));
            }
        }
        event.getRenderState().setRenderData(POPS, pops);
    }

    /** 一块抬高 h 的副本：贴图的随机朝向按真方块的位置（和地上那块长得一样），光照取地表上面那格（露天的亮度）。 */
    private static Pop pop(ClientLevel level, BlockPos surface, BlockPos pos, BlockState state, float h, Vec3 camera) {
        MovingBlockRenderState s = new MovingBlockRenderState();
        s.randomSeedPos = pos;
        s.blockPos = surface.above();
        s.blockState = state;
        s.biome = level.getBiome(pos);
        s.cardinalLighting = level.cardinalLighting();
        s.lightEngine = level.getLightEngine();
        return new Pop((float) (pos.getX() - camera.x), (float) (pos.getY() + h - camera.y), (float) (pos.getZ() - camera.z), s);
    }

    // ---------------------------------------------------------------- 提交：画

    private static void onSubmit(SubmitCustomGeometryEvent event) {
        List<Pop> pops = event.getLevelRenderState().getRenderData(POPS);
        if (pops == null || pops.isEmpty()) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        for (Pop pop : pops) {
            poseStack.pushPose();
            poseStack.translate(pop.x(), pop.y(), pop.z());
            event.getSubmitNodeCollector().submitMovingBlock(poseStack, pop.state());
            poseStack.popPose();
        }
    }
}
