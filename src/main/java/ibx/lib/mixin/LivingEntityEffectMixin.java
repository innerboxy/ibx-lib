package ibx.lib.mixin;

import ibx.lib.effect.IbxEffectTarget;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/** 给 {@link LivingEntity} 加带「是否叠加」「上限」的 {@code addEffect}，规则见 {@link IbxEffectTarget}。 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEffectMixin implements IbxEffectTarget {
    @Override
    public boolean addEffect(MobEffectInstance effect, @Nullable Entity source, boolean stack, int maxAmplifier) {
        LivingEntity self = (LivingEntity) (Object) this;
        MobEffectInstance existing = stack ? self.getEffect(effect.getEffect()) : null;
        int amplifier = effect.getAmplifier();
        int duration = effect.getDuration();
        if (existing != null) {
            amplifier = existing.getAmplifier() + effect.getAmplifier() + 1;
            duration = existing.isInfiniteDuration() || effect.isInfiniteDuration()
                    ? MobEffectInstance.INFINITE_DURATION
                    : Math.max(existing.getDuration(), effect.getDuration());
        }
        if (maxAmplifier != NO_LIMIT) {
            amplifier = Math.min(amplifier, maxAmplifier);
        }
        if (amplifier != effect.getAmplifier() || duration != effect.getDuration()) {
            effect = new MobEffectInstance(effect.getEffect(), duration, amplifier, effect.isAmbient(), effect.isVisible(), effect.showIcon());
        }
        return self.addEffect(effect, source);
    }
}
