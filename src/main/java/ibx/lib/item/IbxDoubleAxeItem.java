package ibx.lib.item;

import ibx.lib.compat.BetterCombatCompat;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/**
 * Ibx 双刃斧：双手拿的大斧（装了 Better Combat 时副手被当成空手），横斩接转身斩。
 *
 * <p>是斧的一种，规则全同 {@link IbxAxeItem}：原版斧的物品设置（每次攻击掉 2 点耐久、把盾打掉 5 秒、斧类方块挖得快）、
 * 右键剥树皮、刮铜锈、刮蜡。只换左键动作：默认用 Better Combat 的 {@code bettercombat:double_axe}。
 * 自动进 {@code #c:tools/double_axe}，连同斧的 {@code #c:tools/axe}。</p>
 */
public class IbxDoubleAxeItem extends IbxAxeItem {
    /** 左键动作的默认预设。 */
    public static final Identifier PRESET = BetterCombatCompat.builtin("double_axe");

    /**
     * @param properties   物品属性，必须带物品 id
     * @param material     材料：耐久、修复材料、附魔能力、挖掘速度和等级，攻击伤害再加上它的加成
     * @param attackDamage 伤害基数：+ 材料加成 + 空手 1 = 显示的攻击伤害
     * @param attackSpeed  攻速基数：+ 玩家的 4 = 显示的攻速
     */
    public IbxDoubleAxeItem(Item.Properties properties, ToolMaterial material, float attackDamage, float attackSpeed) {
        super(properties, material, attackDamage, attackSpeed, PRESET);
    }
}
