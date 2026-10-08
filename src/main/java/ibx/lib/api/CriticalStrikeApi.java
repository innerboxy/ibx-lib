package ibx.lib.api;

import ibx.lib.registry.AttributeRegistry;
import ibx.lib.util.AttributeUtil;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * 暴击的统一读数与判定入口。近战、远程、法术都从这里取值，保证口径一致。
 *
 * <p>暴击伤害的公式是 <b>最终伤害 = 原伤害 × (1 + critical_damage)</b>，
 * 所以属性值 0.5 表示多打五成。{@link #getMultiplier} 返回的就是括号里那个倍率。</p>
 */
public final class CriticalStrikeApi {
    private CriticalStrikeApi() {}

    /** 暴击率（0~1 之间是概率，大于等于 1 必定暴击）。没挂该属性的实体按属性默认值算。 */
    public static float getChance(LivingEntity attacker) {
        return (float) AttributeUtil.read(attacker, AttributeRegistry.CRITICAL_CHANCE);
    }

    /** 暴击伤害加成，即公式里的 critical_damage。没挂该属性的实体按属性默认值算。 */
    public static float getDamageBonus(LivingEntity attacker) {
        return (float) AttributeUtil.read(attacker, AttributeRegistry.CRITICAL_DAMAGE);
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

    /**
     * 远程、法术暴击命中时补上原版近战暴击的那套表现：暴击粒子 + 暴击音效。只在服务端调用。
     *
     * <p>原版近战在 {@code Player#attack} 里做同样两件事，只是位置取的是玩家自己；
     * 远程射手、施法者可能在几十格外，所以这里都放在被击中的目标身上。粒子沿用原版的动画包
     * （{@code CRITICAL_HIT}），客户端收到后自己生成跟踪粒子发射器，和近战完全一致。</p>
     */
    public static void spawnEffects(LivingEntity target) {
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (target.level() instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().sendToTrackingPlayersAndSelf(target,
                    new ClientboundAnimatePacket(target, ClientboundAnimatePacket.CRITICAL_HIT));
        }
    }
}
