package ibx.lib.client.spell;

import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.easing.EasingType;
import ibx.lib.IbxLib;
import ibx.lib.api.event.SpellCastStateEvent;
import ibx.lib.client.PlayerAnimLayers;
import ibx.lib.item.general.IbxMagicWeaponItem;
import ibx.lib.registry.TagRegistry;
import ibx.lib.spell.Spell;
import ibx.lib.spell.SpellCasting;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 玩家施法动作（Player Animation Library）：收到施法开始 / 结束（{@link SpellCastStateEvent}）时在玩家身上放动画，
 * 自己和周围玩家都能看到。动画文件在 lib 的 {@code player_animations/}。
 *
 * <ul>
 *   <li>放哪个：法术自己指定的（{@link Spell#castAnimation}）优先，否则用施法那只手上法器的
 *       （{@link IbxMagicWeaponItem#castAnimation}，默认按施法类型分），不是 Ibx 法器的施法物品用默认那几段（{@link IbxMagicWeaponItem#defaultCastAnimation}）。</li>
 *   <li>用哪只手：主手拿着施法物品就是主手，否则副手。动画都按右手做，施法的是左手时整段镜像。</li>
 *   <li>开始时从当前姿势淡入。施法结束时：只放一遍的动画让它自己放完；循环的淡到一段空动画 {@code cast_rest}，
 *       手臂平滑回到原版姿势，但至少放满 {@link #MIN_SHOW_TICKS}（瞬发的结束紧跟在开始后面，不等就看不到）。
 *       直接停的话控制器立刻失效，淡出不会生效，所以淡到空动画。</li>
 *   <li>第一人称也显示：用第三人称模型的手臂和手上物品画。</li>
 *   <li>施法中或动作还在放时，施法那只手上的物品算"正在施法"（{@link #isCastingWith}），法典据此翻开，瞬发也一样。</li>
 * </ul>
 */
public final class CastAnimations {
    private CastAnimations() {}

    private static final Identifier LAYER = Identifier.fromNamespaceAndPath(IbxLib.MODID, "cast");
    private static final Identifier REST = Identifier.fromNamespaceAndPath(IbxLib.MODID, "cast_rest");
    /** 图层优先级：数值大的后叠加。 */
    private static final int PRIORITY = 1000;
    private static final int FADE_IN_TICKS = 3, FADE_OUT_TICKS = 5;
    /** 循环动画至少放这么久才淡出。 */
    private static final int MIN_SHOW_TICKS = 12;

    /** 正在放施法动作的玩家：开始时刻、动画是否循环、长度（tick）、排好的淡出时刻（没排为 -1）。 */
    private static final Map<Avatar, Playing> PLAYING = new WeakHashMap<>();

    private static final class Playing {
        final long startTick;
        final boolean loops;
        final float lengthTicks;
        long fadeAt = -1L;

        Playing(long startTick, boolean loops, float lengthTicks) {
            this.startTick = startTick;
            this.loops = loops;
            this.lengthTicks = lengthTicks;
        }
    }

    /**
     * 这件物品是不是正拿在手上施法：施法者正在施法（两端同步的施法状态），或施法动作还在放（瞬发法术的动作比施法本身长），
     * 并且物品就在施法那只手上。物品模型条件 {@code ibx_lib:casting} 用它（法典施法时翻开）。
     */
    public static boolean isCastingWith(LivingEntity owner, ItemStack stack) {
        boolean casting = SpellCasting.isCasting(owner) || owner instanceof Avatar avatar && PLAYING.containsKey(avatar);
        return casting && owner.getItemInHand(castingHand(owner)) == stack;
    }

    /** 客户端初始化时调：注册动画图层，监听施法开始 / 结束和客户端 tick。 */
    public static void register() {
        PlayerAnimLayers.register(LAYER, PRIORITY);
        NeoForge.EVENT_BUS.addListener(CastAnimations::onCastStart);
        NeoForge.EVENT_BUS.addListener(CastAnimations::onCastEnd);
        NeoForge.EVENT_BUS.addListener(CastAnimations::onClientTick);
    }

    private static void onCastStart(SpellCastStateEvent.Start event) {
        if (!(event.getCaster() instanceof Avatar avatar)) {
            return;
        }
        PlayerAnimationController controller = controller(avatar);
        if (controller == null) {
            return;
        }
        InteractionHand hand = castingHand(avatar);
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? avatar.getMainArm() : avatar.getMainArm().getOpposite();
        PlayerAnimLayers.setMirrored(controller, arm == HumanoidArm.LEFT);
        Identifier animation = animationFor(event.getContext().spell(), avatar.getItemInHand(hand));
        if (controller.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE_IN_TICKS, EasingType.EASE_OUT_SINE), animation, true)) {
            Animation anim = PlayerAnimResources.getAnimation(animation);
            PLAYING.put(avatar, new Playing(avatar.level().getGameTime(), loops(anim), anim.length()));
        } else {
            PLAYING.remove(avatar);
        }
    }

    private static void onCastEnd(SpellCastStateEvent.End event) {
        if (!(event.getCaster() instanceof Avatar avatar)) {
            return;
        }
        Playing playing = PLAYING.get(avatar);
        // 只放一遍的动画放完自己停，tick 里按长度移出
        if (playing == null || !playing.loops) {
            return;
        }
        long now = avatar.level().getGameTime();
        playing.fadeAt = Math.max(now, playing.startTick + MIN_SHOW_TICKS);
        if (playing.fadeAt <= now) {
            fadeOut(avatar);
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        for (Iterator<Map.Entry<Avatar, Playing>> it = PLAYING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Avatar, Playing> entry = it.next();
            Avatar avatar = entry.getKey();
            Playing playing = entry.getValue();
            long now = avatar.level().getGameTime();
            if (avatar.isRemoved()) {
                it.remove();
            } else if (playing.loops && playing.fadeAt >= 0L && now >= playing.fadeAt) {
                it.remove();
                fadeOutController(avatar);
            } else if (!playing.loops && now >= playing.startTick + (long) Math.ceil(playing.lengthTicks)) {
                it.remove();
            }
        }
    }

    private static void fadeOut(Avatar avatar) {
        PLAYING.remove(avatar);
        fadeOutController(avatar);
    }

    private static void fadeOutController(Avatar avatar) {
        PlayerAnimationController controller = controller(avatar);
        if (controller != null && controller.isActive()) {
            controller.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE_OUT_TICKS, EasingType.EASE_IN_OUT_SINE), REST);
        }
    }

    private static Identifier animationFor(Spell spell, ItemStack stack) {
        Identifier own = spell.castAnimation();
        if (own != null) {
            return own;
        }
        return stack.getItem() instanceof IbxMagicWeaponItem weapon
                ? weapon.castAnimation(spell.castType())
                : IbxMagicWeaponItem.defaultCastAnimation(spell.castType());
    }

    /** 只放一遍的动画（没写循环）会自己结束；其余（循环、停在最后一帧）要在施法结束时淡出。 */
    private static boolean loops(Animation animation) {
        Animation.LoopType type = animation.loopType();
        return type != Animation.LoopType.PLAY_ONCE && type != Animation.LoopType.DEFAULT;
    }

    /** 主手拿着施法物品就是主手，否则副手（都没有时按主手）。 */
    private static InteractionHand castingHand(LivingEntity entity) {
        return !entity.getMainHandItem().is(TagRegistry.CAN_CAST_SPELL) && entity.getOffhandItem().is(TagRegistry.CAN_CAST_SPELL)
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private static @Nullable PlayerAnimationController controller(Avatar avatar) {
        return PlayerAnimLayers.controller(avatar, LAYER);
    }
}
