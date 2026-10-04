package ibx.taverntales.lib.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ibx.taverntales.lib.entity.SpellBeam;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 法术射线的通用渲染器：从起点到终点画一根贴图方管（四个面，照原版守卫者射线、信标光柱的做法），
 * 贴图横向是粗细、纵向沿射线铺、可以随时间往外流。各模组注册实体渲染器时直接用：
 *
 * <pre>{@code
 * event.registerEntityRenderer(FROST_BEAM.get(), ctx -> new SpellBeamRenderer<>(ctx,
 *         Identifier.fromNamespaceAndPath("taverntales_2me", "textures/entity/spell/frost_beam.png"), true, 1.0F, 0.1F));
 * }</pre>
 *
 * <ul>
 *   <li>起点、终点每帧从实体问（{@link SpellBeam#getStart} / {@link SpellBeam#getEnd}）：本机玩家的在本地按胸口和准星算、跟手，
 *       别人的、生物的用服务端同步的两端插值；</li>
 *   <li>方管的四个面绕射线固定（不朝镜头转），从任何角度看粗细都一样，施法者第一人称看也是一个小方口；</li>
 *   <li>剔除按整条射线的包围盒算，不然起点出了屏幕整条就不画了；</li>
 *   <li>{@code fullBright} 为 true 时自发光（半透明发光的渲染类型，不吃光照）。</li>
 * </ul>
 */
public class SpellBeamRenderer<T extends SpellBeam> extends EntityRenderer<T, SpellBeamRenderer.State> {
    private final RenderType renderType;
    private final boolean fullBright;
    private final float textureLength;
    private final float scrollSpeed;

    /** 贴图每 1 格重复一次、不流动。 */
    public SpellBeamRenderer(EntityRendererProvider.Context context, Identifier texture, boolean fullBright) {
        this(context, texture, fullBright, 1.0F, 0.0F);
    }

    /**
     * @param texture       贴图（完整路径，含 {@code textures/} 和 {@code .png}）：横向是粗细，纵向沿射线铺
     * @param fullBright    是否自发光
     * @param textureLength 贴图纵向重复一次对应多少格
     * @param scrollSpeed   贴图每 tick 沿射线往外滑多少格；0 不动，负数往回流
     */
    public SpellBeamRenderer(EntityRendererProvider.Context context, Identifier texture, boolean fullBright,
                             float textureLength, float scrollSpeed) {
        super(context);
        this.renderType = fullBright ? RenderTypes.entityTranslucentEmissive(texture) : RenderTypes.entityTranslucent(texture);
        this.fullBright = fullBright;
        this.textureLength = Math.max(0.01F, textureLength);
        this.scrollSpeed = scrollSpeed;
    }

    @Override
    protected int getBlockLightLevel(T entity, BlockPos pos) {
        return this.fullBright ? 15 : super.getBlockLightLevel(entity, pos);
    }

    /** 剔除按整条射线算。 */
    @Override
    protected AABB getBoundingBoxForCulling(T entity) {
        return new AABB(entity.getStart(1.0F), entity.getEnd(1.0F)).inflate(entity.getWidth());
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.start = entity.getStart(partialTicks);
        state.end = entity.getEnd(partialTicks);
        state.width = entity.getWidth();
        state.scroll = (entity.tickCount + partialTicks) * this.scrollSpeed;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Vec3 span = state.end.subtract(state.start);
        double length = span.length();
        if (length > 1.0E-3) {
            Vec3 axis = span.scale(1.0 / length);
            // 两个垂直于射线的方向定出四个角；射线竖直时换个参考轴
            Vec3 side = axis.cross(new Vec3(0.0, 1.0, 0.0));
            if (side.lengthSqr() < 1.0E-6) {
                side = axis.cross(new Vec3(1.0, 0.0, 0.0));
            }
            double half = state.width / 2.0;
            Vec3 u = side.normalize().scale(half);
            Vec3 v = axis.cross(u).normalize().scale(half);
            Vec3[] corners = {u.add(v), u.subtract(v), u.scale(-1.0).subtract(v), v.subtract(u)};
            Vec3 along = span;
            float v0 = -state.scroll / this.textureLength;
            float v1 = v0 + (float) length / this.textureLength;
            int light = state.lightCoords;

            // poseStack 的原点在实体自己的插值位置，挪到起点
            poseStack.pushPose();
            poseStack.translate(state.start.x - state.x, state.start.y - state.y, state.start.z - state.z);
            collector.submitCustomGeometry(poseStack, this.renderType, (pose, buffer) -> {
                for (int i = 0; i < 4; i++) {
                    Vec3 a = corners[i];
                    Vec3 b = corners[(i + 1) % 4];
                    Vec3 normal = a.add(b).normalize();
                    vertex(buffer, pose, light, a, 0.0F, v0, normal);
                    vertex(buffer, pose, light, b, 1.0F, v0, normal);
                    vertex(buffer, pose, light, b.add(along), 1.0F, v1, normal);
                    vertex(buffer, pose, light, a.add(along), 0.0F, v1, normal);
                }
            });
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, int light, Vec3 point, float u, float v, Vec3 normal) {
        buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    /** 渲染状态：这一帧的两端（世界坐标）、粗细、贴图滑动量。 */
    public static class State extends EntityRenderState {
        Vec3 start = Vec3.ZERO;
        Vec3 end = Vec3.ZERO;
        float width;
        float scroll;
    }
}
