package ibx.lib.compat.jade;

import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade 插件（Jade 按 {@link WailaPlugin} 注解找到它，没装 Jade 时不会加载）：生物提示框里接着护甲显示魔法抗性
 * （{@link SpellResistanceProvider}）。
 */
@WailaPlugin
public final class IbxJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(SpellResistanceProvider.INSTANCE, LivingEntity.class);
    }
}
