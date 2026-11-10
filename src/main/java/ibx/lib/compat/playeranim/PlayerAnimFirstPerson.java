package ibx.lib.compat.playeranim;

import com.zigythebird.playeranim.accessors.IAvatarAnimationState;
import com.zigythebird.playeranim.animation.AvatarAnimManager;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import ibx.lib.compat.PlayerAnimCompat;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Player Animation Library 的「第一人称那一遍」：它播第一人称动作（Better Combat 的攻击）时，会把玩家自己的身体临时画出来，
 * 只留手臂、手持物品和盔甲层，并在这个玩家的渲染状态上做标记。
 *
 * <p>它自己只会藏原版盔甲层画的盔甲；不走原版盔甲层的盔甲（GeckoLib 盔甲）要照同样的规矩自己藏，用这里的查询：
 * 头、身、腿、脚一律不画，手臂只有动作打开了「第一人称显示盔甲」并且显示那只手时才画。</p>
 *
 * Player Animation Library 是可选模组：它的类只在 {@link Impl} 里引用，没装时不会加载，两个查询都返回 false。
 */
public final class PlayerAnimFirstPerson {
    private PlayerAnimFirstPerson() {}

    private static final boolean LOADED = PlayerAnimCompat.isLoaded();

    /** 这份实体渲染状态是不是正被当作「第一人称那一遍」来画。 */
    public static boolean isFirstPersonPass(EntityRenderState state) {
        return LOADED && Impl.isFirstPersonPass(state);
    }

    /** 第一人称那一遍里，这只手臂上的盔甲画不画。不是第一人称那一遍时返回 false。 */
    public static boolean showsArmArmor(EntityRenderState state, HumanoidArm arm) {
        return LOADED && Impl.showsArmArmor(state, arm);
    }

    private static final class Impl {
        private static boolean isFirstPersonPass(EntityRenderState state) {
            return state instanceof IAvatarAnimationState animated && animated.playerAnimLib$isFirstPersonPass();
        }

        private static boolean showsArmArmor(EntityRenderState state, HumanoidArm arm) {
            if (!(state instanceof IAvatarAnimationState animated) || !animated.playerAnimLib$isFirstPersonPass()) {
                return false;
            }
            AvatarAnimManager manager = animated.playerAnimLib$getAnimManager();
            if (manager == null) {
                return false;
            }
            FirstPersonConfiguration configuration = manager.getFirstPersonConfiguration();
            return configuration.isShowArmor() && (arm == HumanoidArm.RIGHT ? configuration.isShowRightArm() : configuration.isShowLeftArm());
        }
    }
}
