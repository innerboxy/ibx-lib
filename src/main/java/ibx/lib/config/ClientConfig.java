package ibx.lib.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 本模组的客户端配置（{@code config/ibx_lib-client.toml}），只影响本机的显示和操作，不同步。
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

    public static final ModConfigSpec.BooleanValue REPLACE_FIRE_OVERLAY = BUILDER
            .comment("第一人称着火时的屏幕效果。",
                    "true  = 换成屏幕四角的火焰边框（中间不挡视线），着火 / 熄灭时淡入淡出；",
                    "false = 原版镜头前的两大片火。")
            .define("replaceFireOverlay", true);

    public static final ModConfigSpec.DoubleValue SPELL_WHEEL_SCALE = BUILDER
            .comment("法术轮盘的大小倍数，以\"刚好放得下 16×16 图标\"为 1。",
                    "屏幕放不下时自动缩小，但不会小于 1。")
            .defineInRange("spellWheelScale", 2.3D, 1.0D, 4.0D);

    public static final ModConfigSpec.BooleanValue TOGGLE_CAST_SPELL = BUILDER
            .comment("吟唱法术的右键操作，游戏里在 选项 → 控制 里改。",
                    "true  = 切换：按一下右键开始，读完自动放出，读条中再按一下取消；",
                    "false = 按住：按住右键读条，松开取消。")
            .define("toggleCastSpell", true);

    public static final ModConfigSpec.BooleanValue TOGGLE_CHANNEL_SPELL = BUILDER
            .comment("引导法术的右键操作，游戏里在 选项 → 控制 里改。",
                    "true  = 切换：按一下右键开始，蓝耗尽或再按一下结束；",
                    "false = 按住：按住右键持续引导，松开结束。")
            .define("toggleChannelSpell", false);

    public static final ModConfigSpec.BooleanValue WHEEL_SELECT_ON_RELEASE = BUILDER
            .comment("法术轮盘怎么选法术，游戏里在 选项 → 控制 里改。",
                    "true  = 可以不点击：松开轮盘键时选中鼠标指着的那格（指着中间空白不改），轮盘打开时左键点选也照常有效；",
                    "false = 必须点击：只有左键点一格才选中，松开轮盘键只是关上。")
            .define("spellWheelSelectOnRelease", true);

    public static final ModConfigSpec.BooleanValue TARGET_UNIT_LOCK_AT_START = BUILDER
            .comment("指向型（锁一个生物的法术、物品）什么时候选目标，游戏里在 选项 → 控制 里改。",
                    "false = 生效时选取：开始时选中的只当后备，读完 / 松手时按当时的准星重新选，没指着才用后备；",
                    "true  = 开始时锁定：开始时选中就锁死，读条中目标死了、走远了就取消。")
            .define("targetUnitLockAtStart", false);

    public static final ModConfigSpec.BooleanValue TARGET_POINT_LOCK_AT_START = BUILDER
            .comment("地点型（选落点的法术、物品）什么时候选落点，游戏里在 选项 → 控制 里改。",
                    "false = 生效时选取：读完 / 松手时按当时的准星重新选，没指着才用开始时的；",
                    "true  = 开始时锁定：开始时选中就定下，之后准星怎么动都不变。")
            .define("targetPointLockAtStart", false);

    public static final ModConfigSpec.BooleanValue SHOW_TARGET_SELF = BUILDER
            .comment("读条时地上的提示（锁定的目标脚下的方块、范围的圆 / 扇形）：是否显示自己的。")
            .define("showTargetSelf", true);

    public static final ModConfigSpec.BooleanValue SHOW_TARGET_PLAYER_ALLY = BUILDER
            .comment("是否显示友方玩家的提示（友方 / 敌方是相对你来说的，按服务器的 PvP 规则和队伍算）。")
            .define("showTargetPlayerAlly", true);

    public static final ModConfigSpec.BooleanValue SHOW_TARGET_PLAYER_ENEMY = BUILDER
            .comment("是否显示敌方玩家的提示。")
            .define("showTargetPlayerEnemy", true);

    public static final ModConfigSpec.BooleanValue SHOW_TARGET_MOB_ALLY = BUILDER
            .comment("是否显示友方生物（你的宠物、召唤物……）的提示。")
            .define("showTargetMobAlly", true);

    public static final ModConfigSpec.BooleanValue SHOW_TARGET_MOB_ENEMY = BUILDER
            .comment("是否显示敌方生物（怪物、Boss……）的提示。")
            .define("showTargetMobEnemy", true);

    public static final ModConfigSpec.ConfigValue<String> TARGET_COLOR_ALLY = BUILDER
            .comment("提示的颜色：作用于友方的（治疗、护盾……），十六进制 #RRGGBB。")
            .define("targetColorAlly", "#55D16A", ClientConfig::isColor);

    public static final ModConfigSpec.ConfigValue<String> TARGET_COLOR_ENEMY = BUILDER
            .comment("提示的颜色：作用于敌方的（伤害、减益……），十六进制 #RRGGBB。")
            .define("targetColorEnemy", "#E5484D", ClientConfig::isColor);

    public static final ModConfigSpec.ConfigValue<String> TARGET_COLOR_ALL = BUILDER
            .comment("提示的颜色：敌我都作用的，以及只标落点的，十六进制 #RRGGBB。")
            .define("targetColorAll", "#F2C230", ClientConfig::isColor);

    public static final ModConfigSpec.BooleanValue JADE_BOSSES_HIDDEN_ONCE = BUILDER
            .comment("内部记录，一般不用改：是否已经替你关掉过 Jade 的「显示Boss」（装了 Jade 才有用）。",
                    "Jade 准星对着 Boss 时会再显示一遍血量，和 Boss 血条重复，所以第一次进世界时自动关一次；",
                    "之后在 Jade 设置里重新打开就不会再自动关；改回 false，下次进世界会再关一次。")
            .define("jadeBossesHiddenOnce", false);

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

    /** 开关类配置的当前值，没加载时取默认值。 */
    public static boolean get(ModConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    /** 颜色配置的 RGB 值（不带透明度）；没加载、写错时取默认值。 */
    public static int color(ModConfigSpec.ConfigValue<String> value) {
        String text = SPEC.isLoaded() ? value.get() : value.getDefault();
        if (!isColor(text)) {
            text = value.getDefault();
        }
        return Integer.parseInt(text.substring(text.length() - 6), 16);
    }

    private static boolean isColor(Object value) {
        return value instanceof String text && text.matches("#?[0-9A-Fa-f]{6}");
    }
}
