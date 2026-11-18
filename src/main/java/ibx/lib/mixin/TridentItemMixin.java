package ibx.lib.mixin;

import ibx.lib.api.CriticalStrikeApi;
import ibx.lib.api.RangedWeaponApi;
import ibx.lib.item.IbxTridentItem;
import ibx.lib.item.vanilla.VanillaRangedWeapons;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 接管原版三叉戟的投掷：行为与 {@link IbxTridentItem} 一致，数值见 {@link VanillaRangedWeapons#TRIDENT_RANGED_DAMAGE}。
 *
 * <p>原版 {@code releaseUsing} 出手前先把戟从手里分出去，之后主手的远程伤害修饰符就读不到了，伤害快照没有合适的插入点，
 * 所以同 {@code BowItemMixin} 整段接管：方法头判断是被接管的原版三叉戟就改走 {@link #ibx_lib$releaseUsing} 并取消原方法。
 * <b>那段代码是 {@link IbxTridentItem#releaseUsing} 的复刻</b>（只是掷出去的是原版的 {@link ThrownTrident}），改一边要同步另一边。
 * 记下的伤害写在戟的 {@code ammo_ranged_damage} 附加数据上，打中时由 {@code ThrownTridentMixin} 接着算。</p>
 *
 * <p>本类继承 {@link Item}（原版三叉戟的父类），这样才能直接用 {@code this} 当物品调统计、取使用时长。</p>
 */
@Mixin(TridentItem.class)
public abstract class TridentItemMixin extends Item {
    private TridentItemMixin(Properties properties) {
        super(properties);
    }

    /** 副手禁用：远程伤害只挂主手，副手掷出去读不到（同 Ibx 三叉戟）。 */
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void ibx_lib$denyOffhand(Level level, Player player, InteractionHand hand,
                                     CallbackInfoReturnable<InteractionResult> cir) {
        if (hand == InteractionHand.OFF_HAND && VanillaRangedWeapons.isManagedTrident(this)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void ibx_lib$takeOverRelease(ItemStack stack, Level level, LivingEntity entity, int remainingTime,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (VanillaRangedWeapons.isManagedTrident(this)) {
            cir.setReturnValue(this.ibx_lib$releaseUsing(stack, level, entity, remainingTime));
        }
    }

    /** {@link IbxTridentItem#releaseUsing} 的复刻，掷出去的是原版三叉戟实体。 */
    @Unique
    private boolean ibx_lib$releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
        if (!(entity instanceof Player player) || this.getUseDuration(stack, entity) - remainingTime < TridentItem.THROW_THRESHOLD_TIME) {
            return false;
        }
        float riptide = EnchantmentHelper.getTridentSpinAttackStrength(stack, player);
        if (riptide > 0.0F && !(player.isInWaterOrRain() && !player.isPassenger()) || stack.nextDamageWillBreak()) {
            return false;
        }
        Holder<SoundEvent> sound = EnchantmentHelper.pickHighestLevel(stack, EnchantmentEffectComponents.TRIDENT_SOUND)
                .orElse(SoundEvents.TRIDENT_THROW);
        player.awardStat(Stats.ITEM_USED.get(this));
        if (level instanceof ServerLevel serverLevel) {
            // 远程伤害、暴击都在戟还拿在手里时读：离手之后主手的修饰符就没了
            float rangedDamage = RangedWeaponApi.getRangedDamage(player, stack);
            stack.hurtWithoutBreaking(1, player);
            if (riptide == 0.0F) {
                boolean critical = CriticalStrikeApi.roll(player);
                ItemStack thrown = stack.consumeAndReturn(1, player);
                ThrownTrident trident = Projectile.spawnProjectileFromRotation((spawnLevel, owner, item) -> {
                    ThrownTrident created = new ThrownTrident(spawnLevel, owner, item);
                    RangedWeaponApi.setAmmoRangedDamage(created, rangedDamage);
                    created.setCritArrow(critical);
                    return created;
                }, serverLevel, thrown, player, 0.0F, TridentItem.PROJECTILE_SHOOT_POWER, 1.0F);
                if (player.hasInfiniteMaterials()) {
                    trident.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                }
                level.playSound(null, trident, sound.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
                return true;
            }
        }
        if (riptide > 0.0F) {
            float yRot = player.getYRot();
            float xRot = player.getXRot();
            float xd = -Mth.sin(yRot * Mth.DEG_TO_RAD) * Mth.cos(xRot * Mth.DEG_TO_RAD);
            float yd = -Mth.sin(xRot * Mth.DEG_TO_RAD);
            float zd = Mth.cos(yRot * Mth.DEG_TO_RAD) * Mth.cos(xRot * Mth.DEG_TO_RAD);
            float dist = Mth.sqrt(xd * xd + yd * yd + zd * zd);
            player.push(xd * riptide / dist, yd * riptide / dist, zd * riptide / dist);
            player.startAutoSpinAttack(20, 8.0F, stack);
            if (player.onGround()) {
                player.move(MoverType.SELF, new Vec3(0.0, 1.1999999F, 0.0));
            }
            level.playSound(null, player, sound.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
            return true;
        }
        return false;
    }
}
