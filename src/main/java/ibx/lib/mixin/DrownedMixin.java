package ibx.lib.mixin;

import ibx.lib.api.RangedWeaponApi;
import ibx.lib.item.vanilla.VanillaRangedWeapons;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 溺尸掷出的原版三叉戟也带上伤害快照，伤害口径与玩家一致：掷出时读溺尸的远程伤害（主手原版三叉戟的 8 加上它身上的效果），
 * 打中时由 {@code ThrownTridentMixin} 接着算，射中玩家时的难度缩放由 {@link ibx.lib.event.DamageHandler} 补回。
 *
 * <p>只在溺尸主手拿的就是被接管的原版三叉戟时介入（原版在主手不是三叉戟时会临时造一把来掷，那把没有修饰符，照原版算）。
 * 不掷暴击，同骷髅射箭（见 {@code ProjectileUtilMixin}）。</p>
 *
 * @see VanillaRangedWeapons
 */
@Mixin(Drowned.class)
public abstract class DrownedMixin {
    @ModifyArg(method = "performRangedAttack", index = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileUsingShoot(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;DDDFF)Lnet/minecraft/world/entity/projectile/Projectile;"))
    private Projectile ibx_lib$recordDamage(Projectile trident) {
        Drowned self = (Drowned) (Object) this;
        ItemStack weapon = self.getMainHandItem();
        if (VanillaRangedWeapons.isManagedTrident(weapon)) {
            RangedWeaponApi.setAmmoRangedDamage(trident, RangedWeaponApi.getRangedDamage(self, weapon));
        }
        return trident;
    }
}
