package ibx.lib.compat.jade;

import ibx.lib.config.ClientConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import snownee.jade.api.config.IWailaConfig;

/**
 * Jade 准星对着 {@code c:bosses} 里的生物时会再显示一遍血量，和 Boss 血条重复，
 * 所以第一次进世界时替玩家把 Jade 的「显示Boss」关掉一次（对着 Boss 时整个不弹提示框），记在客户端配置里
 * （{@link ClientConfig#JADE_BOSSES_HIDDEN_ONCE}），之后玩家在 Jade 里重新打开就不再改。
 *
 * <p>Jade 先调插件、后建配置，插件里还改不了它的设置，所以等进世界时再改。</p>
 *
 * 引用了 Jade 的类，只在客户端、装了 Jade 时注册（先过 {@code JadeCompat.isLoaded()}）。
 */
public final class JadeBossHiding {
    private JadeBossHiding() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(JadeBossHiding::onLoggingIn);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!ClientConfig.SPEC.isLoaded() || ClientConfig.get(ClientConfig.JADE_BOSSES_HIDDEN_ONCE)) {
            return;
        }
        IWailaConfig config = IWailaConfig.get();
        config.general().setDisplayBosses(false);
        config.save();
        ClientConfig.JADE_BOSSES_HIDDEN_ONCE.set(true);
        ClientConfig.JADE_BOSSES_HIDDEN_ONCE.save();
    }
}
