package ibx.lib.client;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.layered.modifier.MirrorModifier;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import org.jspecify.annotations.Nullable;

/**
 * 本模组玩家动作图层（Player Animation Library）的公共写法：每个玩家一个控制器，第一人称用第三人称模型画手臂和手上物品，
 * 挂一个镜像修饰符（动作都按右手做，要给左手用时整段镜像）。施法动作、盾击动作都用它。
 *
 * <p>Player Animation Library 是 lib 的可选依赖：这个类只被 {@code CastAnimations}、{@code ShieldAnimations} 用，
 * 它们只在装了动作库时注册；别的地方别直接引用这个类。</p>
 */
public final class PlayerAnimLayers {
    private PlayerAnimLayers() {}

    /** 注册一个图层，数值大的后叠加（盖在前面的图层上）。客户端初始化时调。 */
    public static void register(Identifier layer, int priority) {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(layer, priority, avatar -> {
            PlayerAnimationController controller = new PlayerAnimationController(avatar, (c, data, setter) -> PlayState.STOP);
            controller.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
            controller.setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));
            MirrorModifier mirror = new MirrorModifier();
            mirror.enabled = false;
            controller.addModifierLast(mirror);
            return controller;
        });
    }

    /** 这个玩家身上的这个图层；还没建好时为 null。 */
    public static @Nullable PlayerAnimationController controller(Avatar avatar, Identifier layer) {
        return PlayerAnimationAccess.getPlayerAnimationLayer(avatar, layer) instanceof PlayerAnimationController controller ? controller : null;
    }

    /** 打开 / 关掉整段镜像：动作按右手做，用在左手上时打开。 */
    public static void setMirrored(PlayerAnimationController controller, boolean mirrored) {
        for (var modifier : controller.getModifiers()) {
            if (modifier instanceof MirrorModifier mirror) {
                mirror.enabled = mirrored;
            }
        }
    }
}
