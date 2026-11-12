package ibx.lib.client.target;

import ibx.lib.IbxLib;
import ibx.lib.client.ground.GroundShapes;
import ibx.lib.config.ClientConfig;
import ibx.lib.network.TargetIndicatorPayload;
import ibx.lib.target.TargetIndicator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
 * 都是中间填色、边框稍深，贴着地形起伏（画法在 {@link GroundShapes}，Boss 的地面预警之类也用它）。颜色按阵营（{@link ClientConfig} 里三种颜色），
 * 看不看得见按「自己 / 玩家（友方、敌方）/ 生物（友方、敌方）」五个开关。
 *
 * <p>服务端发什么画什么（{@link TargetIndicatorPayload}），跟着实体走的形状每帧按插值后的位置算。
 * 不挂在实体渲染器上，整个世界一起画：抽取世界渲染状态时把网格算好，提交自定义几何时画出来。</p>
 */
public final class TargetIndicatorRendering {
    private TargetIndicatorRendering() {}

    private static final ContextKey<List<GroundShapes.Quad>> QUADS = new ContextKey<>(Identifier.fromNamespaceAndPath(IbxLib.MODID, "target_indicators"));

    /** 脚下的方形每边比碰撞箱多出多少格。 */
    private static final double FOOTPRINT_MARGIN = 0.3;

    /** 收到的提示：施法者的实体 id → 提示。 */
    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();

    private record Entry(TargetIndicator indicator, boolean holderIsPlayer, TargetIndicatorPayload.Relation relation) {
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
        List<GroundShapes.Quad> quads = new ArrayList<>();
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
            int rgb = GroundShapes.color(indicator.color());
            int fill = GroundShapes.fill(rgb);
            int edge = GroundShapes.edge(rgb);
            double ground = GroundShapes.groundY(level, center.x, center.z, center.y);
            Vec3 base = new Vec3(center.x, ground, center.z);
            switch (indicator.shape()) {
                case FOOTPRINT -> {
                    double half = anchor.getBbWidth() / 2.0 + FOOTPRINT_MARGIN;
                    GroundShapes.rect(quads, level, base, 1.0, 0.0, half, half, fill, edge, camera);
                }
                case CIRCLE -> GroundShapes.sector(quads, level, base, 0.0F, indicator.radius(), Mth.PI, fill, edge, camera);
                case FAN -> GroundShapes.sector(quads, level, base, Mth.rotLerp(partial, anchor.yRotO, anchor.getYRot()) * Mth.DEG_TO_RAD,
                        indicator.radius(), indicator.halfAngle() * Mth.DEG_TO_RAD, fill, edge, camera);
                case RECT -> {
                    // 中心在面前 ahead 格处，长边横在面朝方向的左右
                    float yaw = Mth.rotLerp(partial, anchor.yRotO, anchor.getYRot()) * Mth.DEG_TO_RAD;
                    double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
                    Vec3 mid = center.add(fx * indicator.ahead(), 0.0, fz * indicator.ahead());
                    Vec3 rectBase = new Vec3(mid.x, GroundShapes.groundY(level, mid.x, mid.z, center.y), mid.z);
                    GroundShapes.rect(quads, level, rectBase, Mth.cos(yaw), Mth.sin(yaw), indicator.radius() / 2.0, indicator.width() / 2.0,
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

    // ---------------------------------------------------------------- 提交：画

    private static void onSubmit(SubmitCustomGeometryEvent event) {
        List<GroundShapes.Quad> quads = event.getLevelRenderState().getRenderData(QUADS);
        if (quads != null) {
            GroundShapes.submit(event.getSubmitNodeCollector(), event.getPoseStack(), quads);
        }
    }
}
