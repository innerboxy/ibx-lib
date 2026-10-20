package ibx.lib.client;

import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import ibx.lib.IbxLib;
import ibx.lib.api.CustomFire;
import ibx.lib.config.ClientConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端：身上着火怎么画。
 * <ul>
 *   <li>第三人称身上的火苗（只管换色的火 {@link CustomFire}）：抽取渲染状态时把实体的 {@code fire_look} 抄进状态（{@link #LOOK}），
 *       {@code FlameFeatureRendererMixin} 画火苗时按它换成 {@link #sprite} 的两张图；</li>
 *   <li>第一人称屏幕上的火（原版火和换色火都管）：原版镜头前那两大片挡准星的火不画了（NeoForge 的 {@link RenderBlockScreenEffectEvent}），
 *       改成一个界面层，照粉雪冻结的画法把一张火焰边框拉满屏幕：火只在四角、后面一层暖色热晕，中间全空。
 *       着火 / 熄灭各 {@value #FADE_TICKS} tick 淡入淡出；同原版，有抗火也照画、按 F1 隐藏界面时也画。</li>
 *   <li>客户端配置 {@code replaceFireOverlay} 关掉时，第一人称回到原版的两大片火；换色火照原版的位置用换色的贴图再画一遍。</li>
 * </ul>
 *
 * <p>边框贴图 {@value #OUTLINE_FRAMES} 帧竖着排（每帧 {@value #OUTLINE_W}×{@value #OUTLINE_H}），每帧 {@value #OUTLINE_TICKS_PER_FRAME} tick 循环。
 * 原版火用 {@code ibx_lib:textures/misc/fire_outline.png}；换色火用 look 的 {@code <命名空间>:textures/misc/<路径>_outline.png}，
 * 没有就退回原版的。</p>
 */
public final class CustomFireRendering {
    private CustomFireRendering() {}

    /** 渲染状态里的“烧的是哪种火”；原版火为 null。 */
    public static final ContextKey<Identifier> LOOK = new ContextKey<>(Identifier.fromNamespaceAndPath("ibx_lib", "fire_look"));

    private static final Identifier SCREEN_FIRE_LAYER = Identifier.fromNamespaceAndPath(IbxLib.MODID, "screen_fire");
    private static final Identifier DEFAULT_OUTLINE = Identifier.fromNamespaceAndPath(IbxLib.MODID, "textures/misc/fire_outline.png");
    private static final int OUTLINE_W = 320;
    private static final int OUTLINE_H = 180;
    private static final int OUTLINE_FRAMES = 16;
    private static final int OUTLINE_TICKS_PER_FRAME = 2;
    private static final int FADE_TICKS = 5;

    /** 换色火的 look -> 用哪张边框（自己的或原版的）；资源重载时清掉。 */
    private static final Map<Identifier, Identifier> OUTLINES = new HashMap<>();
    private static int fade;
    private static int fadeO;
    private static long animTicks;
    /** 正在画的是哪种火：着火时每 tick 更新，熄灭淡出时还用灭之前的样子。 */
    private static @Nullable Identifier shownLook;

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

    /** GAME 总线：换成边框时原版第一人称屏幕上的火一律不画（边框由 {@link #renderScreenFire} 画）；不换时只替换色火重画。 */
    public static void onRenderBlockScreenEffect(RenderBlockScreenEffectEvent event) {
        if (event.getOverlayType() != RenderBlockScreenEffectEvent.OverlayType.FIRE) return;
        if (replaceOverlay()) {
            event.setCanceled(true);
            return;
        }
        @Nullable Identifier look = CustomFire.look(event.getPlayer());
        if (look == null) return;
        event.setCanceled(true);
        renderVanillaScreenFire(event.getPoseStack(), event.getBufferSource().getBuffer(RenderTypes.fireScreenEffect(Sheets.BLOCKS_MAPPER.sheet())),
                sprite(look, 1));
    }

    /** MOD 总线：边框画在镜头叠加层（暗角、粉雪冻结）上面、准星和快捷栏下面。 */
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, SCREEN_FIRE_LAYER, CustomFireRendering::renderScreenFire);
    }

    /** MOD 总线：换资源包后重新找换色火的边框。 */
    public static void onAddReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(IbxLib.MODID, "fire_outlines"),
                (ResourceManagerReloadListener) resourceManager -> OUTLINES.clear());
    }

    /** GAME 总线：淡入淡出和边框动画都按客户端 tick 走（暂停时不动）。 */
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) {
            return;
        }
        Player player = mc.player;
        boolean burning = player != null && player.isOnFire() && !player.isSpectator();
        if (burning) {
            shownLook = CustomFire.look(player);
        }
        fadeO = fade;
        fade = Mth.clamp(fade + (burning ? 1 : -1), 0, FADE_TICKS);
        if (fade > 0 || fadeO > 0) {
            animTicks++;
        }
    }

    private static void renderScreenFire(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!replaceOverlay() || mc.player == null || !mc.options.getCameraType().isFirstPerson()
                || mc.getCameraEntity() instanceof LivingEntity camera && camera.isSleeping()) {
            return;
        }
        float alpha = Mth.lerp(deltaTracker.getGameTimeDeltaPartialTick(false), fadeO, fade) / FADE_TICKS;
        if (alpha <= 0) {
            return;
        }
        int frame = (int) (animTicks / OUTLINE_TICKS_PER_FRAME % OUTLINE_FRAMES);
        graphics.blit(RenderPipelines.GUI_TEXTURED, outline(shownLook), 0, 0, 0, frame * OUTLINE_H,
                graphics.guiWidth(), graphics.guiHeight(), OUTLINE_W, OUTLINE_H, OUTLINE_W, OUTLINE_H * OUTLINE_FRAMES, ARGB.white(alpha));
    }

    private static Identifier outline(@Nullable Identifier look) {
        if (look == null) {
            return DEFAULT_OUTLINE;
        }
        return OUTLINES.computeIfAbsent(look, l -> {
            Identifier own = l.withPath(p -> "textures/misc/" + p + "_outline.png");
            return Minecraft.getInstance().getResourceManager().getResource(own).isPresent() ? own : DEFAULT_OUTLINE;
        });
    }

    private static boolean replaceOverlay() {
        return ClientConfig.get(ClientConfig.REPLACE_FIRE_OVERLAY);
    }

    /** 照原版 {@code ScreenEffectRenderer.renderFire}：屏幕两侧各一片斜着的火，略透明。 */
    private static void renderVanillaScreenFire(PoseStack poseStack, VertexConsumer builder, TextureAtlasSprite sprite) {
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
