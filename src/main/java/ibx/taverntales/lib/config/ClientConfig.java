package ibx.taverntales.lib.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 本模组的客户端配置（{@code config/taverntales_l1b-client.toml}），只影响本机的显示，不同步。
 *
 * <p>都是每帧现读，游戏里改了立即生效。配置还没加载时（极早期）按默认值算。</p>
 */
public final class ClientConfig {
    private ClientConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue VANILLA_HUD = BUILDER
            .comment("是否完全使用原版 HUD。",
                    "false = 心形 / 鸡腿换成生命条、饥饿条，加上属性块、吟唱条、蓝条，隐藏原版护甲行，等级数字挪到经验条正中；",
                    "true  = 全部原版。和其他改 HUD 的模组冲突时可以打开。")
            .define("vanillaHud", false);

    public static final ModConfigSpec.BooleanValue SHOW_STATS_ROW = BUILDER
            .comment("是否在快捷栏右侧显示属性（护甲、法抗、韧性 | 力量、远程伤害、法强）。")
            .define("showStatsRow", true);

    public static final ModConfigSpec.BooleanValue ALWAYS_SHOW_MANA_BAR = BUILDER
            .comment("蓝条是否常驻。",
                    "false = 蓝不满时显示，回满 3 秒后淡出；true = 一直显示。")
            .define("alwaysShowManaBar", false);

    public static final ModConfigSpec.DoubleValue SPELL_WHEEL_SCALE = BUILDER
            .comment("法术轮盘的大小倍数，以\"刚好放得下 16×16 图标\"为 1。",
                    "屏幕放不下时自动缩小，但不会小于 1。")
            .defineInRange("spellWheelScale", 2.3D, 1.0D, 4.0D);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean vanillaHud() {
        return SPEC.isLoaded() && VANILLA_HUD.get();
    }

    public static boolean showStatsRow() {
        return !SPEC.isLoaded() || SHOW_STATS_ROW.get();
    }

    public static double spellWheelScale() {
        return SPEC.isLoaded() ? SPELL_WHEEL_SCALE.get() : SPELL_WHEEL_SCALE.getDefault();
    }

    public static boolean alwaysShowManaBar() {
        return SPEC.isLoaded() && ALWAYS_SHOW_MANA_BAR.get();
    }
}
