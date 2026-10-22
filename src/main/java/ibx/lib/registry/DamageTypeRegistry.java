package ibx.lib.registry;

import ibx.lib.IbxLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/**
 * 伤害类型的 key。全部子模组的伤害类型都定义在这里（{@code data/ibx_lib/damage_type/}），
 * 特性靠标签：本模组的见 {@link TagRegistry}，原版的（无视护甲、无击退等）在 {@code data/minecraft/tags/damage_type/}。
 * 法术伤害类型加进 {@link TagRegistry#IS_SPELL} 就进了法术伤害框架。
 */
public final class DamageTypeRegistry {
    private DamageTypeRegistry() {}

    /** 通用法术伤害：法术不指定伤害类型时用它。已在 {@link TagRegistry#IS_SPELL} 里。 */
    public static final ResourceKey<DamageType> SPELL_GENERAL = key("spell_general");

    /** 消化：别西卜胃里的伤害，无视护甲、盾牌和无敌帧，没有击退。 */
    public static final ResourceKey<DamageType> DIGEST = key("digest");

    /** 流血：真实伤害，无视护甲、盔甲韧性、抗性提升和保护类附魔，没有击退；吸收（金心）照常先扣。 */
    public static final ResourceKey<DamageType> BLEEDING = key("bleeding");

    /**
     * 灵魂链接：链接里别人挨打、平摊过来的那一份。已经按挨打者的护甲等减过，所以是真实伤害：
     * 无视护甲、盔甲韧性、抗性提升、保护类附魔、盾牌和无敌帧，没有击退；吸收（金心）照常先扣。
     */
    public static final ResourceKey<DamageType> SOUL_LINK = key("soul_link");

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(IbxLib.MODID, name));
    }
}
