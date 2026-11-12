package ibx.lib.compat;

import net.neoforged.fml.ModList;

/**
 * Jade 联动（modId {@code jade}，只在客户端）。本模组对它是<b>非强制依赖</b>。
 *
 * <ul>
 *   <li>首次进世界关掉它的「显示Boss」：{@link ibx.lib.compat.jade.JadeBossHiding}，装了才由客户端入口注册；</li>
 *   <li>提示框里的魔法抗性：{@link ibx.lib.compat.jade.IbxJadePlugin}，Jade 按注解自己找到它。</li>
 * </ul>
 */
public final class JadeCompat {
    public static final String MODID = "jade";

    private JadeCompat() {}

    /** Jade 是否已加载。 */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MODID);
    }
}
