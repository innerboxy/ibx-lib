package ibx.taverntales.lib.api;

import ibx.taverntales.lib.registry.AttachmentRegistry;
import ibx.taverntales.lib.registry.AttributeRegistry;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * 远程武器系统的读数入口：满蓄 tick、远程伤害、箭上的伤害快照。
 * 物品类、命中结算、客户端拉弓动画谓词都从这里取值，保证各处口径一致。
 */
public final class RangedWeaponApi {
    private RangedWeaponApi() {}

    /** 满蓄 tick 的下限：属性总值再小也至少 1 tick，避免除零。 */
    public static final int MIN_FULL_DRAW_TICKS = 1;

    /**
     * 满蓄（弓）/ 装填（弩）所需 tick = draw_time ÷ draw_speed × 20，最少 {@link #MIN_FULL_DRAW_TICKS}。
     * 服务端与客户端都可调用（属性已同步）。
     */
    public static int getFullDrawTicks(LivingEntity shooter, ItemStack weapon) {
        // draw_speed 下限 0.01，不会除以零；最坏 1024 ÷ 0.01 × 20 ≈ 200 万 tick，int 装得下。
        double seconds = readAttribute(shooter, weapon, AttributeRegistry.DRAW_TIME)
                / readAttribute(shooter, weapon, AttributeRegistry.DRAW_SPEED);
        return Math.max(MIN_FULL_DRAW_TICKS, (int) Math.round(seconds * 20.0D));
    }

    /** 射手当前的远程伤害（ranged_damage 属性总值）。 */
    public static float getRangedDamage(LivingEntity shooter, ItemStack weapon) {
        return (float) readAttribute(shooter, weapon, AttributeRegistry.RANGED_DAMAGE);
    }

    /** 把算好的最终伤害写到弹射物上；命中时由 DamageHandler 读取并覆盖原版伤害。 */
    public static void setAmmoRangedDamage(Entity projectile, float damage) {
        projectile.setData(AttachmentRegistry.AMMO_RANGED_DAMAGE, damage);
    }

    /** 弹射物上记录的远程伤害；没有（原版弓弩射出的箭）则为空。不会顺手创建默认值。 */
    public static Optional<Float> getAmmoRangedDamage(Entity projectile) {
        return projectile.getExistingData(AttachmentRegistry.AMMO_RANGED_DAMAGE);
    }

    /**
     * 读属性总值。本模组的属性挂在所有生物上，正常都直接读属性——武器在主手时其修饰符已生效。
     * 没挂的实体理论上不存在，这里只防万一：属性默认值 + 武器自身的 ADD_VALUE 修饰符，
     * 再按属性上下限截断（否则 draw_speed 可能被算成 0 以下）。
     */
    private static double readAttribute(LivingEntity shooter, ItemStack weapon, Holder<Attribute> attribute) {
        if (shooter.getAttributes().hasAttribute(attribute)) {
            return shooter.getAttributeValue(attribute);
        }
        double[] sum = {attribute.value().getDefaultValue()};
        weapon.forEachModifier(EquipmentSlot.MAINHAND, (attr, modifier) -> {
            if (attr.equals(attribute) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                sum[0] += modifier.amount();
            }
        });
        return attribute.value().sanitizeValue(sum[0]);
    }
}
