package ibx.lib.item.general;

import ibx.lib.registry.AttributeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 盔甲材质：原版 {@link ArmorMaterial} 的那几项，加上每个部位的法术抗性（{@link AttributeRegistry#SPELL_RESISTANCE}）。
 *
 * <pre>{@code
 * public static final IbxArmorMaterial MATERIAL = new IbxArmorMaterial(37,
 *         IbxArmorMaterial.pieces(3, 8, 6, 3),   // 护甲：头、胸、腿、脚
 *         IbxArmorMaterial.pieces(3, 8, 6, 3),   // 法术抗性：头、胸、腿、脚
 *         15, SoundEvents.ARMOR_EQUIP_NETHERITE, 3.0F, 0.1F, ItemTags.REPAIRS_NETHERITE_ARMOR, ASSET_ID);
 * }</pre>
 *
 * <p>原版材质是 record、继承不了，所以这是另起的一个类。原版只在注册物品时读一次材质，把数值拆进物品的默认组件，
 * 之后不再用它；这个类同样只在注册时用：配 {@link IbxArmorProperties#of(Item.Properties, IbxArmorMaterial, ArmorType)}
 * 注册人形盔甲，别处要原版材质时用 {@link #toVanilla()}。</p>
 *
 * @param durability          耐久倍率：每件耐久 = 部位基数（头 11、胸 16、腿 15、脚 13）× 本值
 * @param defense             各部位的护甲值
 * @param spellResistance     各部位的法术抗性；没填或填 0 的部位不带这条属性
 * @param enchantmentValue    附魔能力
 * @param equipSound          穿戴音效
 * @param toughness           盔甲韧性（每件）
 * @param knockbackResistance 击退抗性（每件）；0 就不带这条属性
 * @param repairIngredient    修复材料
 * @param assetId             装备资源 id
 */
public record IbxArmorMaterial(
        int durability,
        Map<ArmorType, Integer> defense,
        Map<ArmorType, Integer> spellResistance,
        int enchantmentValue,
        Holder<SoundEvent> equipSound,
        float toughness,
        float knockbackResistance,
        TagKey<Item> repairIngredient,
        ResourceKey<EquipmentAsset> assetId
) {
    public IbxArmorMaterial {
        defense = Map.copyOf(defense);
        spellResistance = Map.copyOf(spellResistance);
    }

    /** 照一个原版材质，再给每个部位配上法术抗性。 */
    public static IbxArmorMaterial of(ArmorMaterial material, Map<ArmorType, Integer> spellResistance) {
        return new IbxArmorMaterial(material.durability(), material.defense(), spellResistance, material.enchantmentValue(),
                material.equipSound(), material.toughness(), material.knockbackResistance(), material.repairIngredient(),
                material.assetId());
    }

    /** 四个部位各一个数值，填护甲、法术抗性都用它。 */
    public static Map<ArmorType, Integer> pieces(int helmet, int chestplate, int leggings, int boots) {
        Map<ArmorType, Integer> map = new EnumMap<>(ArmorType.class);
        map.put(ArmorType.HELMET, helmet);
        map.put(ArmorType.CHESTPLATE, chestplate);
        map.put(ArmorType.LEGGINGS, leggings);
        map.put(ArmorType.BOOTS, boots);
        return map;
    }

    /** 去掉法术抗性的原版材质。 */
    public ArmorMaterial toVanilla() {
        return new ArmorMaterial(this.durability, this.defense, this.enchantmentValue, this.equipSound, this.toughness,
                this.knockbackResistance, this.repairIngredient, this.assetId);
    }

    /**
     * 这个部位的属性修饰符：原版材质给的护甲、韧性、击退抗性，法术抗性插在护甲后面（tooltip 也按这个顺序显示）。
     * 修饰符 id 同原版护甲的 {@code minecraft:armor.<部位>}。
     */
    public ItemAttributeModifiers createAttributes(ArmorType type) {
        List<ItemAttributeModifiers.Entry> entries = new ArrayList<>(this.toVanilla().createAttributes(type).modifiers());
        int resistance = this.spellResistance.getOrDefault(type, 0);
        if (resistance != 0) {
            int armor = 0;
            while (armor < entries.size() && !entries.get(armor).attribute().unwrapKey().equals(Attributes.ARMOR.unwrapKey())) {
                armor++;
            }
            AttributeModifier modifier = new AttributeModifier(Identifier.withDefaultNamespace("armor." + type.getName()),
                    resistance, AttributeModifier.Operation.ADD_VALUE);
            entries.add(armor < entries.size() ? armor + 1 : 0, new ItemAttributeModifiers.Entry(
                    AttributeRegistry.SPELL_RESISTANCE, modifier, EquipmentSlotGroup.bySlot(type.getSlot())));
        }
        return new ItemAttributeModifiers(List.copyOf(entries));
    }
}
