package ibx.lib.attribute;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.TooltipFlag;

/**
 * 开关属性：默认 0、范围 0~1，总值大于等于 1 视为开启。装备用 ADD_VALUE 挂 +1 打开。
 *
 * <p>tooltip 不显示数值：正的加法修饰符只显示属性名（"法术可暴击"），而不是"+1 法术可暴击"。
 * 单条、合并、药水效果的 tooltip 都经过 {@link #toComponent}，改这一处就够了。
 * 负数、百分比修饰符对开关没有意义，照父类显示成数字，方便发现写错的数据。</p>
 */
public class FlagAttribute extends RangedAttribute {
    public FlagAttribute(String descriptionId) {
        super(descriptionId, 0.0D, 0.0D, 1.0D);
    }

    @Override
    public MutableComponent toComponent(AttributeModifier modifier, TooltipFlag flag) {
        if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE && modifier.amount() > 0) {
            return Component.translatable(this.getDescriptionId())
                    .withStyle(this.getStyle(true))
                    .append(this.getDebugInfo(modifier, flag));
        }
        return super.toComponent(modifier, flag);
    }
}
