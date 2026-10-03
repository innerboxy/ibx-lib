package ibx.taverntales.lib.registry;

import ibx.taverntales.lib.TavernTalesLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;

/** 本模组自建标签的 key。内容在 {@code data/taverntales_l1b/tags/}，其他模组用 {@code "replace": false} 往里加。 */
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

    /** 能施法的物品：手持右键释放当前选中的法术。本模组里为空，由法典等模组往里加。 */
    public static final TagKey<Item> CAN_CAST_SPELL = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(TavernTalesLib.MODID, "can_cast_spell"));

    private static TagKey<DamageType> damageType(String name) {
        return TagKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(TavernTalesLib.MODID, name));
    }
}
