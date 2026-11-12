package ibx.lib.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 本模组的服务端配置（随存档保存，会同步给客户端）。
 *
 * <p>读都走 {@link #get}：SERVER 配置在存档加载后才可用，没加载时（注册期、主菜单）{@link #get} 取默认值，
 * 直接调配置项的 {@code get()} 会抛异常。</p>
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

    /**
     * 隐身的生物是否连盔甲、头上戴的和手里的物品也一起藏起来（原版隐身只藏身体）。
     *
     * <p>客户端画面上的效果，按同步过来的服务端值算，所以由服务器统一决定、玩家不能自己关掉。</p>
     */
    public static final ModConfigSpec.BooleanValue HIDE_EQUIPMENT_WHEN_INVISIBLE = BUILDER
            .comment("隐身时是否连装备一起隐藏。",
                    "true  = 看不见的生物，身上的盔甲、鞘翅、头上戴的南瓜 / 头颅、手里的物品也都不显示；",
                    "false = 原版：只藏身体，装备和手里的物品照常显示。",
                    "旁观者、开了「看见隐身队友」的队友两种情况下都看得见；盔甲架不受影响。")
            .define("hideEquipmentWhenInvisible", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    /** 配置项的当前值；没加载（不在存档里）时取默认值。 */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
