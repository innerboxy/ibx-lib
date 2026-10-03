package ibx.taverntales.lib.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ibx.taverntales.lib.entity.SpellProjectile;
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

/**
 * 法术弹射物的通用渲染器：一张始终朝向镜头的方形图，中心在碰撞箱正中（照原版末影龙火球、m0b 的胆汁团）。
 * 各模组注册实体渲染器时直接用，不用每个法术自己写：
 *
 * <pre>{@code
 * event.registerEntityRenderer(FIREBALL.get(), ctx -> new SpellProjectileRenderer<>(ctx,
 *         Identifier.fromNamespaceAndPath("taverntales_2me", "textures/entity/spell/fireball.png"), 0.5F, true));
 * }</pre>
 *
 * <ul>
 *   <li>面的法线朝上：原版给实体打的是从上方来的方向光，直接朝镜头的面会被压暗，法线朝上才是贴图本来的颜色；</li>
 *   <li>{@code fullBright} 为 true 时亮度按 15 算（火球、魔法弹这类自己发光的），夜里、洞里也是亮的。</li>
 * </ul>
 */
public class SpellProjectileRenderer<T extends SpellProjectile> extends EntityRenderer<T, SpellProjectileRenderer.State> {
    private final RenderType renderType;
    private final float half;
    private final boolean fullBright;

    /**
     * @param texture    贴图（完整路径，含 {@code textures/} 和 {@code .png}）
     * @param size       边长（格）
     * @param fullBright 是否自发光
     */
    public SpellProjectileRenderer(EntityRendererProvider.Context context, Identifier texture, float size, boolean fullBright) {
        super(context);
        this.renderType = RenderTypes.entityCutout(texture);
        this.half = size / 2.0F;
        this.fullBright = fullBright;
    }

    @Override
    protected int getBlockLightLevel(T entity, BlockPos pos) {
        return this.fullBright ? 15 : super.getBlockLightLevel(entity, pos);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.lift = entity.getBbHeight() / 2.0F;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.0F, state.lift, 0.0F);
        poseStack.mulPose(camera.orientation);
        float half = this.half;
        collector.submitCustomGeometry(poseStack, this.renderType, (pose, buffer) -> {
            vertex(buffer, pose, state.lightCoords, -half, -half, 0, 1);
            vertex(buffer, pose, state.lightCoords, half, -half, 1, 1);
            vertex(buffer, pose, state.lightCoords, half, half, 1, 0);
            vertex(buffer, pose, state.lightCoords, -half, half, 0, 0);
        });
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, int light, float x, float y, int u, int v) {
        buffer.addVertex(pose, x, y, 0.0F)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /** 渲染状态：多记碰撞箱半高（图的中心放在那里）。 */
    public static class State extends EntityRenderState {
        float lift;
    }
}
