package ibx.lib.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import ibx.lib.api.CriticalStrikeApi;
import ibx.lib.api.RangedWeaponApi;
import ibx.lib.item.IbxCrossbowItem;
import ibx.lib.item.ranged_weapon.RangedWeaponItem;
import ibx.lib.item.ranged_weapon.VanillaRangedWeapons;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

/**
 * 原版弩类的注入，分两部分，判断口径不同：
 *
 * <p><b>一、装填时长（Ibx 弩与被接管的原版弩都生效）。</b>{@code CrossbowItem.getChargeDuration} 是 static，
 * 子类重写不了，而它是所有 AI 与渲染判断装填进度的唯一入口。改这一处，下面几处一次性对齐：</p>
 * <ul>
 *   <li>{@code RangedCrossbowAttackGoal}——掠夺者等目标 AI 的松手时机；</li>
 *   <li>{@code CrossbowAttack}——疣猪兽 Brain AI 的松手时机；</li>
 *   <li>{@code ItemInHandRenderer}——玩家第一人称装填时的抖动与缩放动画；</li>
 *   <li>{@code HumanoidMobRenderer} / {@code IllagerRenderer} / {@code PiglinRenderer}——生物装填时的手臂动作；</li>
 *   <li>物品模型属性 {@code minecraft:crossbow/pull}——所以 Ibx 弩的模型可以直接照抄原版 {@code items/crossbow.json}；</li>
 *   <li>原版弩自己的装填音效、拉满装填、松手判定。原版在这里结算的快速装填随之失效，
 *       改由附魔覆盖文件里的"减拉弦时间"接手。</li>
 * </ul>
 * <p>{@link IbxCrossbowItem} 自己的装填判断直接走 {@code RangedWeaponApi}，不经过本方法，无递归。</p>
 *
 * <p><b>二、接管原版弩（只对被接管的原版弩生效）。</b>Ibx 弩在自己的类里重写了这些行为，
 * 它调 super 进到这里时判断都不成立，直接放行。数值见 {@link VanillaRangedWeapons#CROSSBOW}。</p>
 * <ul>
 *   <li>伤害与暴击：{@code createProjectile} 出箭后写伤害快照，暴击标记改成按 critical_chance 掷骰
 *       （原版对玩家恒为暴击）。生物也经过这里，掠夺者、疣猪兽一并覆盖。</li>
 *   <li>箭速：{@code performShooting} 把生物固定的 1.6 换成武器箭速（烟花不动），同 Ibx 弩。</li>
 *   <li>副手禁用、自动射击、"自动发射"提示行。</li>
 * </ul>
 *
 * <p>本类继承 {@link ProjectileWeaponItem}（原版弩的父类）：原版弩没有声明 {@code appendHoverText}，
 * 这里补上的覆写要能 super 调用父类。</p>
 */
@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin extends ProjectileWeaponItem {
    private CrossbowItemMixin(Properties properties) {
        super(properties);
    }

    @ModifyReturnValue(method = "getChargeDuration", at = @At("RETURN"))
    private static int ibx_lib$useWeaponDrawTime(int original, ItemStack crossbow, LivingEntity user) {
        return RangedWeaponApi.isManagedCrossbow(crossbow) ? RangedWeaponApi.getFullDrawTicks(user, crossbow) : original;
    }

    /** 副手禁用：装填与发射都从 use 进，一并挡住（同 Ibx 弩）。 */
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void ibx_lib$denyOffhand(Level level, Player player, InteractionHand hand,
                                            CallbackInfoReturnable<InteractionResult> cir) {
        if (hand == InteractionHand.OFF_HAND && VanillaRangedWeapons.isManagedCrossbow(this)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    /**
     * 箭速换成武器自己的。玩家那路原版本来就传 3.15，不变；生物（{@code CrossbowAttackMob}）固定传 1.6，
     * 这里改成 3.15。装的是烟花火箭时保持原样。此刻弩里的弹药还没被清空，可以读。
     */
    @ModifyVariable(method = "performShooting", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float ibx_lib$weaponVelocity(float power, Level level, LivingEntity shooter, InteractionHand hand,
                                                 ItemStack weapon) {
        if (!VanillaRangedWeapons.isManagedCrossbow(weapon)) {
            return power;
        }
        ChargedProjectiles charged = weapon.get(DataComponents.CHARGED_PROJECTILES);
        boolean firework = charged != null && charged.contains(Items.FIREWORK_ROCKET);
        return firework ? power : VanillaRangedWeapons.CROSSBOW.arrowVelocity();
    }

    /**
     * 出箭后写伤害快照，并把暴击标记改成掷骰结果（本方法只在服务端跑）。
     * 原版在父类里按传入的 isCrit 打过标记，这里直接用掷骰结果覆盖，效果等同于改传参。
     */
    @ModifyReturnValue(method = "createProjectile", at = @At("RETURN"))
    private Projectile ibx_lib$applyRangedDamage(Projectile projectile, Level level, LivingEntity shooter,
                                                         ItemStack heldItem, ItemStack ammo, boolean isCrit) {
        if (projectile instanceof AbstractArrow arrow && VanillaRangedWeapons.isManagedCrossbow(this)) {
            arrow.setCritArrow(CriticalStrikeApi.roll(shooter));
            RangedWeaponApi.setAmmoRangedDamage(arrow, RangedWeaponApi.getRangedDamage(shooter, heldItem));
        }
        return projectile;
    }

    /**
     * 带 {@code auto_fire} 组件时装填完毕自动松手（同 Ibx 弩）。放在末尾：原版在这一 tick 的前面已经装好了弹。
     */
    @Inject(method = "onUseTick", at = @At("TAIL"))
    private void ibx_lib$autoFire(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining,
                                         CallbackInfo ci) {
        if (VanillaRangedWeapons.isManagedCrossbow(this)) {
            RangedWeaponApi.tickAutoFire(entity, stack, ticksRemaining);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        if (VanillaRangedWeapons.isManagedCrossbow(this)) {
            RangedWeaponItem.appendAutoFireTooltip(stack, tooltip);
        }
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
