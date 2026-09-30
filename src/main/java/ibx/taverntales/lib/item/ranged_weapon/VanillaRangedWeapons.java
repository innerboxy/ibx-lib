package ibx.taverntales.lib.item.ranged_weapon;

import ibx.taverntales.lib.config.StartupConfig;
import ibx.taverntales.lib.item.TtBowItem;
import ibx.taverntales.lib.item.TtCrossbowItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * 原版弓与弩的接管：让 {@link Items#BOW} / {@link Items#CROSSBOW} 与 Tt 弓弩走同一套远程系统。
 *
 * <p>只认这两个原版物品本身，<b>其他模组继承原版类的弓弩一律不碰</b>（它们有自己的伤害设计）。
 * 总开关见 {@link StartupConfig#TAKE_OVER_VANILLA_RANGED_WEAPONS}，关掉后本类的判断全部返回 false，
 * 所有接管点都退回原版行为。</p>
 *
 * <p>接管分三块：</p>
 * <ul>
 *   <li>数值：{@link #onModifyDefaultComponents} 把远程伤害 / 拉弦时间修饰符烤进两个物品的默认组件，
 *       tooltip 绿字自动出来；已有存档里的物品只存组件差异，会自动吃到新默认值。</li>
 *   <li>行为：{@code mixin} 包里的 {@code BowItemMixin} / {@code CrossbowItemMixin} 接管物品行为，只问本类的
 *       {@code isManaged*}（Tt 弓弩在自己的类里实现同样的行为）；生物 AI、生物造箭、弩装填时长这些 Tt 与原版共用的注入点
 *       问的是 {@link ibx.taverntales.lib.api.RangedWeaponApi#isManagedBow} / {@code isManagedCrossbow}。</li>
 *   <li>附魔：力量改为加远程伤害、快速装填改为减拉弦时间，在 {@code data/minecraft/enchantment} 下覆盖原版定义。</li>
 * </ul>
 */
public final class VanillaRangedWeapons {
    private VanillaRangedWeapons() {}

    /**
     * 原版弓：满蓄伤害 6（原版满蓄 3.0 箭速 × 2 基础伤害）、拉弓 1 秒、箭速 3.0，与原版手感一致。
     */
    public static final RangedWeaponStats BOW = new RangedWeaponStats(6.0F, 1.0F, 3.0F,
            TtBowItem.BASELINE_DRAW_TIME, TtBowItem.BASELINE_ARROW_VELOCITY);

    /**
     * 原版弩：伤害 7（原版 3.15 箭速 × 2 基础伤害向上取整）、装填 1.25 秒、箭速 3.15。
     * 生物射击同样按 3.15 发射（原版生物固定 1.6），与 Tt 弩一致。
     */
    public static final RangedWeaponStats CROSSBOW = new RangedWeaponStats(7.0F, 1.25F, 3.15F,
            TtCrossbowItem.BASELINE_DRAW_TIME, TtCrossbowItem.BASELINE_ARROW_VELOCITY);

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

    /**
     * 把远程伤害 / 拉弦时间修饰符设为两个原版物品的默认组件（原版两者的默认修饰符都是空的，直接替换）。
     * 按物品栈覆盖的组件（KubeJS、数据包、指令给的）仍然优先。
     */
    public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        if (!isEnabled()) {
            return;
        }
        event.modify(Items.BOW,
                (components, context, item) -> components.set(DataComponents.ATTRIBUTE_MODIFIERS, BOW.modifiers()));
        event.modify(Items.CROSSBOW,
                (components, context, item) -> components.set(DataComponents.ATTRIBUTE_MODIFIERS, CROSSBOW.modifiers()));
    }
}
