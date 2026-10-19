package ibx.lib.client.target;

import ibx.lib.config.ClientConfig;
import ibx.lib.network.TargetModesPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 把两个选取时机设置（指向目标、地点目标）告诉服务端：进服时发一次，在 选项 → 控制 里改了再发（{@code SpellControlOptions}），
 * 客户端配置重载（模组配置界面里改了、配置文件被改了）也再发。选目标在服务端做，所以服务端要知道。
 */
public final class TargetModeSync {
    private TargetModeSync() {}

    /** GAME 总线。 */
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        send();
    }

    /**
     * MOD 总线：客户端配置重载了（模组配置界面关掉时保存、或者配置文件在外面被改了，FML 的文件监视都会发这个）。
     * 文件监视在别的线程上发事件，回主线程再发包。
     */
    public static void onConfigReloaded(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ClientConfig.SPEC) {
            Minecraft.getInstance().execute(TargetModeSync::send);
        }
    }

    /** 连着服务器才发。 */
    public static void send() {
        if (Minecraft.getInstance().getConnection() == null) {
            return;
        }
        ClientPacketDistributor.sendToServer(new TargetModesPayload(
                ClientConfig.get(ClientConfig.TARGET_UNIT_LOCK_AT_START),
                ClientConfig.get(ClientConfig.TARGET_POINT_LOCK_AT_START)));
    }
}
