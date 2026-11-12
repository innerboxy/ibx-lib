package ibx.lib.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import ibx.lib.api.SpellDamageSnapshot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * 用 GeckoLib 3D 模型的法术弹射物：飞行、命中、伤害都同 {@link SpellProjectile}，多了一段飞行中循环播的动画（{@link #flyAnimation()}）。
 * 客户端用 {@code client.renderer.GeoSpellProjectileRenderer} 画。只用一张贴图的弹射物继承 {@link SpellProjectile} 就够了。
 *
 * <p>这个类实现了 GeckoLib 的 {@code GeoEntity}，要装 GeckoLib 才能加载。GeckoLib 对 lib 是可选依赖（用它的模组自己强制要求），
 * 所以 lib 自己常驻的代码里不能引用这个类（{@code instanceof}、参数类型都不行），只给注册了弹射物的模组继承。</p>
 */
public abstract class GeoSpellProjectile extends SpellProjectile implements GeoEntity {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /** 实体类型的工厂用（读档、客户端生成），不要在法术里用。 */
    protected GeoSpellProjectile(EntityType<? extends GeoSpellProjectile> type, Level level) {
        super(type, level);
    }

    /** 法术里用，同 {@link SpellProjectile#SpellProjectile(EntityType, LivingEntity, SpellDamageSnapshot)}。 */
    protected GeoSpellProjectile(EntityType<? extends GeoSpellProjectile> type, LivingEntity caster, SpellDamageSnapshot snapshot) {
        super(type, caster, snapshot);
    }

    /** 飞行中一直循环播的动画；null 为不播。 */
    protected @Nullable RawAnimation flyAnimation() {
        return null;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        RawAnimation fly = this.flyAnimation();
        if (fly != null) {
            controllers.add(new AnimationController<GeoSpellProjectile>("fly", test -> test.setAndContinue(fly)));
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
