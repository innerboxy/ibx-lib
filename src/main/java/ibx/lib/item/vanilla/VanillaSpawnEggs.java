package ibx.lib.item.vanilla;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * 原版注册了却没放进创造栏的刷怪蛋：凋灵刷怪蛋 Wither Spawn Egg、末影龙刷怪蛋 Ender Dragon Spawn Egg。
 * 按这个顺序接在原版刷怪蛋栏最后一个原版刷怪蛋（潜影贝）后面，别的模组往这一栏追加的刷怪蛋排在它们之后。
 */
public final class VanillaSpawnEggs {
    private VanillaSpawnEggs() {}

    private static final Item[] EGGS = {Items.WITHER_SPAWN_EGG, Items.ENDER_DRAGON_SPAWN_EGG};

    public static void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.SPAWN_EGGS) {
            return;
        }
        ItemStack anchor = new ItemStack(Items.SHULKER_SPAWN_EGG);
        for (Item egg : EGGS) {
            ItemStack stack = new ItemStack(egg);
            // 已经有别的模组放进来了就不再放，重复放会抛异常。
            if (!event.getParentEntries().contains(stack)) {
                if (event.getParentEntries().contains(anchor)) {
                    event.insertAfter(anchor, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                } else {
                    event.accept(stack);
                }
            }
            anchor = stack;
        }
    }
}
