package ibx.lib.compat;

import net.neoforged.fml.ModList;

/**
 * InventoryHUD+ 联动（modId {@code inventoryhud}，只在客户端）。本模组对它是<b>非强制依赖</b>。
 *
 * <p>它的盔甲栏 HUD 默认贴着快捷栏两边竖着摆（左列主手、头、胸、箭数，右列副手、腿、脚、背包空格数），
 * 最下面的箭数格正好压在副手栏外侧的当前法术框上。装了就把它这一帧画的格子报给 HUD 的避让，法术框往外让开：
 * {@link ibx.lib.compat.inventoryhud.ArmorHudArea}，装了才由客户端入口注册。</p>
 */
public final class InventoryHudCompat {
    public static final String MODID = "inventoryhud";

    private InventoryHudCompat() {}

    /** InventoryHUD+ 是否已加载。 */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MODID);
    }
}
