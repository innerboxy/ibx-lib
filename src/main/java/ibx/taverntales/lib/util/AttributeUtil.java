package ibx.taverntales.lib.util;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * 属性读数的公共方法。
 */
public final class AttributeUtil {
    private AttributeUtil() {}

    /**
     * 读实体某个属性的总值。本模组的属性挂在所有生物上（{@code AttributeRegistry.attachToLivingEntities}），正常都能直接读；
     * 这里只防万一：没挂该属性的实体直接问会经 AttributeSupplier 抛异常，改按属性的默认值算。
     */
    public static double read(LivingEntity entity, Holder<Attribute> attribute) {
        return entity.getAttributes().hasAttribute(attribute)
                ? entity.getAttributeValue(attribute)
                : attribute.value().getDefaultValue();
    }
}
