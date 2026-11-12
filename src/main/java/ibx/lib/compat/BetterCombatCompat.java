package ibx.lib.compat;

import ibx.lib.item.general.IbxItemComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;

/**
 * Better Combat 联动（modId {@code bettercombat}）。本模组对它是<b>非强制依赖</b>，也不引用它的类。
 *
 * <p>它按 {@code data/<命名空间>/weapon_attributes/<名字>.json} 认武器的左键动作（挥砍方式、判定、单双手）。一件物品用哪份，
 * 先看物品上的 {@code bettercombat:preset_id} 组件（值是那份文件的 id），没有再按物品自己的 id 找。
 * 这里把预设 id 写成物品的默认组件，这样 Ibx 的近战武器按种类自带动作，不用每件物品再写一个只有 {@code parent} 的文件。</p>
 */
public final class BetterCombatCompat {
    public static final String MODID = "bettercombat";

    /** 它的组件类型 id；值是 {@link Identifier}。 */
    private static final Identifier PRESET_ID = Identifier.fromNamespaceAndPath(MODID, "preset_id");

    private BetterCombatCompat() {}

    /** Better Combat 是否已加载。 */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MODID);
    }

    /** 它自带的预设（{@code bettercombat:<名字>}），比如 {@code sword}、{@code dagger}、{@code claymore}、{@code axe}。 */
    public static Identifier builtin(String name) {
        return Identifier.fromNamespaceAndPath(MODID, name);
    }

    /**
     * 让物品默认用这份预设。没装 Better Combat 时什么都不做；物品已经指定过（先调的那次）就不改，
     * 所以注册时自己调一次就能换掉基类给的默认预设。
     *
     * <p>指定了预设之后，按物品 id 找的那份文件不再生效；要用自己写的那份，把它的 id 传进来
     * （文件 {@code data/examplemod/weapon_attributes/my_blade.json} 的 id 是 {@code examplemod:my_blade}）。</p>
     */
    public static Item.Properties preset(Item.Properties properties, Identifier preset) {
        return IbxItemComponents.then(properties, (components, context, item) -> {
            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(PRESET_ID);
            if (type != null && components.get(type) == null) {
                set(components, type, preset);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void set(DataComponentMap.Builder components, DataComponentType<?> type, Identifier preset) {
        components.set((DataComponentType<Identifier>) type, preset);
    }
}
