package ibx.lib.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** 本模组的网络包注册。版本号改了包格式就加一（两端版本不同时拒绝进服）。 */
public final class LibNetwork {
    private LibNetwork() {}

    private static final String VERSION = "1";

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(LibNetwork::onRegisterPayloadHandlers);
    }

    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION)
                .playToServer(SelectSpellPayload.TYPE, SelectSpellPayload.STREAM_CODEC, SelectSpellPayload::handle)
                .playToServer(ShieldBashPayload.TYPE, ShieldBashPayload.STREAM_CODEC, ShieldBashPayload::handle);
    }
}
