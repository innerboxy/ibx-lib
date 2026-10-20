package ibx.lib.client.ground;

import ibx.lib.IbxLib;
import ibx.lib.network.GroundWarningPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
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
 * 客户端：地面预警（{@link ibx.lib.api.GroundWarnings}）。收到就记下来（连同收到时的世界时间），到点自己删，收到撤掉的包提前删；
 * 用 {@link GroundShapes} 画，和目标选择读条时的提示同一套样子。不跟「显示敌方生物的提示」开关走。
 *
 * <p>整个世界一起画：抽取世界渲染状态时把网格算好，提交自定义几何时画出来（同目标选择的提示）。</p>
 */
public final class GroundWarningRendering {
    private GroundWarningRendering() {}

    private static final ContextKey<List<GroundShapes.Quad>> QUADS = new ContextKey<>(Identifier.fromNamespaceAndPath(IbxLib.MODID, "ground_warnings"));

    /** 收到的预警：编号 → 预警和收到时的世界时间。 */
    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();

    private record Entry(GroundWarningPayload warning, long born) {
    }

    /** MOD 总线的收包注册、GAME 总线的渲染和清理。 */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((RegisterClientPayloadHandlersEvent e) -> e.register(GroundWarningPayload.TYPE, GroundWarningRendering::handle));
        NeoForge.EVENT_BUS.addListener(GroundWarningRendering::onExtract);
        NeoForge.EVENT_BUS.addListener(GroundWarningRendering::onSubmit);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ENTRIES.clear());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.Clone e) -> ENTRIES.clear());
    }

    private static void handle(GroundWarningPayload payload, IPayloadContext context) {
        ClientLevel level = Minecraft.getInstance().level;
        if (payload.duration() <= 0 || level == null) {
            ENTRIES.remove(payload.id());
            return;
        }
        if (ENTRIES.size() > 256) ENTRIES.clear();
        ENTRIES.put(payload.id(), new Entry(payload, level.getGameTime()));
    }

    private static void onExtract(ExtractLevelRenderStateEvent event) {
        if (ENTRIES.isEmpty()) {
            event.getRenderState().setRenderData(QUADS, List.of());
            return;
        }
        ClientLevel level = event.getLevel();
        long now = level.getGameTime();
        Vec3 camera = event.getCamera().position();
        List<GroundShapes.Quad> quads = new ArrayList<>();
        Iterator<Map.Entry<Integer, Entry>> it = ENTRIES.entrySet().iterator();
        while (it.hasNext()) {
            Entry entry = it.next().getValue();
            GroundWarningPayload w = entry.warning();
            if (now - entry.born() >= w.duration()) {
                it.remove();
                continue;
            }
            Vec3 c = w.center();
            double reach = w.shape() == GroundWarningPayload.Shape.CIRCLE ? w.size() : w.size() * 0.5 + w.width();
            if (!event.getFrustum().isVisible(new AABB(c, c).inflate(reach + 1.0, 3.0, reach + 1.0))) {
                continue;
            }
            int rgb = GroundShapes.color(w.color());
            int fill = GroundShapes.fill(rgb), edge = GroundShapes.edge(rgb);
            Vec3 base = new Vec3(c.x, GroundShapes.groundY(level, c.x, c.z, c.y), c.z);
            switch (w.shape()) {
                case CIRCLE -> GroundShapes.sector(quads, level, base, 0.0F, w.size(), Mth.PI, fill, edge, camera);
                case LINE -> GroundShapes.rect(quads, level, base, Mth.cos(w.yaw()), Mth.sin(w.yaw()), w.size() / 2.0, w.width() / 2.0,
                        fill, edge, camera);
            }
        }
        event.getRenderState().setRenderData(QUADS, quads);
    }

    private static void onSubmit(SubmitCustomGeometryEvent event) {
        List<GroundShapes.Quad> quads = event.getLevelRenderState().getRenderData(QUADS);
        if (quads != null) {
            GroundShapes.submit(event.getSubmitNodeCollector(), event.getPoseStack(), quads);
        }
    }
}
