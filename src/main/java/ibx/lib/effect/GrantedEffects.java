package ibx.lib.effect;

import ibx.lib.mixin.MobEffectInstanceAccessor;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * 装备给的常驻效果（饰品戴着、套装档位生效期间一直有的状态效果）怎么给、怎么摘。
 *
 * <p>给出去的那份是：无限时长，不闪、不冒粒子、显示图标。摘的时候靠「无限时长、不显示粒子、等级相同」认出它，别处来的同种效果不动。
 * 只在服务端调。</p>
 */
public final class GrantedEffects {
    private GrantedEffects() {}

    /**
     * 身上没有这个效果、或者等级比 amplifier 低，就补上一份常驻的；有等级不低于它的就不动。要一直保持的话每刻调一次：
     * 等级更高的药水生效期间不动它，药水到期后的下一刻补上。
     *
     * @param amplifier 效果等级减 1，0 是一级
     */
    public static void grant(LivingEntity entity, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance current = entity.getEffect(effect);
        if (current == null || current.getAmplifier() < amplifier) {
            entity.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, amplifier, false, false, true));
        }
    }

    /**
     * 去掉 {@link #grant} 给的那一份，别处来的留着。正生效的就是它：整个去掉（别的装备还给着同一效果的话，它下一刻会再补上）。
     * 它被更强的同种效果（药水、信标）盖住了：从压在下面的那条链里摘掉，不然上面的一到期它又冒出来、再也没人去掉。
     */
    public static void revoke(LivingEntity entity, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance current = entity.getEffect(effect);
        if (current == null) {
            return;
        }
        if (isGranted(current, amplifier)) {
            entity.removeEffect(effect);
            return;
        }
        MobEffectInstanceAccessor above = (MobEffectInstanceAccessor) current;
        MobEffectInstance hidden = above.ibx_lib$getHiddenEffect();
        while (hidden != null) {
            if (isGranted(hidden, amplifier)) {
                above.ibx_lib$setHiddenEffect(((MobEffectInstanceAccessor) hidden).ibx_lib$getHiddenEffect());
            } else {
                above = (MobEffectInstanceAccessor) hidden;
            }
            hidden = above.ibx_lib$getHiddenEffect();
        }
    }

    private static boolean isGranted(MobEffectInstance instance, int amplifier) {
        return instance.isInfiniteDuration() && !instance.isVisible() && instance.getAmplifier() == amplifier;
    }
}
