package ibx.taverntales.lib.client;

import ibx.taverntales.lib.TavernTalesLib;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * 客户端入口：只在客户端加载，负责挂客户端的 MOD 总线监听。
 */
@Mod(value = TavernTalesLib.MODID, dist = Dist.CLIENT)
public final class TavernTalesLibClient {
    public TavernTalesLibClient(IEventBus modEventBus) {
        modEventBus.addListener(RangedWeaponModelProperties::onRegisterRangeSelectProperties);
        // 最低优先级：等其他模组先注册完自己的客户端扩展，再给剩下的 Tt 弩补上，避免重复注册报错。
        modEventBus.addListener(EventPriority.LOWEST, CrossbowArmPose::onRegisterClientExtensions);
    }
}
