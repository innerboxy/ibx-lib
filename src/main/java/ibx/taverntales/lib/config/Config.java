package ibx.taverntales.lib.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 本模组的服务端配置（随存档保存，会同步给客户端）。
 *
 * <p>注意读取时机：SERVER 配置在存档加载后才可用，<b>只能在游戏运行中读</b>，
 * 不要在注册期或类初始化时读，否则会抛异常。</p>
 */
public final class Config {
    private Config() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * 是否保留原版的跳劈暴击（下落攻击必定暴击）。
     *
     * <p>默认关闭：暴击完全由 {@code critical_chance} 属性决定，跳劈不再自动暴击。
     * 开启后跳劈仍然必定暴击，并与概率暴击取或——即跳劈时必暴，平地攻击仍按概率判定。
     * 无论开关如何，暴击的伤害倍率一律走 {@code critical_damage}，不再是原版写死的 1.5 倍。</p>
     */
    public static final ModConfigSpec.BooleanValue ENABLE_VANILLA_CRITICAL = BUILDER
            .comment("是否保留原版跳劈暴击（下落攻击必定暴击）。",
                    "false = 暴击完全由 critical_chance 属性决定。",
                    "true  = 跳劈仍必定暴击，平地攻击另按 critical_chance 判定。",
                    "两种情况下暴击倍率都取 critical_damage，而非原版写死的 1.5 倍。")
            .define("enableVanillaCritical", false);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
