package ibx.lib.client;

import ibx.lib.api.ShieldApi;
import ibx.lib.item.IbxHeavyShieldItem;
import ibx.lib.network.ShieldBashPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 盾击的左键：本地玩家主手拿着双手盾、按着右键期间，攻击键一律不交给原版和 Better Combat；
 * 盾举着且不在冷却时按下才发盾击的包（{@link ShieldBashPayload}），其余情况（冷却中、盾击后失效的 0.5 秒）没反应。
 * 在每 tick 开头（原版处理按键之前）看，同右键施法的切换模式。
 *
 * <p>光吃掉点击计数不够：BC 每 tick 用 {@code keyAttack.isDown()} 判断"按着攻击键"，盾击让盾放下以后
 * 左键多半还按着，它就会当普通攻击出招。所以把键状态也压下去，松开再按才恢复。</p>
 *
 * <p>没按右键、主手不是双手盾、副手举盾时什么都不做，左键还是普通攻击。</p>
 */
public final class ShieldInput {
    private ShieldInput() {}

    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null || mc.getOverlay() != null) {
            return;
        }
        if (!(player.getMainHandItem().getItem() instanceof IbxHeavyShieldItem)) {
            return;
        }
        boolean raised = ShieldApi.isRaisingHeavyShield(player);
        if (!raised && !mc.options.keyUse.isDown()) {
            return;
        }
        boolean clicked = mc.options.keyAttack.consumeClick();
        while (mc.options.keyAttack.consumeClick()) {
            // 同一 tick 里连按几下也只算一次
        }
        mc.options.keyAttack.setDown(false);
        if (clicked && raised && ShieldApi.isBashReady(player)) {
            ClientPacketDistributor.sendToServer(ShieldBashPayload.INSTANCE);
        }
    }
}
