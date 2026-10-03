package ibx.taverntales.lib.client.spell;

import ibx.taverntales.lib.config.ClientConfig;
import ibx.taverntales.lib.registry.TagRegistry;
import ibx.taverntales.lib.spell.SpellCasting;
import ibx.taverntales.lib.spell.SpellEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

import java.util.Arrays;

/**
 * 右键施法的「切换」操作：吟唱、引导法术可以在 选项 → 控制 里各自改成按一下右键开始、再按一下结束，不用一直按住。
 * 吟唱默认切换、引导默认按住；蓄力始终按住，松手才放出。
 *
 * <p>只改客户端什么时候松手：原版每 tick 看右键没按着就停用物品（发松手包给服务端），切换模式下这一步跳过
 * （{@code mixin.MinecraftMixin}），改成读条中再按一下右键时松手。服务端收到的还是同一个松手，读条、取消都不用改；
 * 吟唱读完、引导蓝尽、被打断、切物品时服务端照常停用物品。</p>
 *
 * <p>客户端不预测施法状态，读条是服务端开始后同步过来的。服务端拒绝了（法术自己的条件不满足等）就一直不会有读条，
 * 等 {@link #START_GRACE_TICKS} 还没有就自动松手，免得一直举着手减速；读条结束后（服务端会停用物品）也退回按住。</p>
 *
 * <p>打开任何界面（背包、聊天、暂停）都退回按住：关界面时右键没按着，原版就松手取消，和按住模式一样。
 * 原版的「使用：切换」打开时所有物品都已经是切换，这里不再插手。</p>
 */
public final class SpellCastInput {
    private SpellCastInput() {}

    private static final Component TOGGLE = Component.translatable("options.key.toggle");
    private static final Component HOLD = Component.translatable("options.key.hold");

    /** 右键开始后最多等多久服务端的读条同步过来（2 秒，留足延迟）。 */
    private static final int START_GRACE_TICKS = 40;

    /** 这次右键施法是按切换模式开始的：右键松开也不停，等再按一下。 */
    private static boolean toggled;
    /** 服务端的读条已经同步过来了。 */
    private static boolean castSeen;
    /** 开始后过了几 tick 还没看到读条。 */
    private static int waitTicks;

    /** 原版每 tick 右键没按着就松手，返回 true 时跳过（{@code mixin.MinecraftMixin}）。 */
    public static boolean keepUsing() {
        return toggled;
    }

    /** 本地玩家开始用施法物品（右键施法挂上了"使用物品"）：按这次放的法术类型和设置决定是不是切换模式。 */
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.getEntity().level().isClientSide() || event.getEntity() != mc.player
                || !event.getItem().is(TagRegistry.CAN_CAST_SPELL) || mc.options.toggleUse().get()) {
            return;
        }
        SpellEntry entry = SpellCasting.getSelectedSpell(mc.player);
        toggled = entry != null && switch (entry.value().castType()) {
            case CAST -> ClientConfig.get(ClientConfig.TOGGLE_CAST_SPELL);
            case CHANNEL -> ClientConfig.get(ClientConfig.TOGGLE_CHANNEL_SPELL);
            default -> false;
        };
        castSeen = false;
        waitTicks = 0;
    }

    /**
     * 每 tick 开头（原版处理按键之前）：读条中又按了一下右键就松手；服务端一直没开始读条也松手；
     * 已经不在用物品、读条结束了、开着界面时退回按住。
     */
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (!toggled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || !player.isUsingItem() || mc.screen != null || mc.getOverlay() != null) {
            toggled = false;
            return;
        }
        if (SpellCasting.getCurrentCast(player) != null) {
            castSeen = true;
        } else if (castSeen) {
            toggled = false;
            return;
        } else if (++waitTicks > START_GRACE_TICKS) {
            toggled = false;
            mc.gameMode.releaseUsingItem(player);
            return;
        }
        if (mc.options.keyUse.consumeClick()) {
            while (mc.options.keyUse.consumeClick()) {
                // 同一 tick 里连按几下也只算一次松手，剩下的不留给原版（否则会马上再放一次）
            }
            toggled = false;
            mc.gameMode.releaseUsingItem(player);
        }
    }

    /** 控制设置页的选项：插在原版「使用：切换 / 按住」后面，正好凑成一行（{@code mixin.ControlsScreenMixin}）。 */
    public static OptionInstance<?>[] addControlOptions(Options options, OptionInstance<?>[] vanilla) {
        int at = Arrays.asList(vanilla).indexOf(options.toggleUse()) + 1;
        if (at == 0) {
            at = vanilla.length;
        }
        OptionInstance<?>[] all = new OptionInstance<?>[vanilla.length + 2];
        System.arraycopy(vanilla, 0, all, 0, at);
        all[at] = option("options.taverntales_l1b.toggle_cast_spell", ClientConfig.TOGGLE_CAST_SPELL);
        all[at + 1] = option("options.taverntales_l1b.toggle_channel_spell", ClientConfig.TOGGLE_CHANNEL_SPELL);
        System.arraycopy(vanilla, at, all, at + 2, vanilla.length - at);
        return all;
    }

    /** 「切换 / 按住」开关，值存在客户端配置里（每次打开设置页现读，改了立即写回文件）。 */
    private static OptionInstance<Boolean> option(String key, ModConfigSpec.BooleanValue value) {
        return new OptionInstance<>(key, OptionInstance.cachedConstantTooltip(Component.translatable(key + ".tooltip")),
                (caption, on) -> on ? TOGGLE : HOLD, OptionInstance.BOOLEAN_VALUES, ClientConfig.get(value), on -> {
                    if (ClientConfig.SPEC.isLoaded()) {
                        value.set(on);
                        value.save();
                    }
                });
    }
}
