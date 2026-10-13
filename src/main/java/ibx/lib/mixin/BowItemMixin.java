package ibx.lib.mixin;

import ibx.lib.api.CriticalStrikeApi;
import ibx.lib.api.RangedWeaponApi;
import ibx.lib.item.IbxBowItem;
import ibx.lib.item.general.IbxRangedWeaponItem;
import ibx.lib.item.vanilla.VanillaRangedWeapons;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Consumer;

/**
 * 接管原版弓：行为与 {@link IbxBowItem} 一致，数值见 {@link VanillaRangedWeapons#BOW}。
 *
 * <p>原版 {@code releaseUsing} 把二次蓄力曲线（除以 20）、箭速 {@code pow * 3.0}、满蓄才暴击、
 * 没有伤害参数全写在一个方法里，逐点改要四五处且伤害快照只能绕路反推，所以整段接管：
 * 在方法头判断是被接管的原版弓就改走 {@link #ibx_lib$releaseUsing} 并取消原方法。
 * <b>那段代码是 {@link IbxBowItem#releaseUsing} / {@code shootWithDamage} 的复刻</b>，改一边要同步另一边
 * （公式部分都在 {@link RangedWeaponApi}，两边共用）。</p>
 *
 * <p>本类继承 {@link ProjectileWeaponItem}（即原版弓的父类），这样才能直接调用 protected 的
 * {@code createProjectile} / {@code shootProjectile} / {@code draw}，并以 super 调用覆写父类方法。
 * 原版弓没有声明 {@code onUseTick} 与 {@code appendHoverText}，本类补上的这两个方法会成为弓的覆写；
 * 不是被接管的原版弓（其他模组的弓调 super 进来）一律交回父类，行为不变。</p>
 */
@Mixin(BowItem.class)
public abstract class BowItemMixin extends ProjectileWeaponItem {
    private BowItemMixin(Properties properties) {
        super(properties);
    }

    /** 副手禁用：修饰符只挂主手，副手射击读不到武器数值（同 Ibx 弓）。 */
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void ibx_lib$denyOffhand(Level level, Player player, InteractionHand hand,
                                            CallbackInfoReturnable<InteractionResult> cir) {
        if (hand == InteractionHand.OFF_HAND && VanillaRangedWeapons.isManagedBow(this)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void ibx_lib$takeOverRelease(ItemStack stack, Level level, LivingEntity entity, int timeLeft,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (VanillaRangedWeapons.isManagedBow(this)) {
            cir.setReturnValue(this.ibx_lib$releaseUsing(stack, level, entity, timeLeft));
        }
    }

    /** 带 {@code auto_fire} 组件时拉满自动射出（同 Ibx 弓）。 */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (VanillaRangedWeapons.isManagedBow(this)) {
            RangedWeaponApi.tickAutoFire(entity, stack, remainingTicks);
        } else {
            super.onUseTick(level, entity, stack, remainingTicks);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        if (VanillaRangedWeapons.isManagedBow(this)) {
            IbxRangedWeaponItem.appendAutoFireTooltip(stack, tooltip);
        }
        super.appendHoverText(stack, context, display, tooltip, flag);
    }

    /** {@link IbxBowItem#releaseUsing} 的复刻，箭速与发射门槛取原版弓的值。 */
    @Unique
    private boolean ibx_lib$releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        ItemStack ammoStack = player.getProjectile(stack);
        if (ammoStack.isEmpty()) {
            return false;
        }

        int charge = this.getUseDuration(stack, entity) - timeLeft;
        charge = EventHooks.onArrowLoose(stack, level, player, charge, true);
        if (charge < 0) {
            return false;
        }

        float pct = RangedWeaponApi.getChargePercent(charge, player, stack);
        if (pct < IbxBowItem.MIN_CHARGE) {
            return false; // 低于门槛：不发射、不消耗
        }

        List<ItemStack> projectiles = draw(stack, ammoStack, player);
        if (level instanceof ServerLevel serverLevel && !projectiles.isEmpty()) {
            float damage = RangedWeaponApi.getRangedDamage(player, stack) * pct;
            this.ibx_lib$shootWithDamage(serverLevel, player, player.getUsedItemHand(), stack, projectiles,
                    VanillaRangedWeapons.BOW.arrowVelocity() * pct, CriticalStrikeApi.roll(player), damage);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS,
                1.0F, 1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + pct * 0.5F);
        player.awardStat(Stats.ITEM_USED.get(this));
        return true;
    }

    /** {@code IbxBowItem#shootWithDamage} 的复刻（玩家射击，无指定目标、散布 1.0）。 */
    @Unique
    private void ibx_lib$shootWithDamage(ServerLevel level, LivingEntity shooter, InteractionHand hand,
                                                ItemStack weapon, List<ItemStack> projectileItems, float velocity,
                                                boolean isCrit, float damage) {
        float spread = EnchantmentHelper.processProjectileSpread(level, weapon, shooter, 0.0F);
        float step = projectileItems.size() == 1 ? 0.0F : 2.0F * spread / (float) (projectileItems.size() - 1);
        float offset = (float) ((projectileItems.size() - 1) % 2) * step / 2.0F;
        float sign = 1.0F;

        for (int i = 0; i < projectileItems.size(); i++) {
            ItemStack ammoStack = projectileItems.get(i);
            if (ammoStack.isEmpty()) {
                continue;
            }
            float angle = offset + sign * (float) ((i + 1) / 2) * step;
            sign = -sign;
            int index = i;

            Projectile projectile = this.createProjectile(level, shooter, weapon, ammoStack, isCrit);
            RangedWeaponApi.setAmmoRangedDamage(projectile, damage);
            Projectile.spawnProjectile(projectile, level, ammoStack,
                    spawned -> this.shootProjectile(shooter, spawned, index, velocity, 1.0F, angle, null));

            weapon.hurtAndBreak(this.getDurabilityUse(ammoStack), shooter, hand.asEquipmentSlot());
            if (weapon.isEmpty()) {
                break;
            }
        }
    }
}
