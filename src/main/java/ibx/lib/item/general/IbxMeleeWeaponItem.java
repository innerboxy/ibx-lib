package ibx.lib.item.general;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/**
 * Ibx 近战武器：所有近战武器的基类，按原版剑注册（{@code Properties#sword}），伤害、攻速由构造参数给定。
 * 普通武器直接用它；有自己机制的（命中附加伤害、特殊操作）继承它，写在子类里。
 *
 * <ul>
 *   <li>同原版剑：材料给耐久、修复材料、附魔能力；砍蜘蛛网快、每次攻击掉 1 点耐久；主手挂攻击伤害、攻速（基础 id，tooltip 是深绿合并行）。</li>
 *   <li>左键动作（Better Combat 的 {@code weapon_attributes}）和手持显示（lib 的 {@code general_*_hand} 模型）按武器种类在资源里给，不在这里。</li>
 *   <li>自动进 {@code #c:tools}、{@code #c:tools/melee_weapon}（见 {@link ibx.lib.registry.AutoItemTags}），data 里不用再写。</li>
 * </ul>
 *
 * <p>注册：同 {@link IbxMagicWeaponItem}，用 {@code DeferredRegister.Items#registerItem}，在工厂里使用传进来的 Properties，例如
 * {@code ITEMS.registerItem("my_blade", p -> new IbxMeleeWeaponItem(p, ToolMaterial.IRON, 3.0F, -2.4F))}。</p>
 */
public class IbxMeleeWeaponItem extends Item {
    /**
     * @param properties   物品属性，必须带物品 id（见类说明的注册方式）
     * @param material     材料：耐久、修复材料、附魔能力，攻击伤害再加上它的加成
     * @param attackDamage 伤害基数：+ 材料加成 + 空手 1 = 显示的攻击伤害
     * @param attackSpeed  攻速基数：+ 玩家的 4 = 显示的攻速
     */
    public IbxMeleeWeaponItem(Item.Properties properties, ToolMaterial material, float attackDamage, float attackSpeed) {
        super(properties.sword(material, attackDamage, attackSpeed));
    }
}
