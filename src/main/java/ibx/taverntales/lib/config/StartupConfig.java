package ibx.taverntales.lib.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 本模组的启动配置（{@code config/taverntales_l1b-startup.toml}）。
 *
 * <p>STARTUP 类型在注册时就立即加载，注册期也能读——原版弓弩的默认属性修饰符是在
 * {@code ModifyDefaultComponentsEvent} 里烤进物品的，SERVER 配置到那时还没加载，所以开关放在这里。</p>
 *
 * <p><b>不会同步</b>：服务端与客户端各读各的文件，两边要改成一致，否则客户端显示的数值
 * （tooltip、拉弓动画）会与服务端实际结算对不上。改动需重启游戏才生效。</p>
 */
public final class StartupConfig {
    private StartupConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * 是否接管原版弓与弩，让它们与 Tt 弓弩走同一套远程系统（属性、伤害快照、暴击、自动射击、副手禁用）。
     * 详见 {@link ibx.taverntales.lib.item.ranged_weapon.VanillaRangedWeapons}。
     */
    public static final ModConfigSpec.BooleanValue TAKE_OVER_VANILLA_RANGED_WEAPONS = BUILDER
            .comment("是否接管原版弓与弩，让它们按本模组的远程属性结算。",
                    "true  = 原版弓弩带上远程伤害 / 拉弦时间属性，伤害、蓄力、装填、暴击与模组弓弩一致，副手不可使用；",
                    "        手持原版弓弩的生物（骷髅、掠夺者等）同样按属性结算。",
                    "false = 原版弓弩保持原版行为。",
                    "服务端与客户端需保持一致；修改后重启游戏生效。")
            .define("takeOverVanillaRangedWeapons", true);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
