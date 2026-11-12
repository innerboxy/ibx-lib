package ibx.lib.item.general;

import ibx.lib.compat.BetterCombatCompat;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

import java.util.function.UnaryOperator;

/**
 * Ibx 近战武器：所有近战武器的基类，只能继承。按武器种类分子类（{@link ibx.lib.item.IbxSwordItem} 剑、
 * {@link ibx.lib.item.IbxDaggerItem} 匕首、{@link ibx.lib.item.IbxCutlassItem} 砍刀、{@link ibx.lib.item.IbxClaymoreItem} 大剑、
 * {@link ibx.lib.item.IbxAxeItem} 斧、{@link ibx.lib.item.IbxSpearItem} 矛、{@link ibx.lib.item.IbxScytheItem} 镰刀、
 * {@link ibx.lib.item.IbxHammerItem} 战锤、{@link ibx.lib.item.IbxFistItem} 拳套、{@link ibx.lib.item.IbxDoubleAxeItem} 双刃斧……），普通武器直接用种类类；有自己机制的（命中附加伤害、特殊操作）再继承种类类。
 *
 * <p>种类决定两件事，都由子类在构造时交给这里：</p>
 * <ul>
 *   <li>按哪套规则设置物品（{@code setup}）：原版的 {@code Properties#sword}、{@code Properties#axe}、{@code Properties#spear}，或者自己写组件。
 *       耐久、修复材料、附魔能力、攻击伤害、攻速、挖方块、每次攻击掉多少耐久、能不能打掉盾都在这一步里。</li>
 *   <li>左键动作：Better Combat 的动作预设 id（{@code preset}），写成物品的默认组件，不用每件物品再写
 *       {@code weapon_attributes} 文件（见 {@link BetterCombatCompat#preset}）。没装 Better Combat 时是原版挥臂。
 *       给 null 就不挂预设，这件武器用哪份动作由 Better Combat 自己定（它按物品 id 里的词兜底，认不出就不接管左键）。</li>
 * </ul>
 *
 * <p>这里管所有近战共用的：</p>
 * <ul>
 *   <li>注册时用 {@code Properties#attributes} 给的被动属性会留着，排在攻击伤害、攻速后面（{@code sword()} 这类设置本来是整份替换，
 *       见 {@link IbxAttributeModifiers}）。</li>
 *   <li>自动进 {@code #c:tools}、{@code #c:tools/melee_weapon}，种类类再进各自的标签（见 {@link ibx.lib.registry.AutoItemTags}），data 里不用再写。</li>
 * </ul>
 * 手持显示（lib 的 {@code general_*_hand} 模型）按武器种类在资源里给，不在这里。
 *
 * <p>注册：用 {@code DeferredRegister.Items#registerItem}，在工厂里使用传进来的 Properties，例如
 * {@code ITEMS.registerItem("my_blade", p -> new IbxSwordItem(p, ToolMaterial.IRON, 3.0F, -2.4F))}。
 * 想换左键动作，在传进来的 Properties 上先调一次 {@link BetterCombatCompat#preset}。</p>
 */
public abstract class IbxMeleeWeaponItem extends Item {
    /**
     * @param properties 物品属性，必须带物品 id（见类说明的注册方式）
     * @param setup      这一种武器的规则设置，比如 {@code p -> p.sword(material, attackDamage, attackSpeed)}
     * @param preset     Better Combat 的动作预设 id，比如 {@code BetterCombatCompat.builtin("dagger")}；null 是不挂预设
     */
    protected IbxMeleeWeaponItem(Item.Properties properties, UnaryOperator<Item.Properties> setup, @Nullable Identifier preset) {
        super(setUp(properties, setup, preset));
    }

    private static Item.Properties setUp(Item.Properties properties, UnaryOperator<Item.Properties> setup, @Nullable Identifier preset) {
        Item.Properties result = IbxAttributeModifiers.keeping(properties, setup);
        return preset != null ? BetterCombatCompat.preset(result, preset) : result;
    }
}
