package ibx.lib.registry;

import ibx.lib.item.IbxAxeItem;
import ibx.lib.item.IbxBowItem;
import ibx.lib.item.IbxClaymoreItem;
import ibx.lib.item.IbxCrossbowItem;
import ibx.lib.item.IbxCutlassItem;
import ibx.lib.item.IbxDaggerItem;
import ibx.lib.item.IbxDoubleAxeItem;
import ibx.lib.item.IbxFistItem;
import ibx.lib.item.IbxHammerItem;
import ibx.lib.item.IbxScytheItem;
import ibx.lib.item.IbxShieldItem;
import ibx.lib.item.IbxSpearItem;
import ibx.lib.item.IbxStaffItem;
import ibx.lib.item.IbxSwordItem;
import ibx.lib.item.IbxTomeItem;
import ibx.lib.item.IbxHeavyShieldItem;
import ibx.lib.item.general.IbxMagicWeaponItem;
import ibx.lib.item.general.IbxMeleeWeaponItem;
import ibx.lib.item.general.IbxRangedWeaponItem;
import ibx.lib.item.general.IbxAbstractShieldItem;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.Tags;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 按物品类自动加的物品标签：本模组的物品基类、接口（含子类、含其他模组用它们注册的物品）读完数据包标签后自动补进这些标签，
 * 各模组的 data 里不用再写。子类同时满足父类的规则（法典 = 法器的标签 + 法典自己的）。
 *
 * <p>会掉耐久的武器、盾、法器还进原版的 {@code #minecraft:enchantable/durability}，耐久、经验修补这两个附魔认它
 * （铁砧上能附；附魔台另外要物品有附魔能力 {@code Properties#enchantable}）。无法破坏的法器不进。
 * 武器、盾、法器（无法破坏的也算）都进 NeoForge 的 {@code #c:enchantables}。</p>
 * 补标签的时机见 {@link ibx.lib.mixin.TagLoaderMixin}。
 */
public final class AutoItemTags {
    private AutoItemTags() {}

    /** {@code type} 的物品（含子类）满足 {@code when} 就进 {@code tags}。 */
    private record Rule(Class<?> type, List<TagKey<Item>> tags, Predicate<Item> when) {
        Rule(Class<?> type, List<TagKey<Item>> tags) {
            this(type, tags, item -> true);
        }
    }

    private static final List<Rule> RULES = List.of(
            new Rule(IbxMeleeWeaponItem.class, List.of(Tags.Items.TOOLS, Tags.Items.MELEE_WEAPON_TOOLS)),
            new Rule(IbxSwordItem.class, List.of(TagRegistry.TOOLS_SWORD)),
            new Rule(IbxDaggerItem.class, List.of(TagRegistry.TOOLS_DAGGER)),
            new Rule(IbxCutlassItem.class, List.of(TagRegistry.TOOLS_CUTLASS)),
            new Rule(IbxClaymoreItem.class, List.of(TagRegistry.TOOLS_CLAYMORE)),
            new Rule(IbxAxeItem.class, List.of(TagRegistry.TOOLS_AXE)),
            new Rule(IbxDoubleAxeItem.class, List.of(TagRegistry.TOOLS_DOUBLE_AXE)),
            new Rule(IbxSpearItem.class, List.of(TagRegistry.TOOLS_SPEAR)),
            new Rule(IbxScytheItem.class, List.of(TagRegistry.TOOLS_SCYTHE)),
            new Rule(IbxHammerItem.class, List.of(TagRegistry.TOOLS_HAMMER)),
            new Rule(IbxFistItem.class, List.of(TagRegistry.TOOLS_FIST)),
            new Rule(IbxRangedWeaponItem.class, List.of(Tags.Items.TOOLS, Tags.Items.RANGED_WEAPON_TOOLS)),
            new Rule(IbxBowItem.class, List.of(Tags.Items.TOOLS_BOW)),
            new Rule(IbxCrossbowItem.class, List.of(Tags.Items.TOOLS_CROSSBOW)),
            new Rule(IbxAbstractShieldItem.class, List.of(Tags.Items.TOOLS, Tags.Items.TOOLS_SHIELD)),
            new Rule(IbxShieldItem.class, List.of(TagRegistry.TOOLS_ONE_HANDED_SHIELD)),
            new Rule(IbxHeavyShieldItem.class, List.of(TagRegistry.TOOLS_HEAVY_SHIELD)),
            new Rule(IbxMagicWeaponItem.class, List.of(TagRegistry.CAN_CAST_SPELL, Tags.Items.TOOLS, TagRegistry.TOOLS_MAGIC_WEAPON)),
            new Rule(IbxTomeItem.class, List.of(TagRegistry.TOOLS_TOME)),
            new Rule(IbxStaffItem.class, List.of(TagRegistry.TOOLS_STAFF)),
            // 能附魔的物品（NeoForge 的总标签，原版各个 enchantable/* 都在它下面）
            new Rule(IbxMeleeWeaponItem.class, List.of(Tags.Items.ENCHANTABLES)),
            new Rule(IbxRangedWeaponItem.class, List.of(Tags.Items.ENCHANTABLES)),
            new Rule(IbxAbstractShieldItem.class, List.of(Tags.Items.ENCHANTABLES)),
            new Rule(IbxMagicWeaponItem.class, List.of(Tags.Items.ENCHANTABLES)),
            // 耐久、经验修补
            new Rule(IbxMeleeWeaponItem.class, List.of(ItemTags.DURABILITY_ENCHANTABLE)),
            new Rule(IbxRangedWeaponItem.class, List.of(ItemTags.DURABILITY_ENCHANTABLE)),
            new Rule(IbxAbstractShieldItem.class, List.of(ItemTags.DURABILITY_ENCHANTABLE)),
            new Rule(IbxMagicWeaponItem.class, List.of(ItemTags.DURABILITY_ENCHANTABLE),
                    item -> ((IbxMagicWeaponItem) item).breakable()));

    /** 这件物品该自动进的标签。 */
    public static void forEach(Item item, Consumer<TagKey<Item>> consumer) {
        for (Rule rule : RULES) {
            if (rule.type().isInstance(item) && rule.when().test(item)) {
                rule.tags().forEach(consumer);
            }
        }
    }
}
