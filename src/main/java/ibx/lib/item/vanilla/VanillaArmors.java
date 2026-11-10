package ibx.lib.item.vanilla;

import ibx.lib.config.StartupConfig;
import ibx.lib.item.general.IbxArmorProperties;
import ibx.lib.registry.AttributeRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * 原版盔甲套装的属性调整：给几套原版盔甲加上 / 换成法术抗性（{@link AttributeRegistry#SPELL_RESISTANCE}）。
 *
 * <ul>
 *   <li>下界合金：护甲、韧性、击退抗性不变，再加等同于各件护甲值的法术抗性（头 3、胸 8、腿 6、脚 3）。</li>
 *   <li>金：护甲换成钻石套装护甲值的法术抗性（头 3、胸 8、腿 6、脚 3），自己不再有护甲。</li>
 *   <li>铜：护甲换成等量的法术抗性（头 2、胸 4、腿 3、脚 1），自己不再有护甲。</li>
 * </ul>
 *
 * <p>做法同 {@link ibx.lib.item.vanilla.VanillaRangedWeapons}：在 {@code ModifyDefaultComponentsEvent} 里把修饰符烤进
 * 物品的默认组件，tooltip 跟着变；已有存档里的物品只存组件差异，也会吃到新默认值；按物品栈覆盖的组件仍然优先。
 * 数值直接取原版材质（{@link ArmorMaterials}）的护甲值。开关见 {@link StartupConfig#ADJUST_VANILLA_ARMORS}。</p>
 */
public final class VanillaArmors {
    private VanillaArmors() {}

    /** 一套盔甲的四件，下面传物品时按这个顺序。 */
    private static final ArmorType[] PIECES = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};

    public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        if (!StartupConfig.ADJUST_VANILLA_ARMORS.getAsBoolean()) {
            return;
        }
        adjust(event, ArmorMaterials.NETHERITE, true, ArmorMaterials.NETHERITE,
                Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);
        adjust(event, ArmorMaterials.GOLD, false, ArmorMaterials.DIAMOND,
                Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);
        adjust(event, ArmorMaterials.COPPER, false, ArmorMaterials.COPPER,
                Items.COPPER_HELMET, Items.COPPER_CHESTPLATE, Items.COPPER_LEGGINGS, Items.COPPER_BOOTS);
    }

    /**
     * @param material     这套盔甲自己的材质（护甲、韧性、击退抗性从它来）
     * @param keepArmor    保不保留护甲值
     * @param resistanceOf 法术抗性取哪套材质的护甲值
     * @param items        头、胸、腿、脚
     */
    private static void adjust(ModifyDefaultComponentsEvent event, ArmorMaterial material, boolean keepArmor,
                               ArmorMaterial resistanceOf, Item... items) {
        for (int i = 0; i < PIECES.length; i++) {
            ItemAttributeModifiers modifiers = modifiers(material, keepArmor, resistanceOf, PIECES[i]);
            event.modify(items[i], (components, context, item) -> components.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers));
        }
    }

    private static ItemAttributeModifiers modifiers(ArmorMaterial material, boolean keepArmor, ArmorMaterial resistanceOf, ArmorType type) {
        IbxArmorProperties.Modifiers modifiers = IbxArmorProperties.modifiers(material, type);
        if (!keepArmor) {
            modifiers.remove(Attributes.ARMOR);
        }
        return modifiers.add(AttributeRegistry.SPELL_RESISTANCE, resistanceOf.defense().getOrDefault(type, 0)).build();
    }
}
