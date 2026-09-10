package ibx.taverntales.lib.api;

import ibx.taverntales.lib.registry.AttributeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * 暴击的统一读数与判定入口。近战、远程都从这里取值，保证口径一致。
 *
 * <p>暴击伤害的公式是 <b>最终伤害 = 原伤害 × (1 + critical_damage)</b>，
 * 所以属性值 0.5 表示多打五成。{@link #getMultiplier} 返回的就是括号里那个倍率。</p>
 */
public final class CriticalStrikeApi {
    private CriticalStrikeApi() {}

    /** 暴击率（0~1 之间是概率，大于等于 1 必定暴击）。没挂该属性的实体返回 0。 */
    public static float getChance(LivingEntity attacker) {
        return (float) read(attacker, AttributeRegistry.CRITICAL_CHANCE);
    }

    /** 暴击伤害加成，即公式里的 critical_damage。没挂该属性的实体返回 0。 */
    public static float getDamageBonus(LivingEntity attacker) {
        return (float) read(attacker, AttributeRegistry.CRITICAL_DAMAGE);
    }

    /** 暴击时的伤害倍率，等于 1 + {@link #getDamageBonus}。不暴击时不要用它。 */
    public static float getMultiplier(LivingEntity attacker) {
        return 1.0F + getDamageBonus(attacker);
    }

    /**
     * 掷一次暴击判定。
     *
     * <p><b>只应在服务端调用</b>：客户端也跑一遍会得到不同的随机数，导致特效与实际伤害对不上。
     * 暴击率大于等于 1 时必定返回 true（随机数取值范围是 [0,1)）。</p>
     */
    public static boolean roll(LivingEntity attacker) {
        return attacker.getRandom().nextFloat() < getChance(attacker);
    }

    private static double read(LivingEntity attacker, Holder<Attribute> attribute) {
        // 属性只挂在玩家身上，生物读不到；直接问会经 AttributeSupplier 抛异常，所以先判断。
        return attacker.getAttributes().hasAttribute(attribute) ? attacker.getAttributeValue(attribute) : 0.0D;
    }
}
