package ibx.lib.item.general;

import ibx.lib.registry.AttributeRegistry;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * 远程武器构造时给定的静态数值，并据此烤出物品属性修饰符：挂主手、用基础 id，
 * tooltip 上显示为原版剑那样的深绿基础值行。
 *
 * <p>运行时不要直接读远程伤害与拉弓时间：这两项一律通过
 * {@link ibx.lib.api.RangedWeaponApi} 读玩家属性，
 * 这样护甲、饰品、药水对属性的加减，以及按物品栈覆盖组件的数值才都能生效。
 * <b>箭速是例外</b>——它没有对应属性，就是物品上的一个定值，只影响弹道手感、不影响伤害，
 * 所以直接读 {@link #arrowVelocity()}。</p>
 */
public final class IbxRangedWeaponStats {
    private final float rangedDamage;
    private final float drawTime;
    private final float arrowVelocity;
    private final ItemAttributeModifiers modifiers;

    /**
     * @param rangedDamage          远程伤害
     * @param drawTime              满蓄 / 装填秒数；不大于 0 表示用武器类型的基准值
     * @param arrowVelocity         发射初速度；不大于 0 表示用武器类型的基准值
     * @param baselineDrawTime      武器类型的基准秒数（弓 1.0，弩 1.25）
     * @param baselineArrowVelocity 武器类型的基准初速度（弓 3.0，弩 3.15）
     */
    public IbxRangedWeaponStats(float rangedDamage, float drawTime, float arrowVelocity,
                             float baselineDrawTime, float baselineArrowVelocity) {
        this.rangedDamage = rangedDamage;
        this.drawTime = drawTime > 0.0F ? drawTime : baselineDrawTime;
        this.arrowVelocity = arrowVelocity > 0.0F ? arrowVelocity : baselineArrowVelocity;
        // 这里只存 Holder，不解析属性；物品注册早于属性注册，DeferredHolder 到时候自己会绑定。
        this.modifiers = ItemAttributeModifiers.builder()
                .add(AttributeRegistry.RANGED_DAMAGE,
                        new AttributeModifier(AttributeRegistry.BASE_RANGED_DAMAGE_ID, this.rangedDamage,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(AttributeRegistry.DRAW_TIME,
                        new AttributeModifier(AttributeRegistry.BASE_DRAW_TIME_ID, this.drawTime,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    public float rangedDamage() {
        return this.rangedDamage;
    }

    public float drawTime() {
        return this.drawTime;
    }

    /** 发射初速度。弓在此基础上再乘蓄力百分比。 */
    public float arrowVelocity() {
        return this.arrowVelocity;
    }

    /** 烤好的修饰符，供物品的 {@code getDefaultAttributeModifiers} 返回。 */
    public ItemAttributeModifiers modifiers() {
        return this.modifiers;
    }
}
