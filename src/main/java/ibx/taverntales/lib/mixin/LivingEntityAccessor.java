package ibx.taverntales.lib.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读写 {@code LivingEntity#lastHurt}（无敌帧期间的伤害比较基准，protected）。
 *
 * <p>法术"打完不留无敌帧"要在命中后把它和 {@code invulnerableTime} 一起还原，见
 * {@link ibx.taverntales.lib.api.SpellDamageApi#hurt}。</p>
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("lastHurt")
    float taverntales_l1b$getLastHurt();

    @Accessor("lastHurt")
    void taverntales_l1b$setLastHurt(float lastHurt);
}
