package ibx.lib.registry;

import ibx.lib.item.IbxBowItem;
import ibx.lib.item.IbxCrossbowItem;
import ibx.lib.item.IbxStaffItem;
import ibx.lib.item.IbxTomeItem;
import ibx.lib.item.magic_weapon.IbxMagicWeaponItem;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.Tags;

import java.util.List;
import java.util.function.Consumer;

/**
 * 按物品类自动加的物品标签：本模组的物品基类（含子类、含其他模组用它们注册的物品）读完数据包标签后自动补进这些标签，
 * 各模组的 data 里不用再写。子类同时满足父类的规则（法典 = 法器的标签 + 法典自己的）。
 * 补标签的时机见 {@link ibx.lib.mixin.TagLoaderMixin}。
 */
public final class AutoItemTags {
    private AutoItemTags() {}

    private record Rule(Class<? extends Item> type, List<TagKey<Item>> tags) {}

    private static final List<Rule> RULES = List.of(
            new Rule(IbxBowItem.class, List.of(Tags.Items.TOOLS, Tags.Items.RANGED_WEAPON_TOOLS, Tags.Items.TOOLS_BOW)),
            new Rule(IbxCrossbowItem.class, List.of(Tags.Items.TOOLS, Tags.Items.RANGED_WEAPON_TOOLS, Tags.Items.TOOLS_CROSSBOW)),
            new Rule(IbxMagicWeaponItem.class, List.of(TagRegistry.CAN_CAST_SPELL, Tags.Items.TOOLS, TagRegistry.TOOLS_MAGIC_WEAPON)),
            new Rule(IbxTomeItem.class, List.of(TagRegistry.TOOLS_TOME)),
            new Rule(IbxStaffItem.class, List.of(TagRegistry.TOOLS_STAFF)));

    /** 这件物品该自动进的标签。 */
    public static void forEach(Item item, Consumer<TagKey<Item>> consumer) {
        for (Rule rule : RULES) {
            if (rule.type().isInstance(item)) {
                rule.tags().forEach(consumer);
            }
        }
    }
}
