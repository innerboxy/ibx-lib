package ibx.lib.client.spell;

import ibx.lib.config.ClientConfig;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;

/**
 * 选项 → 控制 里本模组的施法开关（{@code mixin.ControlsScreenMixin}），插在原版「使用：切换 / 按住」后面：
 * 吟唱法术、引导法术的「切换 / 按住」（{@link SpellCastInput}），法术轮盘的「必须点击 / 可以不点击」（{@link SpellWheel}）。
 * 值都存在客户端配置里，每次打开设置页现读，改了立即写回文件。
 */
public final class SpellControlOptions {
    private SpellControlOptions() {}

    private static final String KEY = "options.ibx_lib.";
    private static final Component TOGGLE = Component.translatable("options.key.toggle");
    private static final Component HOLD = Component.translatable("options.key.hold");
    private static final Component WHEEL_RELEASE = Component.translatable(KEY + "spell_wheel_select.release");
    private static final Component WHEEL_CLICK = Component.translatable(KEY + "spell_wheel_select.click");

    public static OptionInstance<?>[] addTo(Options options, OptionInstance<?>[] vanilla) {
        OptionInstance<?>[] ours = {
                option("toggle_cast_spell", ClientConfig.TOGGLE_CAST_SPELL, TOGGLE, HOLD),
                option("toggle_channel_spell", ClientConfig.TOGGLE_CHANNEL_SPELL, TOGGLE, HOLD),
                option("spell_wheel_select", ClientConfig.WHEEL_SELECT_ON_RELEASE, WHEEL_RELEASE, WHEEL_CLICK)};
        int at = Arrays.asList(vanilla).indexOf(options.toggleUse()) + 1;
        if (at == 0) {
            at = vanilla.length;
        }
        OptionInstance<?>[] all = new OptionInstance<?>[vanilla.length + ours.length];
        System.arraycopy(vanilla, 0, all, 0, at);
        System.arraycopy(ours, 0, all, at, ours.length);
        System.arraycopy(vanilla, at, all, at + ours.length, vanilla.length - at);
        return all;
    }

    /** 两个取值的开关：{@code on} / {@code off} 是 true / false 时按钮上显示的字。 */
    private static OptionInstance<Boolean> option(String name, ModConfigSpec.BooleanValue value, Component on, Component off) {
        return new OptionInstance<>(KEY + name, OptionInstance.cachedConstantTooltip(Component.translatable(KEY + name + ".tooltip")),
                (caption, enabled) -> enabled ? on : off, OptionInstance.BOOLEAN_VALUES, ClientConfig.get(value), enabled -> {
                    if (ClientConfig.SPEC.isLoaded()) {
                        value.set(enabled);
                        value.save();
                    }
                });
    }
}
