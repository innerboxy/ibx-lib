package ibx.lib.registry;

import ibx.lib.IbxLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;

/**
 * 本模组自建标签的 key。内容在 {@code data/ibx_lib/tags/}，其他模组用 {@code "replace": false} 往里加；
 * 按物品类自动填的（见 {@link AutoItemTags}）大多没有数据文件。
 */
public final class TagRegistry {
    private TagRegistry() {}

    /**
     * 法术伤害：所有"是不是法术伤害"的判断只看它（法术抗性、暴击等）。
     * 原版 {@code bypasses_armor}、{@code no_knockback}、{@code avoids_guardian_thorns} 都包含本标签，
     * 所以进了本标签的类型自动不吃护甲、不被原版击退、不触发守卫者荆棘。
     */
    public static final TagKey<DamageType> IS_SPELL = damageType("is_spell");

    /** 不打断吟唱的伤害：中毒、凋零、余火、冻伤、饥饿、溺水、摔落这类持续掉血。 */
    public static final TagKey<DamageType> CAST_IGNORE = damageType("cast_ignore");

    /**
     * 远程伤害：data 里放了原版的箭（{@code minecraft:arrow}）、三叉戟（{@code minecraft:trident}）和
     * {@link RangedDamageTypeRegistry#GENERIC_RANGED}，别的模组的远程伤害类型由所在模组的 data 往里加。
     * 原版 {@code bypasses_cooldown} 包含本标签，所以进了本标签的伤害不被受伤无敌时间挡（几支箭同时打中都算），
     * 但打完照样给目标留无敌时间。
     */
    public static final TagKey<DamageType> IS_RANGED = damageType("is_ranged");

    /**
     * 能施法的物品：手持右键释放当前选中的法术。所有 {@link ibx.lib.item.general.IbxMagicWeaponItem}（法典等各类法器）自动在里面
     * （{@link AutoItemTags}）；别的物品要施法，由物品所在模组的 data 往里加。
     */
    public static final TagKey<Item> CAN_CAST_SPELL = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "can_cast_spell"));

    /**
     * 能附原版弓的附魔的物品：本模组的 data 把它加进了原版的 {@code #minecraft:enchantable/bow}、{@code enchantable/durability}，
     * 所以能附力量、冲击、火矢、无限、耐久、经验修补、消失诅咒。
     * data 里放了原版的弓；所有 {@link ibx.lib.item.IbxBowItem} 自动在里面（{@link AutoItemTags}），别的物品由所在模组的 data 往里加。
     */
    public static final TagKey<Item> ENCHANTABLE_BOW = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "enchantable/bow"));

    /**
     * 能附原版弩的附魔的物品：本模组的 data 把它加进了原版的 {@code #minecraft:enchantable/crossbow}、{@code enchantable/durability}，
     * 所以能附多重射击、穿透、快速装填、耐久、经验修补、消失诅咒。
     * data 里放了原版的弩；所有 {@link ibx.lib.item.IbxCrossbowItem} 自动在里面（{@link AutoItemTags}），别的物品由所在模组的 data 往里加。
     */
    public static final TagKey<Item> ENCHANTABLE_CROSSBOW = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "enchantable/crossbow"));

    /**
     * 能附原版盾牌的附魔的物品：本模组的 data 把它加进了原版的 {@code #minecraft:enchantable/durability}，
     * 所以能附耐久、经验修补、消失诅咒（原版盾牌没有专属附魔）。
     * data 里放了原版的盾牌；所有 {@link ibx.lib.item.general.IbxAbstractShieldItem} 自动在里面（{@link AutoItemTags}），别的物品由所在模组的 data 往里加。
     */
    public static final TagKey<Item> ENCHANTABLE_SHIELD = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "enchantable/shield"));

    /**
     * 能附原版斧的附魔的物品：本模组的 data 把它加进了原版的 {@code #minecraft:enchantable/sharp_weapon}、
     * {@code enchantable/mining}、{@code enchantable/mining_loot}、{@code enchantable/durability}，所以能附锋利、亡灵杀手、
     * 节肢杀手、效率、时运、精准采集、耐久、经验修补、消失诅咒，又不用进 {@code #minecraft:axes}。
     * data 里放了 {@code #minecraft:axes}；所有 {@link ibx.lib.item.IbxAxeItem}（含双刃斧）自动在里面（{@link AutoItemTags}），
     * 别的物品由所在模组的 data 往里加。
     */
    public static final TagKey<Item> ENCHANTABLE_AXE = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "enchantable/axe"));

    /** 能附原版斧的附魔的双刃斧（{@link ibx.lib.item.IbxDoubleAxeItem}），data 里各自加进原版的四个标签，不经过 {@link #ENCHANTABLE_AXE}。 */
    public static final TagKey<Item> ENCHANTABLE_DOUBLE_AXE = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "enchantable/double_axe"));

    /**
     * 能附原版矛的附魔的物品：本模组的 data 把它加进了原版的 {@code #minecraft:enchantable/lunge}、
     * {@code enchantable/melee_weapon}、{@code enchantable/durability}，所以能附突进、锋利、亡灵杀手、节肢杀手、
     * 击退、抢夺、火焰附加、耐久、经验修补、消失诅咒，又不用进 {@code #minecraft:spears}。
     * data 里放了 {@code #minecraft:spears}；所有 {@link ibx.lib.item.IbxSpearItem} 自动在里面（{@link AutoItemTags}），别的物品由所在模组的 data 往里加。
     */
    public static final TagKey<Item> ENCHANTABLE_SPEAR = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "enchantable/spear"));

    /**
     * 能附原版三叉戟的附魔的物品：本模组的 data 把它加进了原版的 {@code #minecraft:enchantable/trident}、
     * {@code enchantable/durability}，所以能附忠诚、激流、引雷、穿刺、耐久、经验修补、消失诅咒。
     * data 里放了原版的三叉戟；所有 {@link ibx.lib.item.IbxTridentItem} 自动在里面（{@link AutoItemTags}），别的物品由所在模组的 data 往里加。
     */
    public static final TagKey<Item> ENCHANTABLE_TRIDENT = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "enchantable/trident"));

    /**
     * 能附原版剑的附魔的物品：本模组的 data 把它加进了原版的 {@code #minecraft:enchantable/sweeping}、
     * {@code enchantable/melee_weapon}、{@code enchantable/durability}，所以能附横扫之刃、锋利、亡灵杀手、节肢杀手、
     * 击退、抢夺、火焰附加、耐久、经验修补、消失诅咒，又不用进 {@code #minecraft:swords}。
     * data 里放了 {@code #minecraft:swords}；所有 {@link ibx.lib.item.IbxSwordItem} 自动在里面（{@link AutoItemTags}），别的物品由所在模组的 data 往里加。
     */
    public static final TagKey<Item> ENCHANTABLE_SWORD = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "enchantable/sword"));

    /*
     * 匕首、砍刀、大剑、镰刀能附的附魔同剑：本模组的 data 把下面四个标签各自加进了原版的 sweeping、melee_weapon、durability，
     * 不经过 {@code #ibx_lib:enchantable/sword}。对应的类自动在里面（AutoItemTags），别的物品由所在模组的 data 往里加。
     */

    /** 能附原版剑的附魔的匕首（{@link ibx.lib.item.IbxDaggerItem}）。 */
    public static final TagKey<Item> ENCHANTABLE_DAGGER = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "enchantable/dagger"));

    /** 能附原版剑的附魔的砍刀（{@link ibx.lib.item.IbxCutlassItem}）。 */
    public static final TagKey<Item> ENCHANTABLE_CUTLASS = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "enchantable/cutlass"));

    /** 能附原版剑的附魔的大剑（{@link ibx.lib.item.IbxClaymoreItem}）。 */
    public static final TagKey<Item> ENCHANTABLE_CLAYMORE = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "enchantable/claymore"));

    /** 能附原版剑的附魔的镰刀（{@link ibx.lib.item.IbxScytheItem}）。 */
    public static final TagKey<Item> ENCHANTABLE_SCYTHE = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "enchantable/scythe"));

    /*
     * 战锤、拳套能附的附魔同剑、但没有横扫之刃：本模组的 data 把下面两个标签各自加进了原版的 melee_weapon、durability（不进 sweeping），
     * 所以能附锋利、亡灵杀手、节肢杀手、击退、抢夺、火焰附加、耐久、经验修补、消失诅咒。
     * 对应的类自动在里面（AutoItemTags），别的物品由所在模组的 data 往里加。
     */

    /** 战锤（{@link ibx.lib.item.IbxHammerItem}）。 */
    public static final TagKey<Item> ENCHANTABLE_HAMMER = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "enchantable/hammer"));

    /** 拳套（{@link ibx.lib.item.IbxFistItem}）。 */
    public static final TagKey<Item> ENCHANTABLE_FIST = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "enchantable/fist"));

    /*
     * 法典、法杖没有原版对应物；本模组的 data 只把下面两个标签加进了原版的 durability，所以能附耐久、经验修补、消失诅咒；
     * 对应的类自动在里面（AutoItemTags），别的物品由所在模组的 data 往里加。
     */

    /** 法典（{@link ibx.lib.item.IbxTomeItem}）。 */
    public static final TagKey<Item> ENCHANTABLE_TOME = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "enchantable/tome"));

    /** 法杖（{@link ibx.lib.item.IbxStaffItem}）。 */
    public static final TagKey<Item> ENCHANTABLE_STAFF = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "enchantable/staff"));

    /*
     * 法器的通用标签。NeoForge 的 c 标签里没有法器类（只有弓、弩、三叉戟这些），这几个是本模组自建的，
     * 结构仿 {@code c:tools/ranged_weapon}、{@code c:tools/bow}。都由 AutoItemTags 按物品类自动填，没有数据文件。
     */

    /** 所有法器（{@link ibx.lib.item.general.IbxMagicWeaponItem} 及子类）。 */
    public static final TagKey<Item> TOOLS_MAGIC_WEAPON = commonItem("tools/magic_weapon");

    /** 法典（{@link ibx.lib.item.IbxTomeItem}）。 */
    public static final TagKey<Item> TOOLS_TOME = commonItem("tools/tome");

    /** 法杖（{@link ibx.lib.item.IbxStaffItem}）。 */
    public static final TagKey<Item> TOOLS_STAFF = commonItem("tools/staff");

    /*
     * 近战武器按种类分的通用标签。NeoForge 只有 c:tools/melee_weapon，这几个是本模组自建的，挂在它下面一层。
     * 都由 AutoItemTags 按物品类自动填。剑、斧、矛三个有数据文件，分别放了原版的 minecraft:swords、minecraft:axes、minecraft:spears，
     * 所以原版的剑、斧、矛也在里面；其余没有数据文件。本模组的物品不自动进原版那三个标签。
     */

    /** 剑（{@link ibx.lib.item.IbxSwordItem}）。 */
    public static final TagKey<Item> TOOLS_SWORD = commonItem("tools/sword");

    /** 匕首（{@link ibx.lib.item.IbxDaggerItem}）。 */
    public static final TagKey<Item> TOOLS_DAGGER = commonItem("tools/dagger");

    /** 砍刀（{@link ibx.lib.item.IbxCutlassItem}）。 */
    public static final TagKey<Item> TOOLS_CUTLASS = commonItem("tools/cutlass");

    /** 大剑（{@link ibx.lib.item.IbxClaymoreItem}）。 */
    public static final TagKey<Item> TOOLS_CLAYMORE = commonItem("tools/claymore");

    /** 斧（{@link ibx.lib.item.IbxAxeItem} 及子类）。 */
    public static final TagKey<Item> TOOLS_AXE = commonItem("tools/axe");

    /** 双刃斧（{@link ibx.lib.item.IbxDoubleAxeItem}），同时也在斧的标签里。 */
    public static final TagKey<Item> TOOLS_DOUBLE_AXE = commonItem("tools/double_axe");

    /** 矛（{@link ibx.lib.item.IbxSpearItem}）。 */
    public static final TagKey<Item> TOOLS_SPEAR = commonItem("tools/spear");

    /** 镰刀（{@link ibx.lib.item.IbxScytheItem}）。 */
    public static final TagKey<Item> TOOLS_SCYTHE = commonItem("tools/scythe");

    /** 战锤（{@link ibx.lib.item.IbxHammerItem}）。 */
    public static final TagKey<Item> TOOLS_HAMMER = commonItem("tools/hammer");

    /** 拳套（{@link ibx.lib.item.IbxFistItem}）。 */
    public static final TagKey<Item> TOOLS_FIST = commonItem("tools/fist");

    /*
     * 盾牌按单双手分的通用标签。NeoForge 只有 c:tools/shield，这两个是本模组自建的，挂在它下面一层。
     * 都由 AutoItemTags 按物品类自动填，没有数据文件。
     */

    /** 单手盾（{@link ibx.lib.item.IbxShieldItem}）。 */
    public static final TagKey<Item> TOOLS_ONE_HANDED_SHIELD = commonItem("tools/one_handed_shield");

    /** 双手盾（{@link ibx.lib.item.IbxHeavyShieldItem}）。 */
    public static final TagKey<Item> TOOLS_HEAVY_SHIELD = commonItem("tools/heavy_shield");

    private static TagKey<Item> commonItem(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
    }

    private static TagKey<DamageType> damageType(String name) {
        return TagKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(IbxLib.MODID, name));
    }
}
