package ibx.taverntales.lib.attribute;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.common.extensions.IAttributeExtension;
import org.jspecify.annotations.Nullable;

/**
 * 带单位的数值属性：加法修饰符的数值套一个语言键显示（例如 {@code "%s/秒"}），百分比修饰符照旧显示百分数。
 *
 * <p>与 {@link WeaponAttribute} 的区别是没有"武器基础值"那一套，给不由武器提供的属性用（如法力回复效率）。</p>
 */
public class UnitAttribute extends RangedAttribute {
    private final String valueKey;

    /**
     * @param valueKey 数值的语言键，含一个 %s 占位
     */
    public UnitAttribute(String descriptionId, double defaultValue, double min, double max, String valueKey) {
        super(descriptionId, defaultValue, min, max);
        this.valueKey = valueKey;
    }

    @Override
    public MutableComponent toValueComponent(AttributeModifier.@Nullable Operation op, double value, TooltipFlag flag) {
        if (IAttributeExtension.isNullOrAddition(op)) {
            return Component.translatable(this.valueKey, IAttributeExtension.FORMAT.format(value));
        }
        return super.toValueComponent(op, value, flag);
    }
}
