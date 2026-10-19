package ibx.lib.client.target;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ibx.lib.IbxLib;
import ibx.lib.config.ClientConfig;
import ibx.lib.network.TargetIndicatorPayload;
import ibx.lib.target.Faction;
import ibx.lib.target.TargetIndicator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 客户端：读条时地上的提示（{@link TargetIndicator}）。锁定的目标脚下画一块碰撞箱大小的方形，范围画圆 / 扇形 / 长条，
 * 都是中间填色、边框稍深，贴着地形起伏（每个顶点往上 / 往下找地面）。颜色按阵营（{@link ClientConfig} 里三种颜色），
 * 看不看得见按「自己 / 玩家（友方、敌方）/ 生物（友方、敌方）」五个开关。
 *
 * <p>服务端发什么画什么（{@link TargetIndicatorPayload}），跟着实体走的形状每帧按插值后的位置算。
 * 不挂在实体渲染器上，整个世界一起画：抽取世界渲染状态时把网格算好，提交自定义几何时画出来（同护盾术的光盾）。</p>
 */
public final class TargetIndicatorRendering {
    private TargetIndicatorRendering() {}

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(IbxLib.MODID, "textures/misc/target_indicator.png");
    private static final RenderType RENDER_TYPE = RenderTypes.eyes(TEXTURE);
    private static final ContextKey<List<Quad>> QUADS = new ContextKey<>(Identifier.fromNamespaceAndPath(IbxLib.MODID, "target_indicators"));

    /** 填充、边框的不透明度（0 ~ 255）。 */
    private static final int FILL_ALPHA = 0x50, EDGE_ALPHA = 0xC8;
    /** 边框比填充暗多少（RGB 乘它）。 */
    private static final float EDGE_DARKEN = 0.7F;
    /** 边框宽度（格）。 */
    private static final double EDGE = 0.12;
    /** 离地面多高（格），免得和地面打架。 */
    private static final double LIFT = 0.02;
    /** 网格大约多少格一段。 */
    private static final double STEP = 0.5;
    /** 找地面：起点在方块里时往上最多几格，悬空时往下最多几格。 */
    private static final int STEP_UP = 2, STEP_DOWN = 4;
    /** 脚下的方形每边比碰撞箱多出多少格。 */
    private static final double FOOTPRINT_MARGIN = 0.3;

    /** 收到的提示：施法者的实体 id → 提示。 */
    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();

    private record Entry(TargetIndicator indicator, boolean holderIsPlayer, TargetIndicatorPayload.Relation relation) {
    }

    /** 一个四边形，四个点相对镜头。 */
    private record Quad(float[] xyz, int color) {
    }

    /** MOD 总线的收包注册、GAME 总线的渲染和清理。 */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(TargetIndicatorRendering::onRegisterPayloadHandlers);
        NeoForge.EVENT_BUS.addListener(TargetIndicatorRendering::onExtract);
        NeoForge.EVENT_BUS.addListener(TargetIndicatorRendering::onSubmit);
        NeoForge.EVENT_BUS.addListener(TargetIndicatorRendering::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(TargetIndicatorRendering::onClone);
    }

    private static void onRegisterPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(TargetIndicatorPayload.TYPE, TargetIndicatorRendering::handle);
    }

    private static void handle(TargetIndicatorPayload payload, IPayloadContext context) {
        if (payload.indicator() == null) {
            ENTRIES.remove(payload.holderId());
        } else {
            ENTRIES.put(payload.holderId(), new Entry(payload.indicator(), payload.holderIsPlayer(), payload.relation()));
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ENTRIES.clear();
    }

    /**
     * 重生、换维度：本机玩家换了一个对象，客户端世界也可能换了。实体 id 在新世界里另有所指，
     * 换之前没收到「不画了」的提示会挂到不相干的实体脚下，所以全部丢掉，正在读条的会在 20 tick 内重发过来。
     */
    private static void onClone(ClientPlayerNetworkEvent.Clone event) {
        ENTRIES.clear();
    }

    // ---------------------------------------------------------------- 抽取：算网格

    private static void onExtract(ExtractLevelRenderStateEvent event) {
        ClientLevel level = event.getLevel();
        LocalPlayer player = Minecraft.getInstance().player;
        Vec3 camera = event.getCamera().position();
        List<Quad> quads = new ArrayList<>();
        Iterator<Map.Entry<Integer, Entry>> it = ENTRIES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Entry> e = it.next();
            Entity holder = level.getEntity(e.getKey());
            if (holder == null || holder.isRemoved()) {
                it.remove();
                continue;
            }
            Entry entry = e.getValue();
            if (!isShown(entry)) {
                continue;
            }
            TargetIndicator indicator = entry.indicator();
            Entity anchor = indicator.entityId() >= 0 ? level.getEntity(indicator.entityId()) : null;
            if (indicator.entityId() >= 0 && (anchor == null || player != null && anchor != player && anchor.isInvisibleTo(player))) {
                continue;
            }
            float partial = anchor == null ? 0.0F
                    : event.getDeltaTracker().getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(anchor));
            Vec3 center = anchor != null ? anchor.getPosition(partial) : indicator.point();
            double reach = switch (indicator.shape()) {
                case FOOTPRINT -> anchor.getBbWidth();
                case RECT -> Math.abs(indicator.ahead()) + indicator.radius() / 2.0 + indicator.width();
                default -> indicator.radius();
            };
            if (!event.getFrustum().isVisible(new AABB(center, center).inflate(reach + 1.0, 3.0, reach + 1.0))) {
                continue;
            }
            int rgb = color(indicator.color());
            int fill = FILL_ALPHA << 24 | rgb;
            int edge = EDGE_ALPHA << 24 | darken(rgb);
            double ground = groundY(level, center.x, center.z, center.y);
            Vec3 base = new Vec3(center.x, ground, center.z);
            switch (indicator.shape()) {
                case FOOTPRINT -> {
                    double half = anchor.getBbWidth() / 2.0 + FOOTPRINT_MARGIN;
                    rect(quads, level, base, 1.0, 0.0, half, half, fill, edge, camera);
                }
                case CIRCLE -> sector(quads, level, base, 0.0F, indicator.radius(), Mth.PI, fill, edge, camera);
                case FAN -> sector(quads, level, base, Mth.rotLerp(partial, anchor.yRotO, anchor.getYRot()) * Mth.DEG_TO_RAD,
                        indicator.radius(), indicator.halfAngle() * Mth.DEG_TO_RAD, fill, edge, camera);
                case RECT -> {
                    // 中心在面前 ahead 格处，长边横在面朝方向的左右
                    float yaw = Mth.rotLerp(partial, anchor.yRotO, anchor.getYRot()) * Mth.DEG_TO_RAD;
                    double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
                    Vec3 mid = center.add(fx * indicator.ahead(), 0.0, fz * indicator.ahead());
                    Vec3 rectBase = new Vec3(mid.x, groundY(level, mid.x, mid.z, center.y), mid.z);
                    rect(quads, level, rectBase, Mth.cos(yaw), Mth.sin(yaw), indicator.radius() / 2.0, indicator.width() / 2.0,
                            fill, edge, camera);
                }
            }
        }
        event.getRenderState().setRenderData(QUADS, quads);
    }

    /** 按施法者对自己来说是谁、是玩家还是生物，看对应的开关。 */
    private static boolean isShown(Entry entry) {
        return switch (entry.relation()) {
            case SELF -> ClientConfig.get(ClientConfig.SHOW_TARGET_SELF);
            case ALLY -> ClientConfig.get(entry.holderIsPlayer() ? ClientConfig.SHOW_TARGET_PLAYER_ALLY : ClientConfig.SHOW_TARGET_MOB_ALLY);
            case ENEMY -> ClientConfig.get(entry.holderIsPlayer() ? ClientConfig.SHOW_TARGET_PLAYER_ENEMY : ClientConfig.SHOW_TARGET_MOB_ENEMY);
        };
    }

    private static int color(Faction faction) {
        return switch (faction) {
            case ALLY -> ClientConfig.color(ClientConfig.TARGET_COLOR_ALLY);
            case ENEMY -> ClientConfig.color(ClientConfig.TARGET_COLOR_ENEMY);
            case ALL, NONE -> ClientConfig.color(ClientConfig.TARGET_COLOR_ALL);
        };
    }

    private static int darken(int rgb) {
        int r = (int) ((rgb >> 16 & 0xFF) * EDGE_DARKEN);
        int g = (int) ((rgb >> 8 & 0xFF) * EDGE_DARKEN);
        int b = (int) ((rgb & 0xFF) * EDGE_DARKEN);
        return r << 16 | g << 8 | b;
    }

    /**
     * 扇形（{@code halfAngle} = π 时是整圆）：圆心 {@code base}，{@code yaw} 是面朝方向（原版的 yRot，弧度），
     * 里面按环、按段切成小格填色，最外一圈宽 {@value #EDGE} 格是边框；不是整圆时两条半径边也描边。
     */
    private static void sector(List<Quad> out, BlockGetter level, Vec3 base, float yaw, double radius, double halfAngle,
                               int fill, int edge, Vec3 camera) {
        if (radius <= EDGE) {
            return;
        }
        int segments = Mth.clamp(Mth.ceil(radius * halfAngle * 2.0 / STEP), 8, 96);
        int rings = Mth.clamp(Mth.ceil((radius - EDGE) / STEP), 1, 32);
        double[] radii = new double[rings + 2];
        for (int i = 0; i <= rings; i++) {
            radii[i] = (radius - EDGE) * i / rings;
        }
        radii[rings + 1] = radius;
        Vec3[][] grid = new Vec3[rings + 2][segments + 1];
        for (int i = 0; i < radii.length; i++) {
            for (int j = 0; j <= segments; j++) {
                double angle = -halfAngle + 2.0 * halfAngle * j / segments;
                grid[i][j] = ground(level, polar(base, yaw, angle, radii[i]), base.y);
            }
        }
        for (int i = 0; i < radii.length - 1; i++) {
            int color = i == rings ? edge : fill;
            for (int j = 0; j < segments; j++) {
                quad(out, camera, grid[i][j], grid[i][j + 1], grid[i + 1][j + 1], grid[i + 1][j], color);
            }
        }
        if (halfAngle < Mth.PI - 1.0E-3) {
            radialEdge(out, level, base, yaw, -halfAngle, radius, 1.0, edge, camera);
            radialEdge(out, level, base, yaw, halfAngle, radius, -1.0, edge, camera);
        }
    }

    /** 扇形的一条半径边：沿着半径往扇形里面描 {@value #EDGE} 格宽。{@code inward} 是往里的那一侧（+1 / -1）。 */
    private static void radialEdge(List<Quad> out, BlockGetter level, Vec3 base, float yaw, double angle, double radius, double inward,
                                   int edge, Vec3 camera) {
        Vec3 along = polar(Vec3.ZERO, yaw, angle, 1.0);
        Vec3 side = polar(Vec3.ZERO, yaw, angle + inward * Mth.HALF_PI, EDGE);
        int steps = Mth.clamp(Mth.ceil(radius / STEP), 1, 32);
        for (int k = 0; k < steps; k++) {
            Vec3 a = base.add(along.scale(radius * k / steps));
            Vec3 b = base.add(along.scale(radius * (k + 1) / steps));
            quad(out, camera, ground(level, a, base.y), ground(level, b, base.y),
                    ground(level, b.add(side), base.y), ground(level, a.add(side), base.y), edge);
        }
    }

    /**
     * 长方形：中心 {@code base}，{@code (ux, uz)} 是长边方向（单位向量），沿它半长 {@code halfU}、沿垂直方向半长 {@code halfV}；
     * 里面填色，四边描 {@value #EDGE} 格宽的边框。
     */
    private static void rect(List<Quad> out, BlockGetter level, Vec3 base, double ux, double uz, double halfU, double halfV,
                             int fill, int edge, Vec3 camera) {
        double innerU = Math.max(0.0, halfU - EDGE);
        double innerV = Math.max(0.0, halfV - EDGE);
        int nU = Mth.clamp(Mth.ceil(innerU * 2.0 / STEP), 1, 64);
        int nV = Mth.clamp(Mth.ceil(innerV * 2.0 / STEP), 1, 64);
        for (int i = 0; i < nU; i++) {
            for (int j = 0; j < nV; j++) {
                double s0 = -innerU + 2.0 * innerU * i / nU, s1 = -innerU + 2.0 * innerU * (i + 1) / nU;
                double t0 = -innerV + 2.0 * innerV * j / nV, t1 = -innerV + 2.0 * innerV * (j + 1) / nV;
                quad(out, camera, ground(level, at(base, ux, uz, s0, t0), base.y), ground(level, at(base, ux, uz, s0, t1), base.y),
                        ground(level, at(base, ux, uz, s1, t1), base.y), ground(level, at(base, ux, uz, s1, t0), base.y), fill);
            }
        }
        // 四条边：各是内外两条线之间的一圈
        double[][] corners = {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
        for (int c = 0; c < 4; c++) {
            double[] p = corners[c];
            double[] q = corners[(c + 1) % 4];
            int n = p[0] == q[0] ? nV : nU;
            for (int k = 0; k < n; k++) {
                double k0 = (double) k / n, k1 = (double) (k + 1) / n;
                Vec3 outer0 = at(base, ux, uz, Mth.lerp(k0, p[0], q[0]) * halfU, Mth.lerp(k0, p[1], q[1]) * halfV);
                Vec3 outer1 = at(base, ux, uz, Mth.lerp(k1, p[0], q[0]) * halfU, Mth.lerp(k1, p[1], q[1]) * halfV);
                Vec3 inner0 = at(base, ux, uz, Mth.lerp(k0, p[0], q[0]) * innerU, Mth.lerp(k0, p[1], q[1]) * innerV);
                Vec3 inner1 = at(base, ux, uz, Mth.lerp(k1, p[0], q[0]) * innerU, Mth.lerp(k1, p[1], q[1]) * innerV);
                quad(out, camera, ground(level, outer0, base.y), ground(level, outer1, base.y),
                        ground(level, inner1, base.y), ground(level, inner0, base.y), edge);
            }
        }
    }

    /** 长方形里的点：沿长边 {@code s} 格、沿垂直方向 {@code t} 格（只在水平面上）。 */
    private static Vec3 at(Vec3 base, double ux, double uz, double s, double t) {
        return base.add(ux * s - uz * t, 0.0, uz * s + ux * t);
    }

    /** 从 {@code base} 朝面朝方向偏 {@code angle} 弧度、走 {@code distance} 格的点（只在水平面上）。 */
    private static Vec3 polar(Vec3 base, float yaw, double angle, double distance) {
        double a = yaw + angle;
        return base.add(-Math.sin(a) * distance, 0.0, Math.cos(a) * distance);
    }

    /** 把水平面上的点放到它那一列的地面上（略抬高）。 */
    private static Vec3 ground(BlockGetter level, Vec3 point, double nearY) {
        return new Vec3(point.x, groundY(level, point.x, point.z, nearY) + LIFT, point.z);
    }

    /**
     * {@code (x, z)} 那一列在 {@code nearY} 附近的地面高度：起点在方块里（上坡）就往上找第一个空格，最多 {@value #STEP_UP} 格；
     * 悬空（下坡、坑）就往下找第一个有碰撞的方块的顶面，最多 {@value #STEP_DOWN} 格。都找不到就按 {@code nearY}。
     */
    private static double groundY(BlockGetter level, double x, double z, double nearY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(nearY + 0.25), Mth.floor(z));
        double top = topAt(level, pos);
        if (!Double.isNaN(top) && top > nearY + 0.25) {
            for (int i = 0; i < STEP_UP; i++) {
                pos.move(Direction.UP);
                double next = topAt(level, pos);
                if (Double.isNaN(next)) {
                    return top;
                }
                top = next;
            }
            return nearY;
        }
        if (!Double.isNaN(top)) {
            return top;
        }
        for (int i = 0; i < STEP_DOWN; i++) {
            pos.move(Direction.DOWN);
            double below = topAt(level, pos);
            if (!Double.isNaN(below)) {
                return below;
            }
        }
        return nearY;
    }

    /** 这一格碰撞箱的顶面高度；没有碰撞为 NaN。 */
    private static double topAt(BlockGetter level, BlockPos pos) {
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
        return shape.isEmpty() ? Double.NaN : pos.getY() + shape.max(Direction.Axis.Y);
    }

    private static void quad(List<Quad> out, Vec3 camera, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        float[] xyz = new float[12];
        Vec3[] points = {a, b, c, d};
        for (int i = 0; i < 4; i++) {
            xyz[i * 3] = (float) (points[i].x - camera.x);
            xyz[i * 3 + 1] = (float) (points[i].y - camera.y);
            xyz[i * 3 + 2] = (float) (points[i].z - camera.z);
        }
        out.add(new Quad(xyz, color));
    }

    // ---------------------------------------------------------------- 提交：画

    private static void onSubmit(SubmitCustomGeometryEvent event) {
        List<Quad> quads = event.getLevelRenderState().getRenderData(QUADS);
        if (quads == null || quads.isEmpty()) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            for (Quad quad : quads) {
                // eyes 渲染类型剔除背面：正反各画一遍，从上从下都看得见
                for (int i = 0; i < 4; i++) {
                    vertex(buffer, pose, quad, i, 1.0F);
                }
                for (int i = 3; i >= 0; i--) {
                    vertex(buffer, pose, quad, i, -1.0F);
                }
            }
        });
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Quad quad, int i, float up) {
        float[] xyz = quad.xyz();
        buffer.addVertex(pose, xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2])
                .setColor(quad.color())
                .setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0.0F, up, 0.0F);
    }
}
