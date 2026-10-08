package ibx.lib.client.renderer;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ibx.lib.entity.SpellProjectile;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 法术弹射物的 3D 模型渲染器（GeckoLib）：模型顺着飞行方向，中心在碰撞箱正中，飞行中循环播
 * {@code SpellProjectile#flyAnimation}。各模组注册实体渲染器时直接用：
 *
 * <pre>{@code
 * event.registerEntityRenderer(MAGIC_MISSILE.get(), ctx -> new GeoSpellProjectileRenderer<>(ctx,
 *         Identifier.fromNamespaceAndPath("taverntales_2me", "spell/magic_missile")));
 * }</pre>
 *
 * <ul>
 *   <li>文件走 GeckoLib 的默认路径：{@code geckolib/models/entity/<路径>.geo.json}、
 *       {@code geckolib/animations/entity/<路径>.animation.json}、{@code textures/entity/<路径>.png}；</li>
 *   <li>建模约定：尖头朝 Blockbench 的北面（-Z），模型中心放在原点。渲染时原点对准碰撞箱中心，再整个转到飞行方向；</li>
 *   <li>朝向按运动算，不用实体的 yRot / xRot（追踪时每 tick 都在拐）：在上一 tick 走过的位移和这一 tick 的速度之间按
 *       partialTick 插值，拐弯是平滑的；</li>
 *   <li>{@code emissive} 为 true 时自发光：不吃光照，夜里、洞里也是贴图本来的颜色，但各面仍按朝向分明暗，看得出立体。</li>
 * </ul>
 */
public class GeoSpellProjectileRenderer<T extends SpellProjectile, R extends EntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<T, R> {
    /** 这一帧的飞行方向（没归一化）。 */
    private static final DataTicket<Vec3> DIRECTION = DataTicket.create("taverntales_spell_projectile_direction", Vec3.class);

    private final boolean emissive;

    /** 按默认路径找模型、动画、贴图，自发光。 */
    public GeoSpellProjectileRenderer(EntityRendererProvider.Context context, Identifier asset) {
        this(context, new DefaultedEntityGeoModel<>(asset), true);
    }

    public GeoSpellProjectileRenderer(EntityRendererProvider.Context context, GeoModel<T> model, boolean emissive) {
        super(context, model);
        this.emissive = emissive;
    }

    @Override
    public void addRenderData(T entity, Void relatedObject, R renderState, float partialTick) {
        super.addRenderData(entity, relatedObject, renderState, partialTick);
        Vec3 moved = entity.position().subtract(entity.xo, entity.yo, entity.zo);
        Vec3 direction = moved.lerp(entity.getDeltaMovement(), partialTick);
        if (direction.lengthSqr() < 1.0E-8) {
            direction = Vec3.directionFromRotation(entity.getXRot(), entity.getYRot());
        }
        renderState.addGeckolibData(DIRECTION, direction);
    }

    /** 在缩放之前把原点挪到碰撞箱中心，模型怎么缩放都绕中心。 */
    @Override
    public void preRenderPass(RenderPassInfo<R> renderPassInfo, SubmitNodeCollector renderTasks) {
        renderPassInfo.poseStack().translate(0.0F, renderPassInfo.renderState().boundingBoxHeight / 2.0F, 0.0F);
    }

    /** 模型的 -Z 转到飞行方向：先抬头（绕 X），再转身（绕 Y）。 */
    @Override
    protected void applyRotations(RenderPassInfo<R> renderPassInfo, PoseStack poseStack, float nativeScale) {
        Vec3 direction = renderPassInfo.renderState().getOrDefaultGeckolibData(DIRECTION, Vec3.ZERO);
        if (direction.lengthSqr() < 1.0E-8) {
            return;
        }
        poseStack.mulPose(Axis.YP.rotation((float) Mth.atan2(-direction.x, -direction.z)));
        poseStack.mulPose(Axis.XP.rotation((float) Mth.atan2(direction.y, direction.horizontalDistance())));
    }

    @Override
    public @Nullable RenderType getRenderType(R renderState, Identifier texture) {
        return this.emissive ? RenderTypes.entityTranslucentEmissive(texture) : super.getRenderType(renderState, texture);
    }
}
