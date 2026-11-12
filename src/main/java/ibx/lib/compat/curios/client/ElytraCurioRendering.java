package ibx.lib.compat.curios.client;

import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import ibx.lib.IbxLib;
import ibx.lib.client.InvisibilityRendering;
import ibx.lib.compat.curios.ElytraCurioSlot;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.jspecify.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * 鞘翅槽（{@link ElytraCurioSlot}）里的鞘翅画在玩家背上，画法同原版的鞘翅层（{@code WingsLayer}）。只在客户端。
 *
 * <ul>
 *   <li>画哪件同 Curios 自己的饰品渲染：这一格的化妆栏有东西就画化妆栏的；没有就看这一格的显示开关，开着才画正身那件。</li>
 *   <li>物品要有带鞘翅层（{@code wings}）的装备外观（{@code minecraft:equippable} 的 {@code asset_id}），原版鞘翅就是；
 *       附魔光效、玩家皮肤自带的鞘翅图、披风图案照原版。</li>
 *   <li>胸甲栏已经有鞘翅（原版那层在画）时不再画，免得叠两层。</li>
 *   <li>画了鞘翅就不画披风，同原版（{@code ibx.lib.mixin.curios.CapeLayerMixin}）。</li>
 *   <li>隐身时按本模组的「隐身连装备一起藏」（服务端配置 {@code hideEquipmentWhenInvisible}）一起藏，见 {@code InvisibilityRendering}。</li>
 * </ul>
 *
 * 引用了 Curios 的类，只在装了 Curios 时注册。
 */
public final class ElytraCurioRendering {
    /** 这一帧要画在背上的鞘翅槽物品（化妆栏或正身那件）。没有就不设。 */
    public static final ContextKey<ItemStack> WINGS = new ContextKey<>(Identifier.fromNamespaceAndPath(IbxLib.MODID, "elytra_curio_wings"));

    private ElytraCurioRendering() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ElytraCurioRendering::onRegisterRenderStateModifiers);
        modEventBus.addListener(ElytraCurioRendering::onAddLayers);
    }

    private static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {}, (entity, state) -> {
            if (!(entity instanceof Player) || !(state instanceof AvatarRenderState)) return;
            if (InvisibilityRendering.hidesEquipment(entity, state)) return;
            ItemStack shown = shownStack(entity);
            if (!shown.isEmpty()) {
                state.setRenderData(WINGS, shown);
            }
        });
    }

    /** 鞘翅槽第一格起，第一件要画的：化妆栏优先，否则显示开关开着的正身那件。 */
    private static ItemStack shownStack(LivingEntity entity) {
        ICurioStacksHandler handler = CuriosApi.getCuriosInventory(entity)
                .flatMap(inventory -> inventory.getStacksHandler(ElytraCurioSlot.SLOT))
                .orElse(null);
        if (handler == null) return ItemStack.EMPTY;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack cosmetic = handler.getCosmeticStacks().getStackInSlot(i);
            if (!cosmetic.isEmpty()) return cosmetic;
            boolean visible = i < handler.getRenders().size() && handler.getRenders().get(i);
            ItemStack stack = handler.getStacks().getStackInSlot(i);
            if (visible && !stack.isEmpty()) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerModelType type : event.getSkins()) {
            AvatarRenderer<AbstractClientPlayer> renderer = event.getPlayerRenderer(type);
            if (renderer != null) {
                renderer.addLayer(new Layer(renderer, event.getContext()));
            }
        }
    }

    /** 物品的装备外观里有没有鞘翅层。 */
    static boolean hasWingsLayer(EquipmentAssetManager assets, ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.assetId().isPresent()
                && !assets.get(equippable.assetId().get()).getLayers(EquipmentClientInfo.LayerType.WINGS).isEmpty();
    }

    private static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
        private final ElytraModel model;
        private final ElytraModel babyModel;
        private final EquipmentLayerRenderer equipmentRenderer;
        private final EquipmentAssetManager assets;

        Layer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityRendererProvider.Context context) {
            super(parent);
            this.model = new ElytraModel(context.bakeLayer(ModelLayers.ELYTRA));
            this.babyModel = new ElytraModel(context.bakeLayer(ModelLayers.ELYTRA_BABY));
            this.equipmentRenderer = context.getEquipmentRenderer();
            this.assets = context.getEquipmentAssets();
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
            ItemStack stack = state.getRenderData(WINGS);
            if (stack == null || !hasWingsLayer(this.assets, stack) || hasWingsLayer(this.assets, state.chestEquipment)) return;
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, 0.125F);
            this.equipmentRenderer.renderLayers(
                    EquipmentClientInfo.LayerType.WINGS,
                    stack.get(DataComponents.EQUIPPABLE).assetId().get(),
                    state.isBaby ? this.babyModel : this.model,
                    state,
                    stack,
                    poseStack,
                    collector,
                    light,
                    playerTexture(state),
                    state.outlineColor,
                    0);
            poseStack.popPose();
        }

        /** 同原版：皮肤自带鞘翅图就用它，否则披风开着时用披风图。 */
        private static @Nullable Identifier playerTexture(AvatarRenderState state) {
            PlayerSkin skin = state.skin;
            if (skin.elytra() != null) return skin.elytra().texturePath();
            if (skin.cape() != null && state.showCape) return skin.cape().texturePath();
            return null;
        }
    }
}
