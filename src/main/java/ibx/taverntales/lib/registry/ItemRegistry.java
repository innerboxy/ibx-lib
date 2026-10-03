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
 * <p>要在本模组里加测试物品时，用 {@code ITEMS.registerItem(名字, 工厂)}：26.1 起 {@code Item.Properties}
 * 必须先带上物品 id 才能构造物品，registerItem 会代为设置，工厂拿到的就是设好 id 的 Properties，例如
 * {@code ITEMS.registerItem("my_bow", p -> new TtBowItem(p.durability(384), 8.0F, 1.0F))}。
 * 弓弩<b>不需要在这里做任何客户端登记</b>，但记得配套写物品模型定义
 * （{@code assets/<命名空间>/items/<物品名>.json}），拉弓进度的写法见
 * {@link ibx.taverntales.lib.client.RangedWeaponModelProperties}。</p>
 */
public final class ItemRegistry {
    private ItemRegistry() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TavernTalesLib.MODID);

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
