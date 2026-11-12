package ibx.lib.item;

import ibx.lib.compat.BetterCombatCompat;
import ibx.lib.item.general.IbxMeleeWeaponItem;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/**
 * Ibx 矛：规则同原版的矛（{@code Properties#spear}）。
 *
 * <ul>
 *   <li>左键直刺：一条线上能扎到多个目标，攻击距离比剑长；攻击条转满才能打。</li>
 *   <li>右键按住冲刺突刺：伤害看持矛者和目标的相对速度，够快还能击退、把骑着东西的目标撞下来；按得越久这三样依次失效。</li>
 *   <li>材料给耐久、修复材料、附魔能力；每次攻击掉 1 点耐久、打不掉盾。攻击伤害只有材料加成、没有伤害基数，
 *       攻速由「刺一下多久」算出来（都是主手、基础 id，tooltip 是深绿合并行）。木头材料用木矛的音效。</li>
 *   <li>左键动作默认用 Better Combat 的 {@code bettercombat:spear}（双手、直刺）。它给原版的矛配的也是这份（按物品 id 里有没有
 *       {@code spear} 兜底），这里固定挂上，不看物品叫什么；装了它时左键由它接管，右键的冲刺突刺照旧。</li>
 *   <li>自动进 {@code #c:tools/spear}。原版的 {@code #minecraft:spears}（冲刺附魔认它）不自动进。</li>
 * </ul>
 * 其余见 {@link IbxMeleeWeaponItem}。
 */
public class IbxSpearItem extends IbxMeleeWeaponItem {
    /** 左键动作的默认预设。 */
    public static final Identifier PRESET = BetterCombatCompat.builtin("spear");

    /** 下面九个是原版铁矛的数值，只给材料的构造方法用它们。 */
    public static final float BASELINE_ATTACK_DURATION = 0.95F;
    public static final float BASELINE_DAMAGE_MULTIPLIER = 0.95F;
    public static final float BASELINE_DELAY = 0.6F;
    public static final float BASELINE_DISMOUNT_TIME = 2.5F;
    public static final float BASELINE_DISMOUNT_THRESHOLD = 11.0F;
    public static final float BASELINE_KNOCKBACK_TIME = 6.75F;
    public static final float BASELINE_KNOCKBACK_THRESHOLD = 5.1F;
    public static final float BASELINE_DAMAGE_TIME = 11.25F;
    public static final float BASELINE_DAMAGE_THRESHOLD = 4.6F;

    /** 九个数都用原版铁矛的。 */
    public IbxSpearItem(Item.Properties properties, ToolMaterial material) {
        this(properties, material, BASELINE_ATTACK_DURATION, BASELINE_DAMAGE_MULTIPLIER, BASELINE_DELAY,
                BASELINE_DISMOUNT_TIME, BASELINE_DISMOUNT_THRESHOLD, BASELINE_KNOCKBACK_TIME, BASELINE_KNOCKBACK_THRESHOLD,
                BASELINE_DAMAGE_TIME, BASELINE_DAMAGE_THRESHOLD);
    }

    /**
     * 参数顺序同原版的 {@code Properties#spear}，时间都是秒。
     *
     * @param properties         物品属性，必须带物品 id
     * @param material           材料：耐久、修复材料、附魔能力，攻击伤害 = 它的加成 + 空手 1
     * @param attackDuration     左键刺一下多久：攻速 = 1 ÷ 本值，刺的动作也放这么久
     * @param damageMultiplier   冲刺突刺的伤害倍率：伤害 = 持矛者攻击伤害的基础值（玩家是 1）+ 相对速度 × 本值（向下取整）
     * @param delay              右键按住多久之后冲刺突刺才开始生效
     * @param dismountTime       生效后这么久以内能把骑着东西的目标撞下来
     * @param dismountThreshold  撞下来要求持矛者的速度不低于它
     * @param knockbackTime      生效后这么久以内带击退
     * @param knockbackThreshold 击退要求持矛者的速度不低于它
     * @param damageTime         生效后这么久以内有伤害，过了这次冲刺就结束
     * @param damageThreshold    伤害要求持矛者和目标的相对速度不低于它
     */
    public IbxSpearItem(Item.Properties properties, ToolMaterial material, float attackDuration, float damageMultiplier, float delay,
                        float dismountTime, float dismountThreshold, float knockbackTime, float knockbackThreshold,
                        float damageTime, float damageThreshold) {
        super(properties, p -> p.spear(material, attackDuration, damageMultiplier, delay, dismountTime, dismountThreshold,
                knockbackTime, knockbackThreshold, damageTime, damageThreshold), PRESET);
    }
}
