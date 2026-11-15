package ibx.lib.mixin.jade;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import snownee.jade.addon.vanilla.StatusEffectsProvider;

import java.util.function.Predicate;

/**
 * Jade 联动：生物身上的药水效果，「显示图标」的也发给 Jade 显示。
 *
 * <p>Jade 在服务端打包效果时只留粒子可见（{@link MobEffectInstance#isVisible}）的，不冒粒子但显示图标的效果
 * （本模组挂效果的默认写法）在 Jade 里就看不到。这里把它第一道过滤换成「粒子可见或显示图标」；
 * Jade 自己配置里隐藏的效果照样隐藏（第二道过滤不动）。</p>
 */
@Mixin(StatusEffectsProvider.class)
public abstract class StatusEffectsProviderMixin {
    @ModifyArg(method = "streamData(Lsnownee/jade/api/EntityAccessor;)Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;", ordinal = 0))
    private Predicate<MobEffectInstance> ibx$includeIconEffects(Predicate<MobEffectInstance> original) {
        return effect -> effect.isVisible() || effect.showIcon();
    }
}
