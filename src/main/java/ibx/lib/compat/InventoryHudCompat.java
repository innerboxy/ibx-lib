package ibx.lib.compat;

import ibx.lib.client.hud.HudAvoidance;
import ibx.lib.compat.inventoryhud.ArmorHudArea;
import net.neoforged.fml.ModList;

/**
 * InventoryHUD+（modId {@code inventoryhud}，只在客户端）联动：它的盔甲栏 HUD 默认贴着快捷栏两边竖着摆
 * （左列主手、头、胸、箭数，右列副手、腿、脚、背包空格数），最下面的箭数格正好压在副手栏外侧的当前法术框上。
 * 装了就把它这一帧画的格子报给 {@link HudAvoidance}，法术框往外让开。
 *
 * <p>软依赖——它的类只在 {@link ArmorHudArea} 里引用，没装时不会加载。</p>
 */
public final class InventoryHudCompat {
    private InventoryHudCompat() {}

    public static final String MOD_ID = "inventoryhud";

    /** 在客户端模组入口里调一次。 */
    public static void register() {
        if (ModList.get().isLoaded(MOD_ID)) {
            HudAvoidance.register(ArmorHudArea::collect);
        }
    }
}
