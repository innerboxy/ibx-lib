package ibx.lib.item;

import ibx.lib.compat.BetterCombatCompat;
import ibx.lib.item.general.IbxMeleeWeaponItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Weapon;

/**
 * Ibx 战锤：双手拿的重锤，只有一段下砸（装了 Better Combat 时副手被当成空手）。
 *
 * <ul>
 *   <li>规则在原版剑（{@code Properties#sword}）上改一处：打中举盾的人把盾打掉 {@link #DISABLE_BLOCKING_SECONDS} 秒（同原版斧）。
 *       其余同剑：材料给耐久、修复材料、附魔能力；砍蜘蛛网快、每次攻击掉 1 点耐久；
 *       主手挂攻击伤害、攻速（基础 id，tooltip 是深绿合并行）。</li>
 *   <li>左键动作默认用 Better Combat 的 {@code bettercombat:hammer}。</li>
 *   <li>自动进 {@code #c:tools/hammer}。</li>
 * </ul>
 * 其余见 {@link IbxMeleeWeaponItem}。
 */
public class IbxHammerItem extends IbxMeleeWeaponItem {
    /** 左键动作的默认预设。 */
    public static final Identifier PRESET = BetterCombatCompat.builtin("hammer");
    /** 把盾打掉几秒（原版斧 5 秒）。 */
    public static final float DISABLE_BLOCKING_SECONDS = 5.0F;

    /**
     * @param properties   物品属性，必须带物品 id
     * @param material     材料：耐久、修复材料、附魔能力，攻击伤害再加上它的加成
     * @param attackDamage 伤害基数：+ 材料加成 + 空手 1 = 显示的攻击伤害
     * @param attackSpeed  攻速基数：+ 玩家的 4 = 显示的攻速
     */
    public IbxHammerItem(Item.Properties properties, ToolMaterial material, float attackDamage, float attackSpeed) {
        super(properties, p -> p.sword(material, attackDamage, attackSpeed)
                .component(DataComponents.WEAPON, new Weapon(1, DISABLE_BLOCKING_SECONDS)), PRESET);
    }
}
