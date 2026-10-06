package ibx.taverntales.lib.client;

import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import ibx.taverntales.lib.api.CustomFire;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * 客户端：换颜色的火（{@link CustomFire}）怎么画。
 * <ul>
 *   <li>第三人称身上的火苗：抽取渲染状态时把实体的 {@code fire_look} 抄进状态（{@link #LOOK}），
 *       {@code FlameFeatureRendererMixin} 画火苗时按它换成 {@link #sprite} 的两张图；</li>
 *   <li>第一人称屏幕上的火：自己身上是换色的火时取消原版的屏幕火（NeoForge 的 {@link RenderBlockScreenEffectEvent}），
 *       照原版的画法用换色的贴图再画一遍。</li>
 * </ul>
 */
public final class CustomFireRendering {
    private CustomFireRendering() {}

    /** 渲染状态里的“烧的是哪种火”；原版火为 null。 */
    public static final ContextKey<Identifier> LOOK = new ContextKey<>(Identifier.fromNamespaceAndPath("taverntales_l1b", "fire_look"));

    /** 方块图集里的那张：look 的 {@code block/<路径>_<index>}。 */
    public static TextureAtlasSprite sprite(Identifier look, int index) {
        return Minecraft.getInstance().getAtlasManager().get(Sheets.BLOCKS_MAPPER.apply(look.withSuffix("_" + index)));
    }

    /** MOD 总线：所有实体渲染器抽取状态之后，把实体烧的是哪种火抄进去。 */
    public static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {}, (entity, state) -> {
            Identifier look = state.displayFireAnimation ? CustomFire.look(entity) : null;
            state.setRenderData(LOOK, look);
        });
    }

    /** GAME 总线：第一人称屏幕上的火。 */
    public static void onRenderBlockScreenEffect(RenderBlockScreenEffectEvent event) {
        if (event.getOverlayType() != RenderBlockScreenEffectEvent.OverlayType.FIRE) return;
        @Nullable Identifier look = CustomFire.look(event.getPlayer());
        if (look == null) return;
        event.setCanceled(true);
        renderScreenFire(event.getPoseStack(), event.getBufferSource().getBuffer(RenderTypes.fireScreenEffect(Sheets.BLOCKS_MAPPER.sheet())),
                sprite(look, 1));
    }

    /** 照原版 {@code ScreenEffectRenderer.renderFire}：屏幕两侧各一片斜着的火，略透明。 */
    private static void renderScreenFire(PoseStack poseStack, VertexConsumer builder, TextureAtlasSprite sprite) {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        for (int i = 0; i < 2; i++) {
            poseStack.pushPose();
            poseStack.translate(-(i * 2 - 1) * 0.24F, -0.3F, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees((i * 2 - 1) * 10.0F));
            Matrix4f pose = poseStack.last().pose();
            builder.addVertex(pose, -0.5F, -0.5F, -0.5F).setUv(u1, v1).setColor(1.0F, 1.0F, 1.0F, 0.9F);
            builder.addVertex(pose, 0.5F, -0.5F, -0.5F).setUv(u0, v1).setColor(1.0F, 1.0F, 1.0F, 0.9F);
            builder.addVertex(pose, 0.5F, 0.5F, -0.5F).setUv(u0, v0).setColor(1.0F, 1.0F, 1.0F, 0.9F);
            builder.addVertex(pose, -0.5F, 0.5F, -0.5F).setUv(u1, v0).setColor(1.0F, 1.0F, 1.0F, 0.9F);
            poseStack.popPose();
        }
    }
}
