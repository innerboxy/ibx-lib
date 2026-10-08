package ibx.lib.client;

import ibx.lib.IbxLib;
import ibx.lib.client.hud.TavernHud;
import ibx.lib.client.spell.CastAnimations;
import ibx.lib.client.spell.IsCastingProperty;
import ibx.lib.client.spell.SpellCastInput;
import ibx.lib.client.spell.SpellIcons;
import ibx.lib.client.spell.SpellItemTooltip;
import ibx.lib.client.spell.SpellWheel;
import ibx.lib.config.ClientConfig;
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
@Mod(value = IbxLib.MODID, dist = Dist.CLIENT)
public final class IbxLibClient {
    public IbxLibClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        modEventBus.addListener(RangedWeaponModelProperties::onRegisterRangeSelectProperties);
        // 最低优先级：等其他模组先注册完自己的客户端扩展，再给剩下的 Ibx 弩补上，避免重复注册报错。
        modEventBus.addListener(EventPriority.LOWEST, CrossbowArmPose::onRegisterClientExtensions);

        // HUD：快捷栏旁的属性块、副手栏外侧的当前法术、吟唱条、蓝条、经验条正中的等级数字；隐藏原版护甲行
        modEventBus.addListener(TavernHud::onRegisterGuiLayers);
        NeoForge.EVENT_BUS.addListener(TavernHud::onClientTick);
        // 法术轮盘：按住轮盘键弹出、左键点选；法术图标资源重载时重新找
        modEventBus.addListener(SpellWheel::onRegisterKeyMappings);
        modEventBus.addListener(SpellWheel::onRegisterGuiLayers);
        modEventBus.addListener(SpellIcons::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(SpellWheel::onClientTick);
        NeoForge.EVENT_BUS.addListener(SpellWheel::onMouseButton);
        NeoForge.EVENT_BUS.addListener(SpellWheel::onMouseScroll);
        // 右键施法的切换模式（吟唱、引导可以按一下开始、再按一下结束），开关在 选项 → 控制
        NeoForge.EVENT_BUS.addListener(SpellCastInput::onUseItemStart);
        NeoForge.EVENT_BUS.addListener(SpellCastInput::onClientTick);
        // 法术容器的物品提示：名字下面列出提供的法术
        NeoForge.EVENT_BUS.addListener(SpellItemTooltip::onItemTooltip);
        // 玩家施法动作（Player Animation Library），以及法典施法时翻开用的物品模型条件 ibx_lib:casting
        CastAnimations.register();
        modEventBus.addListener(IsCastingProperty::onRegister);
        // 换颜色的火（翠焰的绿火）：身上的火苗、第一人称屏幕上的火
        modEventBus.addListener(CustomFireRendering::onRegisterRenderStateModifiers);
        NeoForge.EVENT_BUS.addListener(CustomFireRendering::onRenderBlockScreenEffect);
    }
}
