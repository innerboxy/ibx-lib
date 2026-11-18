package ibx.lib.item.vanilla;

import ibx.lib.config.StartupConfig;
import ibx.lib.item.IbxBowItem;
import ibx.lib.item.IbxCrossbowItem;
import ibx.lib.item.general.IbxRangedWeaponStats;
import ibx.lib.registry.AttributeRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * 原版弓、弩、三叉戟的接管：让 {@link Items#BOW} / {@link Items#CROSSBOW} 与 Ibx 弓弩、{@link Items#TRIDENT} 与
 * {@link ibx.lib.item.IbxTridentItem} 走同一套远程系统。
 *
 * <p>只认这三个原版物品本身，<b>其他模组继承原版类的弓弩、三叉戟一律不碰</b>（它们有自己的伤害设计）。
 * 总开关见 {@link StartupConfig#TAKE_OVER_VANILLA_RANGED_WEAPONS}，关掉后本类的判断全部返回 false，
 * 所有接管点都退回原版行为。</p>
 *
 * <p>弓弩的接管分三块：</p>
 * <ul>
 *   <li>数值：{@link #onModifyDefaultComponents} 把远程伤害 / 拉弦时间修饰符烤进两个物品的默认组件，
 *       tooltip 绿字自动出来；已有存档里的物品只存组件差异，会自动吃到新默认值。</li>
 *   <li>行为：{@code mixin} 包里的 {@code BowItemMixin} / {@code CrossbowItemMixin} 接管物品行为，只问本类的
 *       {@code isManaged*}（Ibx 弓弩在自己的类里实现同样的行为）；生物 AI、生物造箭、弩装填时长这些 Ibx 与原版共用的注入点
 *       问的是 {@link ibx.lib.api.RangedWeaponApi#isManagedBow} / {@code isManagedCrossbow}。</li>
 *   <li>附魔：力量改为加远程伤害、快速装填改为减拉弦时间，在 {@code data/minecraft/enchantment} 下覆盖原版定义。</li>
 * </ul>
 *
 * <p>三叉戟的接管：</p>
 * <ul>
 *   <li>数值：原版的攻击伤害、攻速之外再挂一条远程伤害 {@value #TRIDENT_RANGED_DAMAGE}（基础 id，深绿合并行），
 *       等于原版掷出去的伤害。</li>
 *   <li>行为同 {@link ibx.lib.item.IbxTridentItem}：{@code TridentItemMixin} 在出手那一刻记下投掷者的远程伤害、按暴击率掷暴击，
 *       写在掷出去的戟上；副手不能用。{@code ThrownTridentMixin} 打中时拿记下的伤害代替原版写死的 8，过完穿刺再交给
 *       本模组弓箭的命中结算（暴击倍率、难度缩放）。溺尸掷戟由 {@code DrownedMixin} 同样记下它的远程伤害（不掷暴击，同骷髅射箭）。</li>
 *   <li>附魔照原版：穿刺加在记下的伤害上，忠诚、激流、引雷不涉及伤害，都不用改。</li>
 * </ul>
 */
public final class VanillaRangedWeapons {
    private VanillaRangedWeapons() {}

    /**
     * 原版弓：满蓄伤害 6（原版满蓄 3.0 箭速 × 2 基础伤害）、拉弓 1 秒、箭速 3.0，与原版手感一致。
     */
    public static final IbxRangedWeaponStats BOW = new IbxRangedWeaponStats(6.0F, 1.0F, 3.0F,
            IbxBowItem.BASELINE_DRAW_TIME, IbxBowItem.BASELINE_ARROW_VELOCITY);

    /**
     * 原版弩：伤害 7（原版 3.15 箭速 × 2 基础伤害向上取整）、装填 1.25 秒、箭速 3.15。
     * 生物射击同样按 3.15 发射（原版生物固定 1.6），与 Ibx 弩一致。
     */
    public static final IbxRangedWeaponStats CROSSBOW = new IbxRangedWeaponStats(7.0F, 1.25F, 3.15F,
            IbxCrossbowItem.BASELINE_DRAW_TIME, IbxCrossbowItem.BASELINE_ARROW_VELOCITY);

    /** 原版三叉戟主手的远程伤害：同原版掷出去的伤害（{@link TridentItem#BASE_DAMAGE}）。 */
    public static final float TRIDENT_RANGED_DAMAGE = 8.0F;

    /** 原版三叉戟的主手修饰符：原版的攻击伤害、攻速，再加远程伤害。 */
    public static ItemAttributeModifiers tridentModifiers() {
        return TridentItem.createAttributes().withModifierAdded(AttributeRegistry.RANGED_DAMAGE,
                new AttributeModifier(AttributeRegistry.BASE_RANGED_DAMAGE_ID, TRIDENT_RANGED_DAMAGE, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND);
    }

    /** 接管开关（启动配置，改动需重启）。 */
    public static boolean isEnabled() {
        return StartupConfig.TAKE_OVER_VANILLA_RANGED_WEAPONS.getAsBoolean();
    }

    /** 是不是被接管的原版弓。 */
    public static boolean isManagedBow(Item item) {
        return item == Items.BOW && isEnabled();
    }

    public static boolean isManagedBow(ItemStack stack) {
        return isManagedBow(stack.getItem());
    }

    /** 是不是被接管的原版弩。 */
    public static boolean isManagedCrossbow(Item item) {
        return item == Items.CROSSBOW && isEnabled();
    }

    public static boolean isManagedCrossbow(ItemStack stack) {
        return isManagedCrossbow(stack.getItem());
    }

    /** 是不是被接管的原版三叉戟。 */
    public static boolean isManagedTrident(Item item) {
        return item == Items.TRIDENT && isEnabled();
    }

    public static boolean isManagedTrident(ItemStack stack) {
        return isManagedTrident(stack.getItem());
    }

    /**
     * 把远程伤害 / 拉弦时间修饰符设为原版弓弩的默认组件（原版两者的默认修饰符都是空的，直接替换）；
     * 原版三叉戟换成 {@link #tridentModifiers()}。按物品栈覆盖的组件（KubeJS、数据包、指令给的）仍然优先。
     */
    public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        if (!isEnabled()) {
            return;
        }
        event.modify(Items.BOW,
                (components, context, item) -> components.set(DataComponents.ATTRIBUTE_MODIFIERS, BOW.modifiers()));
        event.modify(Items.CROSSBOW,
                (components, context, item) -> components.set(DataComponents.ATTRIBUTE_MODIFIERS, CROSSBOW.modifiers()));
        ItemAttributeModifiers trident = tridentModifiers();
        event.modify(Items.TRIDENT,
                (components, context, item) -> components.set(DataComponents.ATTRIBUTE_MODIFIERS, trident));
    }
}
