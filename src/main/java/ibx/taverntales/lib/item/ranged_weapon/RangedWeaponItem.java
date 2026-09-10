package ibx.taverntales.lib.item.ranged_weapon;

import net.minecraft.world.item.Item;

/**
 * 本模组远程武器的公共接口。弓、弩各自继承对应的原版类（Java 单继承），
 * 共享的部分走本接口 + {@link ibx.taverntales.lib.api.RangedWeaponApi}。
 *
 * <p>注意与原版的 {@link net.minecraft.world.item.ProjectileWeaponItem} 区分：那是原版远程武器的
 * <b>抽象基类</b>（弓弩都继承它）；本接口是本模组自己的<b>标记 + 数值入口</b>，
 * 名字里的 Item 只表示"实现者是物品"。原版与 NeoForge 都没有叫 RangedWeaponItem 的类，不会撞名。</p>
 */
public interface RangedWeaponItem {
    /** 构造时给定的静态数值（远程伤害、拉弓秒数、烤好的属性修饰符）。 */
    RangedWeaponStats stats();

    /**
     * 给"只传耐久"的便捷构造器用的物品属性。
     *
     * @param durability 最大耐久；大于 0 才设耐久组件（并自动变为不可堆叠），
     *                   不大于 0 则不加，得到一把不会损坏的武器。
     */
    static Item.Properties properties(int durability) {
        Item.Properties properties = new Item.Properties();
        return durability > 0 ? properties.durability(durability) : properties;
    }
}
