package ibx.lib.client;

import ibx.lib.IbxLib;
import ibx.lib.client.attribute.AttributePanel;
import ibx.lib.client.creativetab.CreativeSubTabStrip;
import ibx.lib.client.ground.BlockWaveRendering;
import ibx.lib.client.ground.GroundWarningRendering;
import ibx.lib.client.hud.TavernHud;
import ibx.lib.client.spell.CastAnimations;
import ibx.lib.client.spell.IsCastingProperty;
import ibx.lib.client.spell.SpellCastInput;
import ibx.lib.client.spell.SpellIcons;
import ibx.lib.client.spell.SpellItemTooltip;
import ibx.lib.client.spell.SpellPickerInput;
import ibx.lib.client.spell.SpellWheel;
import ibx.lib.client.target.TargetIndicatorRendering;
import ibx.lib.client.target.TargetModeSync;
import ibx.lib.compat.jade.JadeCompat;
import ibx.lib.config.ClientConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 客户端入口：只在客户端加载，负责客户端配置和客户端的 MOD / GAME 总线监听。
 */
@Mod(value = IbxLib.MODID, dist = Dist.CLIENT)
public final class IbxLibClient {
    /** Player Animation Library 的模组 id：可选依赖，装了才注册施法、盾击动作。 */
    private static final String PLAYER_ANIMATION_LIBRARY = "player_animation_library";

    public IbxLibClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        // Player Animation Library 是可选依赖：没装时动作相关的类（CastAnimations、ShieldAnimations、PlayerAnimLayers）一个都不加载
        boolean playerAnimationsLoaded = ModList.get().isLoaded(PLAYER_ANIMATION_LIBRARY);
        // 模组列表里的「配置」按钮：NeoForge 自带的配置界面，游戏里直接改（名字见语言文件 ibx_lib.configuration.*，说明取配置注释）
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modEventBus.addListener(RangedWeaponModelProperties::onRegisterRangeSelectProperties);
        // 最低优先级：等其他模组先注册完自己的客户端扩展，再给剩下的 Ibx 弩补上，避免重复注册报错。
        modEventBus.addListener(EventPriority.LOWEST, CrossbowArmPose::onRegisterClientExtensions);

        // HUD：副手栏外侧的当前法术、吟唱条、蓝条、经验条正中的等级数字；隐藏原版护甲行
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
        // 服务端拒绝右键施法（没有目标等）时马上松手
        modEventBus.addListener(SpellCastInput::onRegisterPayloadHandlers);
        // 选法术界面：主手拿着 SpellPicker 物品蹲下 + 左键打开（右边列注册的全部法术还是身上的法术，由物品决定）
        NeoForge.EVENT_BUS.addListener(SpellPickerInput::onInteraction);
        // 法术容器的物品提示：名字下面列出提供的法术
        NeoForge.EVENT_BUS.addListener(SpellItemTooltip::onItemTooltip);
        // 玩家施法动作（Player Animation Library，可选：没装就不放动作），以及法典施法时翻开用的物品模型条件 ibx_lib:casting
        if (playerAnimationsLoaded) {
            CastAnimations.register();
        }
        modEventBus.addListener(IsCastingProperty::onRegister);
        // 着火：换颜色的火（翠焰的绿火）身上的火苗；第一人称屏幕上的火改成四周的边框（原版火、换色火都是）
        modEventBus.addListener(CustomFireRendering::onRegisterRenderStateModifiers);
        modEventBus.addListener(CustomFireRendering::onRegisterGuiLayers);
        modEventBus.addListener(CustomFireRendering::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(CustomFireRendering::onRenderBlockScreenEffect);
        NeoForge.EVENT_BUS.addListener(CustomFireRendering::onClientTick);
        // 选目标：读条时地上的提示（锁定的目标脚下的方形、范围的圆 / 扇形）；进服时和客户端配置重载时把选取时机设置告诉服务端
        TargetIndicatorRendering.register(modEventBus);
        // 地面预警（Boss 招式落点之类，同目标提示的画法）、地面起伏（浪经过的方块弹起来）
        GroundWarningRendering.register(modEventBus);
        BlockWaveRendering.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(TargetModeSync::onLoggingIn);
        modEventBus.addListener(TargetModeSync::onConfigReloaded);
        // 盾牌：主手举盾中的左键（发盾击的包，不交给原版）和盾击动作（盾击动作同样要 Player Animation Library）
        if (playerAnimationsLoaded) {
            ShieldAnimations.register();
        }
        NeoForge.EVENT_BUS.addListener(ShieldInput::onClientTick);
        // Jade（装了才有）：首次进世界关掉它的「显示Boss」；提示框里的魔抗由 Jade 插件 IbxJadePlugin 自己注册
        JadeCompat.register();
        // 背包左边的属性面板（人物框左下角的卷轴按钮开关）和它的设置界面
        AttributePanel.register(modEventBus);
        // 创造栏子栏：选中的创造栏建了子栏（CreativeSubTabs）时，创造模式背包左边一列分组图标
        CreativeSubTabStrip.register();
    }
}
