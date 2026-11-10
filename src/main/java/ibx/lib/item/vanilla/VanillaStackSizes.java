package ibx.lib.item.vanilla;

import ibx.lib.config.StartupConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * 原版堆叠上限的调整：原版里一组只能叠 16 个的物品改成 64 个（成书 Written Book 除外）。
 *
 * <p>做法同 {@link VanillaArmors}：在 {@code ModifyDefaultComponentsEvent} 里改物品的默认组件（{@code max_stack_size}），
 * 已有存档里的物品也跟着变；按物品栈覆盖的组件仍然优先。只认下面列出的原版物品本身，别的模组的物品不碰。
 * 开关见 {@link StartupConfig#RAISE_VANILLA_STACK_SIZES}。</p>
 */
public final class VanillaStackSizes {
    private VanillaStackSizes() {}

    /** 改成的堆叠上限。 */
    public static final int STACK_SIZE = 64;

    /** 原版里堆叠上限 16、改成 64 的物品。 */
    private static final Item[] ITEMS = {
            // 告示牌 Sign
            Items.OAK_SIGN, Items.SPRUCE_SIGN, Items.BIRCH_SIGN, Items.JUNGLE_SIGN, Items.ACACIA_SIGN, Items.CHERRY_SIGN,
            Items.DARK_OAK_SIGN, Items.PALE_OAK_SIGN, Items.MANGROVE_SIGN, Items.BAMBOO_SIGN, Items.CRIMSON_SIGN, Items.WARPED_SIGN,
            // 悬挂式告示牌 Hanging Sign
            Items.OAK_HANGING_SIGN, Items.SPRUCE_HANGING_SIGN, Items.BIRCH_HANGING_SIGN, Items.JUNGLE_HANGING_SIGN,
            Items.ACACIA_HANGING_SIGN, Items.CHERRY_HANGING_SIGN, Items.DARK_OAK_HANGING_SIGN, Items.PALE_OAK_HANGING_SIGN,
            Items.MANGROVE_HANGING_SIGN, Items.BAMBOO_HANGING_SIGN, Items.CRIMSON_HANGING_SIGN, Items.WARPED_HANGING_SIGN,
            // 旗帜 Banner
            Items.WHITE_BANNER, Items.ORANGE_BANNER, Items.MAGENTA_BANNER, Items.LIGHT_BLUE_BANNER, Items.YELLOW_BANNER,
            Items.LIME_BANNER, Items.PINK_BANNER, Items.GRAY_BANNER, Items.LIGHT_GRAY_BANNER, Items.CYAN_BANNER,
            Items.PURPLE_BANNER, Items.BLUE_BANNER, Items.BROWN_BANNER, Items.GREEN_BANNER, Items.RED_BANNER, Items.BLACK_BANNER,
            // 投掷物：雪球 Snowball、鸡蛋 Egg、蓝色鸡蛋 Blue Egg、棕色鸡蛋 Brown Egg、末影珍珠 Ender Pearl
            Items.SNOWBALL, Items.EGG, Items.BLUE_EGG, Items.BROWN_EGG, Items.ENDER_PEARL,
            // 其他：铁桶 Bucket、盔甲架 Armor Stand、蜂蜜瓶 Honey Bottle
            Items.BUCKET, Items.ARMOR_STAND, Items.HONEY_BOTTLE,
    };

    public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        if (!StartupConfig.RAISE_VANILLA_STACK_SIZES.getAsBoolean()) {
            return;
        }
        for (Item item : ITEMS) {
            event.modify(item, (components, context, it) -> components.set(DataComponents.MAX_STACK_SIZE, STACK_SIZE));
        }
    }
}
