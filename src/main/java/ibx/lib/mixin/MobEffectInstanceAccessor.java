package ibx.lib.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读写 {@code MobEffectInstance#hiddenEffect}（被更强、更短的同种效果盖住的那一份，上面的到期后它会顶上来，private）。
 *
 * <p>饰品摘下、套装掉档时要把自己给的常驻效果从这条链里摘掉，不然盖着它的药水一到期它又冒出来，见
 * {@link ibx.lib.effect.GrantedEffects#revoke}。</p>
 */
@Mixin(MobEffectInstance.class)
public interface MobEffectInstanceAccessor {
    @Accessor("hiddenEffect")
    @Nullable MobEffectInstance ibx_lib$getHiddenEffect();

    @Accessor("hiddenEffect")
    void ibx_lib$setHiddenEffect(@Nullable MobEffectInstance hiddenEffect);
}
