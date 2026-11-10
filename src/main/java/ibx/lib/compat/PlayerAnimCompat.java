package ibx.lib.compat;

import net.neoforged.fml.ModList;

/**
 * Player Animation Library（modId {@code player_animation_library}）联动。本模组对它是<b>非强制依赖</b>，只在客户端用到。
 *
 * <p><b>凡是会碰到 {@code com.zigythebird} 的类，加载前必须先过 {@link #isLoaded()}</b>，否则玩家没装这个模组时会 NoClassDefFoundError。
 * 施法、盾击的动作在 {@code ibx.lib.client}（装了才注册）；第一人称那一遍的查询见 {@link ibx.lib.compat.playeranim.PlayerAnimFirstPerson}。</p>
 */
public final class PlayerAnimCompat {
    public static final String MODID = "player_animation_library";

    private PlayerAnimCompat() {}

    /** Player Animation Library 是否已加载。 */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MODID);
    }
}
