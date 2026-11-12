package ibx.lib.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** 本模组的网络包注册。版本号改了包格式就加一（两端版本不同时拒绝进服）。 */
public final class LibNetwork {
    private LibNetwork() {}

    private static final String VERSION = "4";

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(LibNetwork::onRegisterPayloadHandlers);
    }

    /** 发给客户端的包只在这里登记格式，处理器由用到它的客户端类各自注册（{@code RegisterClientPayloadHandlersEvent}）。 */
    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION)
                .playToServer(SelectSpellPayload.TYPE, SelectSpellPayload.STREAM_CODEC, SelectSpellPayload::handle)
                .playToServer(PickSpellsPayload.TYPE, PickSpellsPayload.STREAM_CODEC, PickSpellsPayload::handle)
                .playToServer(ShieldBashPayload.TYPE, ShieldBashPayload.STREAM_CODEC, ShieldBashPayload::handle)
                .playToServer(TargetModesPayload.TYPE, TargetModesPayload.STREAM_CODEC, TargetModesPayload::handle)
                .playToClient(TargetIndicatorPayload.TYPE, TargetIndicatorPayload.STREAM_CODEC)
                .playToClient(CastRejectedPayload.TYPE, CastRejectedPayload.STREAM_CODEC)
                .playToClient(GroundWarningPayload.TYPE, GroundWarningPayload.STREAM_CODEC)
                .playToClient(BlockWavePayload.TYPE, BlockWavePayload.STREAM_CODEC);
    }
}
