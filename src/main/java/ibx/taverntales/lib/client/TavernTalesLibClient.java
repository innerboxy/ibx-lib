package ibx.taverntales.lib.client;

import ibx.taverntales.lib.TavernTalesLib;
import ibx.taverntales.lib.client.hud.HudPreviewCommand;
import ibx.taverntales.lib.client.hud.TavernHud;
import ibx.taverntales.lib.config.ClientConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 客户端入口：只在客户端加载，负责客户端配置和客户端的 MOD / GAME 总线监听。
 */
@Mod(value = TavernTalesLib.MODID, dist = Dist.CLIENT)
public final class TavernTalesLibClient {
    public TavernTalesLibClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        modEventBus.addListener(RangedWeaponModelProperties::onRegisterRangeSelectProperties);
        // 最低优先级：等其他模组先注册完自己的客户端扩展，再给剩下的 Tt 弩补上，避免重复注册报错。
        modEventBus.addListener(EventPriority.LOWEST, CrossbowArmPose::onRegisterClientExtensions);

        // HUD：快捷栏右侧的属性块、吟唱条、蓝条、经验条正中的等级数字；隐藏原版护甲行
        modEventBus.addListener(TavernHud::onRegisterGuiLayers);
        NeoForge.EVENT_BUS.addListener(TavernHud::onClientTick);
        // 吟唱条预览指令，法术系统接上以后删掉
        NeoForge.EVENT_BUS.addListener(HudPreviewCommand::onRegisterClientCommands);
        NeoForge.EVENT_BUS.addListener(HudPreviewCommand::onClientTick);
    }
}
