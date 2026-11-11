package ibx.lib.item.general;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;

import java.util.ArrayList;
import java.util.List;

/**
 * 一件人形盔甲的物品属性：材质自带护甲 / 法术抗性 / 韧性 / 击退抗性，在这之上逐条加这件自己的属性。
 *
 * <pre>{@code
 * p -> new Item(IbxArmorProperties.of(p, MATERIAL, ArmorType.HELMET)
 *         .add(Attributes.MAX_HEALTH, 2)
 *         .fireResistant()
 *         .build())
 * }</pre>
 *
 * 材质用带法术抗性的 {@link IbxArmorMaterial}，也收原版的 {@link ArmorMaterial}（没有法术抗性）。
 * 只要属性修饰符（比如改已有物品的默认组件）时用 {@link #modifiers}。
 *
 * <p>加的修饰符 id 都同原版护甲的 {@code minecraft:armor.<部位>}（一个部位只穿得下一件，不会撞），
 * 所以一件上同一种属性只能有一条：材质已经给的护甲、法术抗性、韧性、击退抗性不能再加，要换先 {@link Modifiers#remove}。</p>
 */
public final class IbxArmorProperties {
    private final Item.Properties properties;
    private final Modifiers modifiers;

    private IbxArmorProperties(Item.Properties properties, ArmorMaterial material, ArmorType type, Modifiers modifiers) {
        this.properties = properties.humanoidArmor(material, type);
        this.modifiers = modifiers;
    }

    /**
     * @param properties 注册时传进来的物品属性（带着物品 id）
     * @param material   材质：耐久、护甲、法术抗性、韧性、击退抗性、附魔能力、穿戴音效、修复材料、装备资源 id
     * @param type       部位
     */
    public static IbxArmorProperties of(Item.Properties properties, IbxArmorMaterial material, ArmorType type) {
        return new IbxArmorProperties(properties, material.toVanilla(), type, modifiers(material, type));
    }

    /**
     * @param properties 注册时传进来的物品属性（带着物品 id）
     * @param material   原版材质：耐久、护甲、韧性、击退抗性、附魔能力、穿戴音效、修复材料、装备资源 id
     * @param type       部位
     */
    public static IbxArmorProperties of(Item.Properties properties, ArmorMaterial material, ArmorType type) {
        return new IbxArmorProperties(properties, material, type, modifiers(material, type));
    }

    /** 只要这件盔甲的属性修饰符：从材质自带的那几条开始（法术抗性排在护甲后面）。 */
    public static Modifiers modifiers(IbxArmorMaterial material, ArmorType type) {
        return new Modifiers(type, material.createAttributes(type));
    }

    /** 只要这件盔甲的属性修饰符：从原版材质自带的那几条开始。 */
    public static Modifiers modifiers(ArmorMaterial material, ArmorType type) {
        return new Modifiers(type, material.createAttributes(type));
    }

    /** 加一条属性（直接加数值）。 */
    public IbxArmorProperties add(Holder<Attribute> attribute, double amount) {
        this.modifiers.add(attribute, amount);
        return this;
    }

    /** 加一条属性，自己指定运算方式（按百分比加的用 {@code ADD_MULTIPLIED_BASE}）。 */
    public IbxArmorProperties add(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
        this.modifiers.add(attribute, amount, operation);
        return this;
    }

    /** 防火：掉进岩浆、火里不烧。 */
    public IbxArmorProperties fireResistant() {
        this.properties.fireResistant();
        return this;
    }

    public Item.Properties build() {
        return this.properties.attributes(this.modifiers.build());
    }

    /** 一件盔甲的属性修饰符。 */
    public static final class Modifiers {
        private final Identifier id;
        private final EquipmentSlotGroup slot;
        private final List<ItemAttributeModifiers.Entry> entries;

        private Modifiers(ArmorType type, ItemAttributeModifiers base) {
            this.id = Identifier.withDefaultNamespace("armor." + type.getName());
            this.slot = EquipmentSlotGroup.bySlot(type.getSlot());
            this.entries = new ArrayList<>(base.modifiers());
        }

        /** 加一条属性（直接加数值）。 */
        public Modifiers add(Holder<Attribute> attribute, double amount) {
            return this.add(attribute, amount, AttributeModifier.Operation.ADD_VALUE);
        }

        /** 加一条属性，自己指定运算方式。这件上已经有这种属性时抛异常。 */
        public Modifiers add(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
            if (this.entries.stream().anyMatch(entry -> same(entry.attribute(), attribute))) {
                throw new IllegalArgumentException("Armor piece " + this.id + " already has a modifier for " + attribute.getRegisteredName());
            }
            this.entries.add(new ItemAttributeModifiers.Entry(attribute, new AttributeModifier(this.id, amount, operation), this.slot));
            return this;
        }

        /** 去掉这种属性的修饰符（比如不要材质给的护甲），没有就什么都不做。 */
        public Modifiers remove(Holder<Attribute> attribute) {
            this.entries.removeIf(entry -> same(entry.attribute(), attribute));
            return this;
        }

        public ItemAttributeModifiers build() {
            return new ItemAttributeModifiers(List.copyOf(this.entries));
        }

        /** 是不是同一种属性：按注册键比，引用型的 Holder 和 DeferredHolder 指向同一个属性也算。 */
        private static boolean same(Holder<Attribute> a, Holder<Attribute> b) {
            return a.unwrapKey().equals(b.unwrapKey());
        }
    }
}
