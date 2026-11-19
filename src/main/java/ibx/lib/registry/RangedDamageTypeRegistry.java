package ibx.lib.registry;

import ibx.lib.IbxLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/**
 * lib 的远程伤害类型的 key，定义在 {@code data/ibx_lib/damage_type/}。箭、三叉戟照原版用 {@code minecraft:arrow} /
 * {@code minecraft:trident}，不换成这里的类型；这里的给不是箭的远程攻击用。
 *
 * <p>特性靠标签：在 {@link TagRegistry#IS_RANGED} 里（所以不被受伤无敌时间挡），另外照原版的箭进了
 * {@code minecraft:is_projectile}（弹射物保护能减）、{@code always_kills_armor_stands}、{@code panic_causes}。</p>
 */
public final class RangedDamageTypeRegistry {
    private RangedDamageTypeRegistry() {}

    /**
     * 通用远程伤害：死亡信息、消耗、难度缩放都同原版的箭（死亡信息借原版 {@code arrow} 的）。
     * 用法如 {@code level.damageSources().source(GENERIC_RANGED, projectile, owner)}。
     */
    public static final ResourceKey<DamageType> GENERIC_RANGED = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(IbxLib.MODID, "generic_ranged"));
}
