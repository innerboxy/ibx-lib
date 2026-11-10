package ibx.lib.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读创造模式背包当前选中的创造栏 {@code selectedTab}（private static）、改物品格子的滚动位置 {@code scrollOffs}（private）。
 *
 * <p>创造栏子栏换掉物品格子里的物品后，滚动条要回到顶上，见 {@link ibx.lib.client.creativetab.CreativeSubTabStrip}。</p>
 */
@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeModeInventoryScreenAccessor {
    @Accessor("selectedTab")
    static CreativeModeTab ibx_lib$getSelectedTab() {
        throw new AssertionError();
    }

    @Accessor("scrollOffs")
    void ibx_lib$setScrollOffs(float scrollOffs);
}
