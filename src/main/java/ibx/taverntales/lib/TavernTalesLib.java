package ibx.taverntales.lib;

import com.mojang.logging.LogUtils;
import ibx.taverntales.lib.command.ManaCommand;
import ibx.taverntales.lib.command.SpellCommand;
import ibx.taverntales.lib.config.Config;
import ibx.taverntales.lib.config.StartupConfig;
import ibx.taverntales.lib.event.DamageHandler;
import ibx.taverntales.lib.event.HealHandler;
import ibx.taverntales.lib.event.ManaHandler;
import ibx.taverntales.lib.item.ranged_weapon.VanillaRangedWeapons;
import ibx.taverntales.lib.registry.AttachmentRegistry;
import ibx.taverntales.lib.registry.AttributeRegistry;
import ibx.taverntales.lib.registry.DataComponentRegistry;
import ibx.taverntales.lib.registry.ItemRegistry;
import ibx.taverntales.lib.spell.SpellCasting;
import ibx.taverntales.lib.spell.SpellRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(TavernTalesLib.MODID)
public class TavernTalesLib {
    public static final String MODID = "taverntales_l1b";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TavernTalesLib(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        // 启动配置注册即加载，下面的默认组件事件要读它，必须先注册。
        modContainer.registerConfig(ModConfig.Type.STARTUP, StartupConfig.SPEC);

        // MOD 总线：注册表、属性挂载、创造标签。
        AttributeRegistry.register(modEventBus);
        AttachmentRegistry.register(modEventBus);
        DataComponentRegistry.register(modEventBus);
        ItemRegistry.register(modEventBus);
        SpellRegistries.register(modEventBus);
        modEventBus.addListener(VanillaRangedWeapons::onModifyDefaultComponents);

        // GAME 总线：统一的伤害、治疗、法力与施法结算，以及法力、法术指令。
        DamageHandler.register();
        HealHandler.register();
        ManaHandler.register();
        SpellCasting.register();
        ManaCommand.register();
        SpellCommand.register();
        LOGGER.info("{} loaded", MODID);
    }
}
