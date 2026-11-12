package ibx.lib.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ibx.lib.entity.SpellBeam;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
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

import java.util.List;

/**
 * 法术射线的通用渲染器：从起点到终点画一根或几根同轴的贴图方管（四个面，照原版守卫者射线、信标光柱的做法），
 * 贴图横向是一个面的宽度，纵向沿射线铺、可以随时间往外流。各模组注册实体渲染器时直接用：
 *
 * <pre>{@code
 * // 一层
 * event.registerEntityRenderer(FROST_BEAM.get(), ctx -> new SpellBeamRenderer<>(ctx,
 *         Identifier.fromNamespaceAndPath("examplemod", "textures/entity/spell/frost_beam.png"), true, 1.0F, 0.1F));
 * // 两层：外面一层半透明的辉光，里面一根细芯（后写的画在上面）
 * event.registerEntityRenderer(EMERALD_BEAM.get(), ctx -> new SpellBeamRenderer<>(ctx, true,
 *         new SpellBeamRenderer.Layer(GLOW_TEXTURE, 1.0F, 1.2F, 0.2F),
 *         new SpellBeamRenderer.Layer(CORE_TEXTURE, 0.33F, 0.8F, 0.3F)));
 * }</pre>
 *
 * <ul>
 *   <li>起点、终点每帧从实体问（{@link SpellBeam#getStart} / {@link SpellBeam#getEnd}）：本机玩家的在本地按胸口和准星算、跟手，
 *       别人的、生物的用服务端同步的两端插值；</li>
 *   <li>方管的四个面绕射线固定（不朝镜头转），从任何角度看粗细都一样，施法者第一人称看也是一个小方口。
 *       所以<b>贴图横向不能中间亮两边暗</b>：斜着看时两个面并排、每个面的边正好落在射线正中，会裂成两条；
 *       要中间亮就再加一层细芯；</li>
 *   <li>几层按写的顺序画（{@link SubmitNodeCollector#order}），后面的盖在前面的上面——芯要写在最后，
 *       不然会被半透明的外层罩住；</li>
 *   <li>{@code fullBright} 为 true 时自发光：用原版 eyes 渲染类型（半透明、不写深度、不吃光照，各个面一样亮），它剔除背面，
 *       所以每个面正反各画一遍，射线另一侧的面也从里面透出来，和不剔除背面的半透明一样是两层叠着；
 *       false 时用半透明实体渲染类型，吃环境光、各个面按朝向明暗；</li>
 *   <li>剔除按整条射线的包围盒算，不然起点出了屏幕整条就不画了；</li>
 *   <li>镜头周围一个球里的那段不画（{@link #CAMERA_CLEARANCE}）：第一人称时起点（胸口）就在镜头下面零点几格，
 *       抬头时镜头贴着管壁甚至钻进管子里，贴图被放大到半屏、满屏还在往外流，看着就是天空一闪一闪；被射线照脸时终点也贴着镜头。</li>
 * </ul>
 */
public class SpellBeamRenderer<T extends SpellBeam> extends EntityRenderer<T, SpellBeamRenderer.State> {
    /**
     * 一层方管。
     *
     * @param texture       贴图（完整路径，含 {@code textures/} 和 {@code .png}）：横向是一个面的宽度，纵向沿射线铺
     * @param widthScale    粗细，相对射线粗细（{@link SpellBeam#getWidth}）的比例
     * @param textureLength 贴图纵向重复一次对应多少格
     * @param scrollSpeed   贴图每 tick 沿射线往外滑多少格；0 不动，负数往回流
     */
    public record Layer(Identifier texture, float widthScale, float textureLength, float scrollSpeed) {
    }

    /** 镜头周围多大的球里不画（格），再加上最粗那层的半对角线，镜头在管子正中时也碰不到管壁。 */
    private static final double CAMERA_CLEARANCE = 0.6;
    /** 挖完剩下比这还短的段就不画了（格）。 */
    private static final double MIN_SEGMENT = 1.0E-3;

    private final List<Layer> layers;
    private final List<RenderType> renderTypes;
    private final boolean fullBright;
    /** 最粗那一层的粗细比例：剔除和镜头附近挖掉多少都按它算。 */
    private final float widestScale;

    /** 一层，贴图每 1 格重复一次、不流动。 */
    public SpellBeamRenderer(EntityRendererProvider.Context context, Identifier texture, boolean fullBright) {
        this(context, texture, fullBright, 1.0F, 0.0F);
    }

    /**
     * 一层，粗细就是射线的粗细。
     *
     * @param texture       贴图（完整路径，含 {@code textures/} 和 {@code .png}）：横向是粗细，纵向沿射线铺
     * @param fullBright    是否自发光
     * @param textureLength 贴图纵向重复一次对应多少格
     * @param scrollSpeed   贴图每 tick 沿射线往外滑多少格；0 不动，负数往回流
     */
    public SpellBeamRenderer(EntityRendererProvider.Context context, Identifier texture, boolean fullBright,
                             float textureLength, float scrollSpeed) {
        this(context, fullBright, new Layer(texture, 1.0F, textureLength, scrollSpeed));
    }

    /** 几层同轴方管，按写的顺序画，后面的盖在前面的上面（外层辉光写前面、芯写最后）。 */
    public SpellBeamRenderer(EntityRendererProvider.Context context, boolean fullBright, Layer... layers) {
        super(context);
        if (layers.length == 0) {
            throw new IllegalArgumentException("SpellBeamRenderer needs at least one layer");
        }
        this.layers = List.of(layers);
        this.renderTypes = this.layers.stream()
                .map(layer -> fullBright ? RenderTypes.eyes(layer.texture()) : RenderTypes.entityTranslucent(layer.texture()))
                .toList();
        this.fullBright = fullBright;
        float widest = 0.0F;
        for (Layer layer : layers) {
            widest = Math.max(widest, layer.widthScale());
        }
        this.widestScale = widest;
    }

    @Override
    protected int getBlockLightLevel(T entity, BlockPos pos) {
        return this.fullBright ? 15 : super.getBlockLightLevel(entity, pos);
    }

    /** 剔除按整条射线算（用最粗那一层）。 */
    @Override
    protected AABB getBoundingBoxForCulling(T entity) {
        return new AABB(entity.getStart(1.0F), entity.getEnd(1.0F)).inflate(entity.getWidth() * this.widestScale);
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
        state.age = entity.tickCount + partialTicks;
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
            Vec3 unitU = side.normalize();
            Vec3 unitV = axis.cross(unitU).normalize();
            int light = state.lightCoords;
            double[] segments = visibleSegments(state.start, axis, length, camera.pos,
                    CAMERA_CLEARANCE + state.width * this.widestScale * Math.sqrt(0.5));

            // poseStack 的原点在实体自己的插值位置，挪到起点
            poseStack.pushPose();
            poseStack.translate(state.start.x - state.x, state.start.y - state.y, state.start.z - state.z);
            for (int i = 0; i < this.layers.size(); i++) {
                Layer layer = this.layers.get(i);
                double half = state.width * layer.widthScale() / 2.0;
                Vec3 u = unitU.scale(half);
                Vec3 v = unitV.scale(half);
                Vec3[] corners = {u.add(v), u.subtract(v), u.scale(-1.0).subtract(v), v.subtract(u)};
                float textureLength = Math.max(0.01F, layer.textureLength());
                float scroll = -state.age * layer.scrollSpeed() / textureLength;
                OrderedSubmitNodeCollector target = i == 0 ? collector : collector.order(i);
                target.submitCustomGeometry(poseStack, this.renderTypes.get(i), (pose, buffer) -> {
                    for (int s = 0; s < segments.length; s += 2) {
                        double from = segments[s];
                        double to = segments[s + 1];
                        tube(buffer, pose, light, corners, axis.scale(from), axis.scale(to - from),
                                scroll + (float) from / textureLength, scroll + (float) to / textureLength, this.fullBright);
                    }
                });
            }
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);
    }

    /**
     * 射线上要画的几段，按离起点的距离成对给出 {@code [from0, to0, from1, to1, ...]}：整条减去镜头周围半径 {@code clearance} 的球。
     * 镜头离射线远时就是整条；挨着起点或终点时剩一段；在射线中间时剩两段；整条都在球里时一段不剩。
     */
    private static double[] visibleSegments(Vec3 start, Vec3 axis, double length, Vec3 cameraPos, double clearance) {
        Vec3 toCamera = cameraPos.subtract(start);
        double along = toCamera.dot(axis);
        double offAxisSqr = toCamera.lengthSqr() - along * along;
        if (offAxisSqr >= clearance * clearance) {
            return new double[]{0.0, length};
        }
        double half = Math.sqrt(clearance * clearance - offAxisSqr);
        double cutFrom = along - half;
        double cutTo = along + half;
        if (cutTo <= 0.0 || cutFrom >= length) {
            return new double[]{0.0, length};
        }
        boolean before = cutFrom > MIN_SEGMENT;
        boolean after = length - cutTo > MIN_SEGMENT;
        if (before && after) {
            return new double[]{0.0, cutFrom, cutTo, length};
        }
        if (before) {
            return new double[]{0.0, cutFrom};
        }
        return after ? new double[]{cutTo, length} : new double[0];
    }

    /**
     * 一根方管的四个面。每个面从外面看是逆时针（正面朝外，法线朝外，按面受光时亮面在上）；{@code bothSides} 时再反着画一遍，
     * 给剔除背面的渲染类型用，射线另一侧的面才能从里面透出来。{@code offset} 是这一段的起点（相对射线起点），{@code along} 是这一段本身。
     */
    private static void tube(VertexConsumer buffer, PoseStack.Pose pose, int light, Vec3[] corners, Vec3 offset, Vec3 along,
                             float v0, float v1, boolean bothSides) {
        for (int i = 0; i < 4; i++) {
            Vec3 a = corners[i].add(offset);
            Vec3 b = corners[(i + 1) % 4].add(offset);
            Vec3 normal = corners[i].add(corners[(i + 1) % 4]).normalize();
            vertex(buffer, pose, light, a, 0.0F, v0, normal);
            vertex(buffer, pose, light, a.add(along), 0.0F, v1, normal);
            vertex(buffer, pose, light, b.add(along), 1.0F, v1, normal);
            vertex(buffer, pose, light, b, 1.0F, v0, normal);
            if (bothSides) {
                Vec3 inward = normal.scale(-1.0);
                vertex(buffer, pose, light, a, 0.0F, v0, inward);
                vertex(buffer, pose, light, b, 1.0F, v0, inward);
                vertex(buffer, pose, light, b.add(along), 1.0F, v1, inward);
                vertex(buffer, pose, light, a.add(along), 0.0F, v1, inward);
            }
        }
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, int light, Vec3 point, float u, float v, Vec3 normal) {
        buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    /** 渲染状态：这一帧的两端（世界坐标）、粗细、存在了多少 tick（各层按自己的速度算贴图滑动量）。 */
    public static class State extends EntityRenderState {
        Vec3 start = Vec3.ZERO;
        Vec3 end = Vec3.ZERO;
        float width;
        float age;
    }
}
