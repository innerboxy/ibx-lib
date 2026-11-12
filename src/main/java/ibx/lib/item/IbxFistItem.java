package ibx.lib.item;

import ibx.lib.compat.BetterCombatCompat;
import ibx.lib.item.general.IbxMeleeWeaponItem;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/**
 * Ibx 拳套：戴在手上出拳的单手武器，攻击距离比剑短。规则同原版剑（{@code Properties#sword}）：材料给耐久、修复材料、附魔能力；砍蜘蛛网快、每次攻击掉 1 点耐久、打不掉盾；
 * 主手挂攻击伤害、攻速（基础 id，tooltip 是深绿合并行）。左键动作默认用 Better Combat 的 {@code bettercombat:fist}。
 * 自动进 {@code #c:tools/fist}。其余见 {@link IbxMeleeWeaponItem}。
 */
public class IbxFistItem extends IbxMeleeWeaponItem {
    /** 左键动作的默认预设。 */
    public static final Identifier PRESET = BetterCombatCompat.builtin("fist");

    /**
     * @param properties   物品属性，必须带物品 id
     * @param material     材料：耐久、修复材料、附魔能力，攻击伤害再加上它的加成
     * @param attackDamage 伤害基数：+ 材料加成 + 空手 1 = 显示的攻击伤害
     * @param attackSpeed  攻速基数：+ 玩家的 4 = 显示的攻速
     */
    public IbxFistItem(Item.Properties properties, ToolMaterial material, float attackDamage, float attackSpeed) {
        super(properties, p -> p.sword(material, attackDamage, attackSpeed), PRESET);
    }
}
