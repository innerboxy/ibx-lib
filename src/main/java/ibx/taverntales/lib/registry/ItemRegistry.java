package ibx.taverntales.lib.registry;

import ibx.taverntales.lib.TavernTalesLib;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组注册的物品。
 *
 * <p>目前为空：本模组是库，只提供可复用的武器类
 * （{@link ibx.taverntales.lib.item.TtBowItem}、{@link ibx.taverntales.lib.item.TtCrossbowItem}），
 * 具体武器由使用方自行注册。之前那两把测试弓弩已按需求删除。</p>
 *
 * <p>要在本模组里加物品时，在这里 {@code ITEMS.register(...)} 即可；
 * 弓弩的拉弓动画谓词由 {@link ibx.taverntales.lib.client.RangedWeaponModelPredicates}
 * 遍历物品注册表自动挂载，<b>不需要在这里做任何客户端登记</b>，但记得配套写模型 JSON。</p>
 */
public final class ItemRegistry {
    private ItemRegistry() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TavernTalesLib.MODID);

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
