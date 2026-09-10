package ibx.taverntales.lib;

import com.mojang.logging.LogUtils;
import ibx.taverntales.lib.config.Config;
import ibx.taverntales.lib.event.DamageHandler;
import ibx.taverntales.lib.event.HealHandler;
import ibx.taverntales.lib.registry.AttachmentRegistry;
import ibx.taverntales.lib.registry.AttributeRegistry;
import ibx.taverntales.lib.registry.ItemRegistry;
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

        // MOD 总线：注册表、属性挂载、创造标签。
        AttributeRegistry.register(modEventBus);
        AttachmentRegistry.register(modEventBus);
        ItemRegistry.register(modEventBus);

        // GAME 总线：统一的伤害与治疗结算。
        DamageHandler.register();
        HealHandler.register();
        LOGGER.info("{} loaded", MODID);
    }
}
