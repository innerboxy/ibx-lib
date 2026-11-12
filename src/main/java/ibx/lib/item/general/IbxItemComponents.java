package ibx.lib.item.general;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantable;

/**
 * 往 {@link Item.Properties} 的组件初始化步骤里接一步，这一步能读到前面已经写进去的组件。
 *
 * <p>物品的默认组件要到注册表加载时才真正写；{@link Item.Properties} 对外只有「写一个组件」的入口，构造物品时读不到已经写了什么。
 * 要「看情况再写」（和已有的合并、没写过才写、查别的模组的组件类型在不在）就用这里。</p>
 */
public final class IbxItemComponents {
    private IbxItemComponents() {}

    /** 组件初始化时跑的一步。 */
    @FunctionalInterface
    public interface Step {
        /**
         * @param components 这件物品到这一步为止的默认组件，可读可写
         * @param context    注册表，解析标签、别的注册项用
         * @param item       这件物品的注册键
         */
        void run(DataComponentMap.Builder components, HolderLookup.Provider context, ResourceKey<?> item);
    }

    /** 注册方没设过附魔能力（{@code Properties#enchantable}）才设成 {@code value}，设过的不动。 */
    public static Item.Properties enchantableIfUnset(Item.Properties properties, int value) {
        return then(properties, (components, context, item) -> {
            if (components.get(DataComponents.ENCHANTABLE) == null) {
                components.set(DataComponents.ENCHANTABLE, new Enchantable(value));
            }
        });
    }

    /** 在已有的初始化步骤后面接一步。 */
    public static Item.Properties then(Item.Properties properties, Step step) {
        // 借 delayedComponent：它拿 SingleComponentInitializer#asInitializer 的返回值当步骤，重写它就能接上任意一步；
        // 组件类型只是占位，这一步自己不写它
        return properties.delayedComponent(DataComponents.UNBREAKABLE, new DataComponentInitializers.SingleComponentInitializer<>() {
            @Override
            public Unit create(HolderLookup.Provider context) {
                throw new IllegalStateException("IbxItemComponents step was run as a plain component initializer");
            }

            @Override
            public <T> DataComponentInitializers.Initializer<T> asInitializer(DataComponentType<Unit> type) {
                return (components, context, key) -> step.run(components, context, key);
            }
        });
    }
}
