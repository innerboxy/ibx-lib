package ibx.lib.client;

import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.easing.EasingType;
import ibx.lib.IbxLib;
import ibx.lib.api.event.ShieldBashEvent;
import ibx.lib.item.IbxTwoHandedShieldItem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 双手盾的动作（Player Animation Library），自己和周围玩家都能看到。放哪段由主手的盾给，动画文件不存在就什么都不放；
 * 动作按右手做，主手是左手时整段镜像。主手普通攻击的动作不在这里：交给 Better Combat 的 {@code weapon_attributes}。
 *
 * <ul>
 *   <li>格挡：主手举着双手盾时循环放 {@link IbxTwoHandedShieldItem#guardAnimation}（双手持盾，就是盾击的预备姿势），放下时淡回原来的姿势。
 *       只管第三人称，第一人称照原版画举盾（图层的第一人称模式设成 {@code DISABLED}）：第三人称的盾立在身前、顶到头顶，
 *       第一人称照搬会把准星挡住。每 tick 按"在不在举盾"看（使用物品的标记是同步的，周围玩家也一样）。</li>
 *   <li>盾击：收到 {@link ShieldBashEvent} 时放一遍 {@link IbxTwoHandedShieldItem#bashAnimation}（从格挡姿势起手），
 *       第一人称也显示（用第三人称模型画手臂和盾，同施法动作）。盾击会让盾放下，放完回到双手站姿；右键还按着的话
 *       盾失效一过客户端重新举盾，再接回格挡。</li>
 * </ul>
 *
 * Player Animation Library 是 lib 的可选依赖：这个类引用了它的类，只能在装了它时加载——只由 {@code IbxLibClient} 判断装了才调 {@code register}，
 * 别的地方别直接引用这个类。
 */
public final class ShieldAnimations {
    private ShieldAnimations() {}

    private static final Identifier LAYER = Identifier.fromNamespaceAndPath(IbxLib.MODID, "shield");
    private static final Identifier REST = Identifier.fromNamespaceAndPath(IbxLib.MODID, "cast_rest");
    /** 图层优先级：盖过施法动作（1000）和 Better Combat 的站姿（1~4），让给 BC 的攻击动作（2000）。 */
    private static final int PRIORITY = 1005;
    private static final int BASH_FADE_IN_TICKS = 2, GUARD_FADE_IN_TICKS = 3, FADE_OUT_TICKS = 4;

    private enum Pose { GUARD, BASH }

    /** 正在放的那段；盾击记放完的时刻。 */
    private record Playing(Pose pose, long endTick) {}

    private static final Map<Avatar, Playing> PLAYING = new WeakHashMap<>();

    /** 客户端初始化时调：注册动画图层，监听盾击和客户端 tick。 */
    public static void register() {
        PlayerAnimLayers.register(LAYER, PRIORITY);
        NeoForge.EVENT_BUS.addListener(ShieldAnimations::onBash);
        NeoForge.EVENT_BUS.addListener(ShieldAnimations::onClientTick);
    }

    private static void onBash(ShieldBashEvent event) {
        if (!(event.getEntity() instanceof Avatar avatar) || !(avatar.getMainHandItem().getItem() instanceof IbxTwoHandedShieldItem shield)) {
            return;
        }
        PlayerAnimationController controller = PlayerAnimLayers.controller(avatar, LAYER);
        if (controller == null) {
            return;
        }
        PlayerAnimLayers.setMirrored(controller, avatar.getMainArm() == HumanoidArm.LEFT);
        controller.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
        Identifier animation = shield.bashAnimation();
        if (controller.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(BASH_FADE_IN_TICKS, EasingType.EASE_OUT_SINE), animation, true)) {
            Animation anim = PlayerAnimResources.getAnimation(animation);
            PLAYING.put(avatar, new Playing(Pose.BASH, avatar.level().getGameTime() + (long) Math.ceil(anim.length())));
        } else {
            PLAYING.remove(avatar);
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            PLAYING.clear();
            return;
        }
        for (Player player : mc.level.players()) {
            if (player instanceof Avatar avatar) {
                tick(avatar);
            }
        }
        PLAYING.keySet().removeIf(Avatar::isRemoved);
    }

    private static void tick(Avatar avatar) {
        Playing playing = PLAYING.get(avatar);
        if (playing != null && playing.pose() == Pose.BASH) {
            // 盾击放一遍自己停，放完再看在不在举盾
            if (avatar.level().getGameTime() < playing.endTick()) {
                return;
            }
            PLAYING.remove(avatar);
            playing = null;
        }
        IbxTwoHandedShieldItem guardingWith = guardingWith(avatar);
        if ((guardingWith != null) == (playing != null)) {
            return;
        }
        PlayerAnimationController controller = PlayerAnimLayers.controller(avatar, LAYER);
        if (controller == null) {
            return;
        }
        if (guardingWith != null) {
            PlayerAnimLayers.setMirrored(controller, avatar.getMainArm() == HumanoidArm.LEFT);
            controller.setFirstPersonMode(FirstPersonMode.DISABLED);
            controller.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(GUARD_FADE_IN_TICKS, EasingType.EASE_OUT_SINE),
                    guardingWith.guardAnimation(), true);
            // 动画文件不存在时也记下，免得每 tick 重试
            PLAYING.put(avatar, new Playing(Pose.GUARD, Long.MAX_VALUE));
        } else {
            PLAYING.remove(avatar);
            if (controller.isActive()) {
                controller.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE_OUT_TICKS, EasingType.EASE_IN_OUT_SINE), REST);
            }
        }
    }

    /** 主手正举着一面双手盾格挡就返回它，否则 null。 */
    private static @Nullable IbxTwoHandedShieldItem guardingWith(Avatar avatar) {
        return avatar.getMainHandItem().getItem() instanceof IbxTwoHandedShieldItem shield
                && avatar.isUsingItem() && avatar.getUsedItemHand() == InteractionHand.MAIN_HAND ? shield : null;
    }
}
