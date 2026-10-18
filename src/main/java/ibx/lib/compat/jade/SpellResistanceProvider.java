package ibx.lib.compat.jade;

import ibx.lib.IbxLib;
import ibx.lib.registry.AttributeRegistry;
import ibx.lib.util.AttributeUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.Element;
import snownee.jade.api.ui.JadeUI;

/**
 * Jade 生物提示框里的魔法抗性（{@code spell_resistance}）：接在 Jade 自己的护甲后面、同一行，画法照护甲
 * （{@link SpellResistanceElement}）；没有护甲那一项时单独一行；魔抗为 0 不显示。
 * 魔抗属性会同步给客户端，直接读客户端的值。Jade 的插件设置里可以关掉。
 */
public enum SpellResistanceProvider implements IEntityComponentProvider {
    INSTANCE;

    private static final Identifier UID = Identifier.fromNamespaceAndPath(IbxLib.MODID, "spell_resistance");

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof LivingEntity living)) return;
        float value = (float) AttributeUtil.read(living, AttributeRegistry.SPELL_RESISTANCE);
        if (value <= 0.0F) return;
        Element element = new SpellResistanceElement(value).tag(UID);
        if (tooltip.get(JadeIds.MC_ENTITY_ARMOR).isEmpty()) {
            tooltip.add(element);
        } else {
            // 护甲是上一个插件最后加的，在最后一行
            tooltip.append(JadeUI.spacer(4, 0));
            tooltip.append(element);
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    /** 紧跟在 Jade 的生命、护甲（-8000）后面，才接得上护甲那一行。 */
    @Override
    public int getDefaultPriority() {
        return -7999;
    }
}
