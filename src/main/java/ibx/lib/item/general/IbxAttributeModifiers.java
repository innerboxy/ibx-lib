package ibx.lib.item.general;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

/**
 * 物品基类往 {@code attribute_modifiers} 组件里写自己的属性修饰符时用：和前面已经写的合并，不互相顶掉。
 *
 * <p>物品的属性只认一处：{@code attribute_modifiers} 组件不为空时 NeoForge 就不再问 {@code getDefaultAttributeModifiers}，
 * 而 {@link Item.Properties#attributes}、原版的 {@code sword()} / {@code humanoidArmor()} 都是整份替换这个组件。
 * 所以 Ibx 的物品基类一律把自己的修饰符写进组件，并且都从这里写：注册时用 {@code Properties#attributes} 给的被动属性、
 * 子类先加的修饰符都留着，排在后写的那份后面（说明框里基类的几行在前）。</p>
 *
 * <pre>{@code
 * // 注册：弓自带远程伤害、拉弓时间，再加 2 点法术强度
 * ITEMS.registerItem("frost_bow", p -> new IbxBowItem(p.durability(384).attributes(ItemAttributeModifiers.builder()
 *         .add(AttributeRegistry.SPELL_POWER, new AttributeModifier(id("frost_bow"), 2.0, AttributeModifier.Operation.ADD_VALUE),
 *                 EquipmentSlotGroup.MAINHAND)
 *         .build()), 8.0F, 1.0F));
 *
 * // 自己写物品基类：在把 properties 交给父类之前加
 * super(IbxAttributeModifiers.add(properties, myModifiers));
 * }</pre>
 *
 * 同一个属性、同一个修饰符 id、同一个槽位写了两次（比如给剑再写一条 {@code base_attack_damage}）在组件初始化时抛异常：
 * 这样的两条装备时只有一条生效。
 */
public final class IbxAttributeModifiers {
    private IbxAttributeModifiers() {}

    /** 往物品属性里加一份修饰符，前面已经写的接在它后面。 */
    public static Item.Properties add(Item.Properties properties, ItemAttributeModifiers modifiers) {
        return keeping(properties, p -> p.attributes(modifiers));
    }

    /**
     * 跑一段会整份改写 {@code attribute_modifiers} 的设置（原版的 {@code sword()}、{@code humanoidArmor()}……），
     * 改写之前已有的修饰符接在它写的后面。{@code writer} 没碰这个组件时什么都不变。
     */
    public static Item.Properties keeping(Item.Properties properties, UnaryOperator<Item.Properties> writer) {
        // 组件要到注册表加载时才真正写，这里只是往初始化步骤里插两步：writer 之前记下已有的，之后合并
        ItemAttributeModifiers[] before = new ItemAttributeModifiers[1];
        Item.Properties written = writer.apply(step(properties,
                (components, key) -> before[0] = components.get(DataComponents.ATTRIBUTE_MODIFIERS)));
        return step(written, (components, key) -> {
            ItemAttributeModifiers kept = before[0];
            ItemAttributeModifiers own = components.get(DataComponents.ATTRIBUTE_MODIFIERS);
            if (kept == null || kept.modifiers().isEmpty() || kept == own) {
                return;
            }
            components.set(DataComponents.ATTRIBUTE_MODIFIERS, own == null ? kept : merge(key, own, kept));
        });
    }

    private static ItemAttributeModifiers merge(ResourceKey<?> item, ItemAttributeModifiers first, ItemAttributeModifiers second) {
        List<ItemAttributeModifiers.Entry> entries = new ArrayList<>(first.modifiers());
        for (ItemAttributeModifiers.Entry added : second.modifiers()) {
            for (ItemAttributeModifiers.Entry existing : first.modifiers()) {
                if (sameModifier(existing, added)) {
                    throw new IllegalStateException("Item " + item.identifier() + " sets attribute modifier " + added.modifier().id()
                            + " for " + added.attribute().getRegisteredName() + " twice; the item's base class already provides it");
                }
            }
            entries.add(added);
        }
        return new ItemAttributeModifiers(List.copyOf(entries));
    }

    /** 装备时会互相顶掉的两条：同一个属性（按注册键比，DeferredHolder 和引用型 Holder 指向同一个也算）、同一个 id、同一个槽位。 */
    private static boolean sameModifier(ItemAttributeModifiers.Entry a, ItemAttributeModifiers.Entry b) {
        return a.slot() == b.slot() && a.modifier().id().equals(b.modifier().id())
                && a.attribute().unwrapKey().equals(b.attribute().unwrapKey());
    }

    /**
     * 往 properties 的组件初始化步骤末尾接一步，这一步能读到前面已经写进去的组件。
     * {@link Item.Properties} 对外只有「写一个组件」的入口，这里借 {@code delayedComponent}：它拿
     * {@link DataComponentInitializers.SingleComponentInitializer#asInitializer} 的返回值当步骤，重写它就能接上任意一步。
     */
    private static Item.Properties step(Item.Properties properties, BiConsumer<DataComponentMap.Builder, ResourceKey<?>> action) {
        return properties.delayedComponent(DataComponents.ATTRIBUTE_MODIFIERS, new DataComponentInitializers.SingleComponentInitializer<>() {
            @Override
            public ItemAttributeModifiers create(HolderLookup.Provider context) {
                throw new IllegalStateException("IbxAttributeModifiers step was run as a plain component initializer");
            }

            @Override
            public <T> DataComponentInitializers.Initializer<T> asInitializer(DataComponentType<ItemAttributeModifiers> type) {
                return (components, context, key) -> action.accept(components, key);
            }
        });
    }
}
