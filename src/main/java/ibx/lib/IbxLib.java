package ibx.lib;

import com.mojang.logging.LogUtils;
import ibx.lib.api.CustomFire;
import ibx.lib.command.ManaCommand;
import ibx.lib.command.SpellCommand;
import ibx.lib.config.Config;
import ibx.lib.config.StartupConfig;
import ibx.lib.event.DamageHandler;
import ibx.lib.event.HealHandler;
import ibx.lib.event.ManaHandler;
import ibx.lib.item.vanilla.VanillaArmors;
import ibx.lib.item.vanilla.VanillaRangedWeapons;
import ibx.lib.network.LibNetwork;
import ibx.lib.registry.AttachmentRegistry;
import ibx.lib.registry.AttributeRegistry;
import ibx.lib.registry.DataComponentRegistry;
import ibx.lib.registry.SoundRegistry;
import ibx.lib.spell.SpellCasting;
import ibx.lib.spell.SpellRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(IbxLib.MODID)
public class IbxLib {
    public static final String MODID = "ibx_lib";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IbxLib(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        // 启动配置注册即加载，下面的默认组件事件要读它，必须先注册。
        modContainer.registerConfig(ModConfig.Type.STARTUP, StartupConfig.SPEC);

        // MOD 总线：注册表、属性挂载、网络包。
        AttributeRegistry.register(modEventBus);
        AttachmentRegistry.register(modEventBus);
        DataComponentRegistry.register(modEventBus);
        SpellRegistries.register(modEventBus);
        SoundRegistry.register(modEventBus);
        LibNetwork.register(modEventBus);
        modEventBus.addListener(VanillaRangedWeapons::onModifyDefaultComponents);
        modEventBus.addListener(VanillaArmors::onModifyDefaultComponents);

        // GAME 总线：统一的伤害、治疗、法力与施法结算，以及法力、法术指令。
        DamageHandler.register();
        HealHandler.register();
        ManaHandler.register();
        SpellCasting.register();
        CustomFire.register();
        ManaCommand.register();
        SpellCommand.register();
        LOGGER.info("{} loaded", MODID);
    }
}
