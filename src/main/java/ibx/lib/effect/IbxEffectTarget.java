package ibx.lib.effect;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * 原版 {@link LivingEntity#addEffect} 的扩展版本：末尾多「是否叠加」「上限」两个参数。
 * lib 用 mixin 把它们加到 {@link LivingEntity} 上，并用接口注入让 {@code entity.addEffect(效果, 来源, true, 4)} 能直接写；
 * 开发环境没有接口注入时先强转：{@code ((IbxEffectTarget) entity).addEffect(...)}。
 *
 * <p>叠加（{@code stack = true}）且目标身上已有同一个效果时：放大等级 = 原来的 + （给的放大等级 + 1），不超过上限；
 * 时长取原来剩下的和给的较长的那个（有一边无限就无限）；环境效果、粒子、图标三项照原版用给的。没有这个效果时按给的直接加。
 * 不叠加时同原版。两种情况下给的等级超过上限都先截到上限。算好后交给原版的 {@code addEffect(效果, 来源)}，
 * 所以能否生效的判断、NeoForge 的效果事件、属性刷新、同步都照原版。</p>
 *
 * <p>上限用放大等级（从 0 起，4 = V 级）；{@link #NO_LIMIT} 或不填就是不封顶。</p>
 */
public interface IbxEffectTarget {
    /** 不封顶。 */
    int NO_LIMIT = -1;

    /** 同 {@link #addEffect(MobEffectInstance, Entity, boolean, int)}，没有来源。 */
    default boolean addEffect(MobEffectInstance effect, boolean stack, int maxAmplifier) {
        return this.addEffect(effect, null, stack, maxAmplifier);
    }

    /** 同 {@link #addEffect(MobEffectInstance, Entity, boolean, int)}，没有来源、不封顶。 */
    default boolean addEffect(MobEffectInstance effect, boolean stack) {
        return this.addEffect(effect, null, stack, NO_LIMIT);
    }

    /** 同 {@link #addEffect(MobEffectInstance, Entity, boolean, int)}，不封顶。 */
    default boolean addEffect(MobEffectInstance effect, @Nullable Entity source, boolean stack) {
        return this.addEffect(effect, source, stack, NO_LIMIT);
    }

    /**
     * 给效果，可以叠加、可以设上限；返回值同原版：效果有没有加上或变化。
     *
     * @param stack        是否叠加：在目标现有的等级上往上加
     * @param maxAmplifier 上限（放大等级，从 0 起）；{@link #NO_LIMIT} 不封顶
     */
    default boolean addEffect(MobEffectInstance effect, @Nullable Entity source, boolean stack, int maxAmplifier) {
        // 接口注入要求有默认实现，真正的实现由 mixin 写进 LivingEntity
        throw new UnsupportedOperationException("Implemented by LivingEntityEffectMixin");
    }
}
