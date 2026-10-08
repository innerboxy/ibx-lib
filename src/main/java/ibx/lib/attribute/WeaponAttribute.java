package ibx.lib.attribute;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.common.extensions.IAttributeExtension;
import org.jspecify.annotations.Nullable;

/**
 * 远程武器用的属性：自带"基础修饰符 id"，让武器上挂的数值在 tooltip 里渲染成原版剑那样的
 * 深绿合并行（"8 远程伤害"），而不是蓝色的"+8 远程伤害"。
 * 识别逻辑在 NeoForge 的 {@link IAttributeExtension#getBaseId()} 与 {@code AttributeUtil.applyTextFor}：
 * 物品上 id 等于 {@link #getBaseId()} 的 ADD_VALUE 修饰符被视为"武器基础值"，加上实体基础值后显示，
 * 其他来源的同属性修饰符自动合并进去。
 *
 * <p>可选地给数值套一个语言键（例如 {@code "%s秒"}），用于拉弓时间这种带单位的属性。</p>
 */
public class WeaponAttribute extends RangedAttribute {
    private final Identifier baseId;
    private final @Nullable String valueKey;

    /**
     * @param baseId   基础修饰符 id。武器用这个 id 挂的 ADD_VALUE 修饰符会被当作"武器基础值"显示。
     * @param valueKey 数值的语言键（一个 %s 占位），null 表示直接显示数字。
     *                 只作用于加法修饰符与基础值，百分比修饰符照旧显示百分数。
     */
    public WeaponAttribute(String descriptionId, double defaultValue, double min, double max,
                           Identifier baseId, @Nullable String valueKey) {
        super(descriptionId, defaultValue, min, max);
        this.baseId = baseId;
        this.valueKey = valueKey;
    }

    @Override
    public Identifier getBaseId() {
        return this.baseId;
    }

    @Override
    public MutableComponent toValueComponent(AttributeModifier.@Nullable Operation op, double value, TooltipFlag flag) {
        if (this.valueKey != null && IAttributeExtension.isNullOrAddition(op)) {
            return Component.translatable(this.valueKey, IAttributeExtension.FORMAT.format(value));
        }
        return super.toValueComponent(op, value, flag);
    }

    @Override
    public MutableComponent toBaseComponent(double value, double entityBase, boolean merged, TooltipFlag flag) {
        MutableComponent vanilla = super.toBaseComponent(value, entityBase, merged, flag);
        if (this.valueKey == null) {
            return vanilla;
        }
        // 只把数值换成带单位的写法，其余照搬父类：高级 tooltip 的 "[基础值 + 物品加值]" 调试信息
        // 是父类以子组件形式追加的，这里原样搬过来，免得带单位的属性反而少一截信息。
        MutableComponent comp = Component.translatable("attribute.modifier.equals.0",
                Component.translatable(this.valueKey, IAttributeExtension.FORMAT.format(value)),
                Component.translatable(this.getDescriptionId()));
        vanilla.getSiblings().forEach(comp::append);
        return comp;
    }
}
