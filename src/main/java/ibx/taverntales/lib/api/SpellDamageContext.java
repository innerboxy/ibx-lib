package ibx.taverntales.lib.api;

import ibx.taverntales.lib.registry.AttributeRegistry;
import ibx.taverntales.lib.util.AttributeUtil;
import net.minecraft.world.entity.LivingEntity;

/**
 * 法术伤害公式的输入：施法瞬间从施法者身上读出的数值。
 *
 * <p>伤害公式由法术自带，形如 {@code ctx -> ctx.spellPower() * 1.6F}。框架在施法瞬间用 {@link #of} 读一次属性，
 * 交给公式算出基础值，再连同暴击参数一起写进快照；之后换装备、buff 过期都不影响已经算好的伤害。治疗法术同样用它。</p>
 *
 * <p>暴击在这里只记参数、不掷骰：命中时按快照里的概率每个目标各掷一次。</p>
 *
 * @param caster         施法者。公式要用别的数值（最大生命、护甲……）就从它身上读
 * @param spellPower     法术强度（{@code spell_power} 总值）
 * @param canCrit        法术能否暴击（{@code can_spell_crit} 总值大于等于 1）
 * @param critChance     暴击率（{@code critical_chance}）：0~1 为概率，大于等于 1 必定暴击
 * @param critMultiplier 暴击时的伤害倍率，即 1 + {@code critical_damage}
 */
public record SpellDamageContext(LivingEntity caster, float spellPower, boolean canCrit,
                                 float critChance, float critMultiplier) {

    /**
     * 按施法者此刻的属性生成。
     *
     * <p>两端都能调用：用到的属性都同步到客户端，客户端可以拿它算 tooltip 里的预估伤害。</p>
     */
    public static SpellDamageContext of(LivingEntity caster) {
        return new SpellDamageContext(
                caster,
                (float) AttributeUtil.read(caster, AttributeRegistry.SPELL_POWER),
                AttributeUtil.read(caster, AttributeRegistry.CAN_SPELL_CRIT) >= 1.0D,
                CriticalStrikeApi.getChance(caster),
                CriticalStrikeApi.getMultiplier(caster));
    }
}
