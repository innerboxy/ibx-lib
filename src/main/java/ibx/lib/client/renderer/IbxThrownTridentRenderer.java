package ibx.lib.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ibx.lib.entity.IbxThrownTrident;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * 掷出去的 Ibx 三叉戟的通用渲染器：画掷出去的那把戟自己的物品模型，戟尖顺着飞行方向，附魔光效跟着物品走。
 * 各模组给自己的实体类型注册时直接用：
 *
 * <pre>{@code
 * event.registerEntityRenderer(MY_TRIDENT.get(), IbxThrownTridentRenderer::new);
 * }</pre>
 *
 * <ul>
 *   <li>物品模型按 {@link ItemDisplayContext#NONE} 取（不带任何显示变换）：物品 json 照原版矛那样按显示位置分
 *       （物品栏、地上、展示框用小图标，其余用手持模型），这里拿到的就是手持模型。</li>
 *   <li>默认按「斜放的平面贴图」摆：戟尖在贴图右上角、戟尾在左下角（同 {@code ibx_lib:item/trident_in_hand}），
 *       大小同第三人称手持（{@link #DEFAULT_SCALE} 倍、厚度减半），戟尖比实体的位置靠前一点，扎进方块里一截。</li>
 *   <li>模型不是这种摆法的（3D 模型、戟尖朝别的方向），继承本类重写 {@link #applyModelPose}。</li>
 * </ul>
 */
public class IbxThrownTridentRenderer<T extends IbxThrownTrident> extends EntityRenderer<T, IbxThrownTridentRenderer.State> {
    /** 默认大小：同 {@code ibx_lib:item/trident_in_hand} 第三人称的缩放。 */
    public static final float DEFAULT_SCALE = 1.7F;
    /** 平面贴图中心到角的距离（格，缩放 1 时）。 */
    private static final float HALF_DIAGONAL = 0.70710677F;
    /** 戟尖比实体的位置靠前多少格（同原版三叉戟模型，扎进方块里的那一截）。 */
    private static final float TIP_AHEAD = 0.25F;

    private final ItemModelResolver itemModelResolver;
    private final float scale;

    public IbxThrownTridentRenderer(EntityRendererProvider.Context context) {
        this(context, DEFAULT_SCALE);
    }

    /** @param scale 物品模型放大几倍 */
    public IbxThrownTridentRenderer(EntityRendererProvider.Context context, float scale) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
        this.scale = scale;
    }

    /**
     * 把物品模型摆成「戟尖朝 +X」：调用时坐标系的 +X 已经是飞行方向、原点是实体的位置，物品模型的中心在原点。
     * 默认把斜放的平面贴图转 45° 放平，再往后退到戟尖略超过原点。
     */
    protected void applyModelPose(PoseStack poseStack) {
        poseStack.translate(TIP_AHEAD - HALF_DIAGONAL * this.scale, 0.0F, 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-45.0F));
        poseStack.scale(this.scale, this.scale, this.scale * 0.5F);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = entity.getYRot(partialTicks);
        state.xRot = entity.getXRot(partialTicks);
        this.itemModelResolver.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.NONE, entity);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        // 箭类实体的朝向：先绕 Y 转 yRot - 90、再绕 Z 转 xRot，之后 +X 就是飞行方向（同原版箭）
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.xRot));
        this.applyModelPose(poseStack);
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    /** 渲染状态：朝向和掷出去的那把戟的物品模型。 */
    public static class State extends EntityRenderState {
        public float yRot;
        public float xRot;
        public final ItemStackRenderState item = new ItemStackRenderState();
    }
}
