package ibx.taverntales.lib.client;

import ibx.taverntales.lib.api.RangedWeaponApi;
import ibx.taverntales.lib.item.TtBowItem;
import ibx.taverntales.lib.item.TtCrossbowItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 给所有 Tt 弓弩注册模型谓词（拉弓动画）。
 *
 * <p>原版谓词是按物品实例注册的（只给 {@code Items.BOW} / {@code Items.CROSSBOW}），所以这里在
 * 客户端初始化时遍历物品注册表，凡是 {@link TtBowItem} / {@link TtCrossbowItem} 的实例都注册一遍，
 * 其他 mod 用这两个类注册的武器也一并覆盖。谓词名沿用原版（pull / pulling / charged / firework），
 * 模型 JSON 可以照抄原版 bow.json / crossbow.json 的 overrides。</p>
 *
 * <p>弓的 pull 原版写死除以 20 tick，这里改为除以玩家 draw_time 算出的满蓄 tick，
 * 让三个拉弓阶段跟着自定义拉弓时间走。</p>
 */
public final class RangedWeaponModelPredicates {
    private RangedWeaponModelPredicates() {}

    private static final ResourceLocation PULL = ResourceLocation.withDefaultNamespace("pull");
    private static final ResourceLocation PULLING = ResourceLocation.withDefaultNamespace("pulling");
    private static final ResourceLocation CHARGED = ResourceLocation.withDefaultNamespace("charged");
    private static final ResourceLocation FIREWORK = ResourceLocation.withDefaultNamespace("firework");

    static void onClientSetup(FMLClientSetupEvent event) {
        // ItemProperties.register 不是线程安全的，必须放到主线程。
        event.enqueueWork(() -> {
            for (Item item : BuiltInRegistries.ITEM) {
                if (item instanceof TtBowItem) {
                    registerBow(item);
                } else if (item instanceof TtCrossbowItem) {
                    registerCrossbow(item);
                }
            }
        });
    }

    private static void registerBow(Item item) {
        ItemProperties.register(item, PULL, (stack, level, entity, seed) -> {
            if (entity == null || entity.getUseItem() != stack) {
                return 0.0F;
            }
            return (float) (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks())
                    / (float) RangedWeaponApi.getFullDrawTicks(entity, stack);
        });
        ItemProperties.register(item, PULLING, (stack, level, entity, seed) ->
                entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
    }

    private static void registerCrossbow(Item item) {
        ItemProperties.register(item, PULL, (stack, level, entity, seed) -> {
            if (entity == null || CrossbowItem.isCharged(stack)) {
                return 0.0F;
            }
            return (float) (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks())
                    / (float) RangedWeaponApi.getFullDrawTicks(entity, stack);
        });
        ItemProperties.register(item, PULLING, (stack, level, entity, seed) ->
                entity != null && entity.isUsingItem() && entity.getUseItem() == stack && !CrossbowItem.isCharged(stack)
                        ? 1.0F : 0.0F);
        ItemProperties.register(item, CHARGED, (stack, level, entity, seed) ->
                CrossbowItem.isCharged(stack) ? 1.0F : 0.0F);
        ItemProperties.register(item, FIREWORK, (stack, level, entity, seed) -> {
            ChargedProjectiles charged = stack.get(DataComponents.CHARGED_PROJECTILES);
            return charged != null && charged.contains(Items.FIREWORK_ROCKET) ? 1.0F : 0.0F;
        });
    }
}
