package ibx.lib.registry;

import ibx.lib.IbxLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;

/**
 * 本模组自建标签的 key。内容在 {@code data/ibx_lib/tags/}，其他模组用 {@code "replace": false} 往里加；
 * 按物品类自动填的（见 {@link AutoItemTags}）没有数据文件。
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
     * 能施法的物品：手持右键释放当前选中的法术。所有 {@link ibx.lib.item.magic_weapon.IbxMagicWeaponItem}（法典等各类法器）自动在里面
     * （{@link AutoItemTags}）；别的物品要施法，由物品所在模组的 data 往里加。
     */
    public static final TagKey<Item> CAN_CAST_SPELL = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "can_cast_spell"));

    /*
     * 法器的通用标签。NeoForge 的 c 标签里没有法器类（只有弓、弩、三叉戟这些），这几个是本模组自建的，
     * 结构仿 {@code c:tools/ranged_weapon}、{@code c:tools/bow}。都由 AutoItemTags 按物品类自动填，没有数据文件。
     */

    /** 所有法器（{@link ibx.lib.item.magic_weapon.IbxMagicWeaponItem} 及子类）。 */
    public static final TagKey<Item> TOOLS_MAGIC_WEAPON = commonItem("tools/magic_weapon");

    /** 法典（{@link ibx.lib.item.IbxTomeItem}）。 */
    public static final TagKey<Item> TOOLS_TOME = commonItem("tools/tome");

    /** 法杖（{@link ibx.lib.item.IbxStaffItem}）。 */
    public static final TagKey<Item> TOOLS_STAFF = commonItem("tools/staff");

    private static TagKey<Item> commonItem(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
    }

    private static TagKey<DamageType> damageType(String name) {
        return TagKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(IbxLib.MODID, name));
    }
}
