package ibx.taverntales.lib.api;

import ibx.taverntales.lib.registry.AttachmentRegistry;
import ibx.taverntales.lib.registry.AttributeRegistry;
import ibx.taverntales.lib.registry.DataComponentRegistry;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.EventHooks;

import java.util.Optional;

/**
 * 远程武器系统的读数入口：满蓄 tick、远程伤害、箭上的伤害快照、自动射击。
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

    /** 这把武器是否开了自动射击（{@code auto_fire} 组件为 true）。没有组件视为 false。 */
    public static boolean isAutoFire(ItemStack weapon) {
        return weapon.getOrDefault(DataComponentRegistry.AUTO_FIRE.get(), false);
    }

    /**
     * 自动射击的每 tick 判定，由弓弩的 {@code onUseTick} 调用：开了自动射击且已到满蓄 tick，
     * 就当作玩家此刻松开了右键——弓射出、弩装填。之后客户端发现不再使用物品，只要右键还按着，
     * 原版的按住重复使用逻辑会自动再次拉弓，于是按住即连发；弩装填好后同样由那套逻辑扣扳机。
     *
     * <p><b>只对玩家生效</b>：生物的拉弓与射击由 AI 目标自己掌控（拉满后先 stopUsingItem 再射），
     * 这里替它停用会让它重新拉弓、永远射不出去。</p>
     *
     * <p>服务端与客户端都会调用，与手动松手一致：客户端那次只做预测
     * （弓不射、弩本地先装上），真正的发射与同步由服务端负责。</p>
     *
     * <p>这里复刻了 {@code LivingEntity#releaseUsingItem}，但<b>去掉了它末尾的 updatingUsingItem()</b>：
     * 那一步会再跑一遍 onUseTick、又回到本方法，无限递归直到栈溢出。本方法本身就在 onUseTick 里，
     * 这一 tick 已经算过了，不需要补。</p>
     *
     * @param remainingTicks {@code onUseTick} 收到的剩余使用 tick
     * @return 是否已释放
     */
    public static boolean tickAutoFire(LivingEntity shooter, ItemStack weapon, int remainingTicks) {
        if (!(shooter instanceof Player player) || !isAutoFire(weapon)) {
            return false;
        }
        int elapsed = weapon.getUseDuration(shooter) - remainingTicks;
        if (elapsed < getFullDrawTicks(shooter, weapon)) {
            return false;
        }
        if (!EventHooks.onUseItemStop(shooter, weapon, remainingTicks)) {
            ItemStack copy = weapon.copy();
            weapon.releaseUsing(shooter.level(), shooter, remainingTicks);
            if (weapon.isEmpty()) {
                EventHooks.onPlayerDestroyItem(player, copy, shooter.getUsedItemHand());
            }
        }
        shooter.stopUsingItem();
        return true;
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
