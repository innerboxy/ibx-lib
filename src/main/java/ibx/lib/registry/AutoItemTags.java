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
import ibx.lib.item.IbxTridentItem;
import ibx.lib.item.IbxHeavyShieldItem;
import ibx.lib.item.general.IbxMagicWeaponItem;
import ibx.lib.item.general.IbxMeleeWeaponItem;
import ibx.lib.item.general.IbxRangedWeaponItem;
import ibx.lib.item.general.IbxAbstractShieldItem;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.Tags;

import java.util.List;
import java.util.function.Consumer;

/**
 * 按物品类自动加的物品标签：本模组的物品基类、接口（含子类、含其他模组用它们注册的物品）读完数据包标签后自动补进这些标签，
 * 各模组的 data 里不用再写。子类同时满足父类的规则（法典 = 法器的标签 + 法典自己的）。
 *
 * <p>附魔标签：代码只把各种类加进本模组的 {@code #ibx_lib:enchantable/<种类>}，原版的 {@code #minecraft:enchantable/*} 由本模组的 data
 * 引用这些标签（铁砧上能附；附魔台另外要物品有附魔能力 {@code Properties#enchantable}）。每个种类都挂进了
 * {@code enchantable/durability}（耐久、经验修补、消失诅咒），NeoForge 的 {@code #c:enchantables} 包含它，不用另外加。
 * 直接继承基类、不属于任何种类的物品不进附魔标签，由所在模组的 data 加。
 * {@code #c:tools} 包含 {@code #c:tools/melee_weapon}、{@code ranged_weapon}、{@code shield}，这三类不用另外加；法器的标签不在里面，所以单独加。
 * 弓、弩还各自进 {@code #ibx_lib:enchantable/bow}、{@code crossbow}，能附原版弓、弩的全部附魔
 * （见 {@link TagRegistry#ENCHANTABLE_BOW}、{@link TagRegistry#ENCHANTABLE_CROSSBOW}）；
 * 剑、匕首、砍刀、大剑、镰刀还各自进 {@code #ibx_lib:enchantable/sword}、{@code dagger}、{@code cutlass}、{@code claymore}、
 * {@code scythe}，能附原版剑的全部附魔（见 {@link TagRegistry#ENCHANTABLE_SWORD}）；
 * 战锤、拳套还各自进 {@code #ibx_lib:enchantable/hammer}、{@code fist}，同剑但没有横扫之刃（见 {@link TagRegistry#ENCHANTABLE_HAMMER}）；
 * 斧、双刃斧还各自进 {@code #ibx_lib:enchantable/axe}、{@code double_axe}，能附原版斧的全部附魔（见 {@link TagRegistry#ENCHANTABLE_AXE}）；
 * 矛还进 {@code #ibx_lib:enchantable/spear}，能附原版矛的全部附魔（见 {@link TagRegistry#ENCHANTABLE_SPEAR}）；
 * 三叉戟还进 NeoForge 的 {@code #c:tools/trident}、{@code #c:tools/ranged_weapon}（NeoForge 把原版三叉戟同时放在近战、远程两个标签里）
 * 和 {@code #ibx_lib:enchantable/trident}，能附原版三叉戟的全部附魔（见 {@link TagRegistry#ENCHANTABLE_TRIDENT}）；
 * 盾还进 {@code #ibx_lib:enchantable/shield}（见 {@link TagRegistry#ENCHANTABLE_SHIELD}）；
 * 法典、法杖还各自进 {@code #ibx_lib:enchantable/tome}、{@code staff}（见 {@link TagRegistry#ENCHANTABLE_TOME}）。</p>
 * 补标签的时机见 {@link ibx.lib.mixin.TagLoaderMixin}。
 */
public final class AutoItemTags {
    private AutoItemTags() {}

    /** {@code type} 的物品（含子类）进 {@code tags}。 */
    private record Rule(Class<?> type, List<TagKey<Item>> tags) {}

    private static final List<Rule> RULES = List.of(
            new Rule(IbxMeleeWeaponItem.class, List.of(Tags.Items.MELEE_WEAPON_TOOLS)),
            new Rule(IbxSwordItem.class, List.of(TagRegistry.TOOLS_SWORD, TagRegistry.ENCHANTABLE_SWORD)),
            new Rule(IbxDaggerItem.class, List.of(TagRegistry.TOOLS_DAGGER, TagRegistry.ENCHANTABLE_DAGGER)),
            new Rule(IbxCutlassItem.class, List.of(TagRegistry.TOOLS_CUTLASS, TagRegistry.ENCHANTABLE_CUTLASS)),
            new Rule(IbxClaymoreItem.class, List.of(TagRegistry.TOOLS_CLAYMORE, TagRegistry.ENCHANTABLE_CLAYMORE)),
            new Rule(IbxAxeItem.class, List.of(TagRegistry.TOOLS_AXE, TagRegistry.ENCHANTABLE_AXE)),
            new Rule(IbxDoubleAxeItem.class, List.of(TagRegistry.TOOLS_DOUBLE_AXE, TagRegistry.ENCHANTABLE_DOUBLE_AXE)),
            new Rule(IbxSpearItem.class, List.of(TagRegistry.TOOLS_SPEAR, TagRegistry.ENCHANTABLE_SPEAR)),
            new Rule(IbxTridentItem.class, List.of(Tags.Items.TOOLS_TRIDENT, Tags.Items.RANGED_WEAPON_TOOLS, TagRegistry.ENCHANTABLE_TRIDENT)),
            new Rule(IbxScytheItem.class, List.of(TagRegistry.TOOLS_SCYTHE, TagRegistry.ENCHANTABLE_SCYTHE)),
            new Rule(IbxHammerItem.class, List.of(TagRegistry.TOOLS_HAMMER, TagRegistry.ENCHANTABLE_HAMMER)),
            new Rule(IbxFistItem.class, List.of(TagRegistry.TOOLS_FIST, TagRegistry.ENCHANTABLE_FIST)),
            new Rule(IbxRangedWeaponItem.class, List.of(Tags.Items.RANGED_WEAPON_TOOLS)),
            new Rule(IbxBowItem.class, List.of(Tags.Items.TOOLS_BOW, TagRegistry.ENCHANTABLE_BOW)),
            new Rule(IbxCrossbowItem.class, List.of(Tags.Items.TOOLS_CROSSBOW, TagRegistry.ENCHANTABLE_CROSSBOW)),
            new Rule(IbxAbstractShieldItem.class, List.of(Tags.Items.TOOLS_SHIELD, TagRegistry.ENCHANTABLE_SHIELD)),
            new Rule(IbxShieldItem.class, List.of(TagRegistry.TOOLS_ONE_HANDED_SHIELD)),
            new Rule(IbxHeavyShieldItem.class, List.of(TagRegistry.TOOLS_HEAVY_SHIELD)),
            new Rule(IbxMagicWeaponItem.class, List.of(TagRegistry.CAN_CAST_SPELL, Tags.Items.TOOLS, TagRegistry.TOOLS_MAGIC_WEAPON)),
            new Rule(IbxTomeItem.class, List.of(TagRegistry.TOOLS_TOME, TagRegistry.ENCHANTABLE_TOME)),
            new Rule(IbxStaffItem.class, List.of(TagRegistry.TOOLS_STAFF, TagRegistry.ENCHANTABLE_STAFF)));

    /** 这件物品该自动进的标签。 */
    public static void forEach(Item item, Consumer<TagKey<Item>> consumer) {
        for (Rule rule : RULES) {
            if (rule.type().isInstance(item)) {
                rule.tags().forEach(consumer);
            }
        }
    }
}
