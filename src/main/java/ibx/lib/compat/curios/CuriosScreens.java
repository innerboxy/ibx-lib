package ibx.lib.compat.curios;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.Rect2i;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;
import top.theillusivec4.curios.client.screen.CuriosScreen;
import top.theillusivec4.curios.client.screen.button.CuriosButton;
import top.theillusivec4.curios.common.integration.CuriosExclusionAreas;

import java.util.List;

/**
 * Curios 的饰品栏界面（点背包人物框左上角的 Curios 按钮打开）：布局同原版背包，左边多贴一栏饰品槽。
 *
 * <ul>
 *   <li>属性面板和饰品槽占同一块地方，所以饰品栏界面里不放面板；在这里点属性卷轴时用 {@link #closeToInventory}
 *       替玩家点一下 Curios 自己的按钮，回到原版背包再显示面板（走 Curios 原本的切换流程，手上拿着的物品也照它的办法带过去）。</li>
 *   <li>{@link #slotAreas}：饰品栏占的区域，报给 JEI 让左边的书签栏让开。Curios 自己算好了这块（它给 JEI 的注册是注释掉的），直接借用。</li>
 * </ul>
 * Curios 是可选模组：它的类只在 {@link Impl} 里引用，没装 Curios 时不会加载。
 */
public final class CuriosScreens {
    private CuriosScreens() {}

    private static final boolean LOADED = ModList.get().isLoaded("curios");

    public static boolean isCuriosScreen(@Nullable Screen screen) {
        return LOADED && screen != null && Impl.isCuriosScreen(screen);
    }

    /** 在饰品栏界面里时切回原版背包；Curios 的按钮被配置关掉了就什么都不做，返回 false。 */
    public static boolean closeToInventory(Screen screen) {
        return isCuriosScreen(screen) && Impl.closeToInventory(screen);
    }

    /** 饰品栏界面上饰品槽（和上方化妆栏开关）占的区域，不是饰品栏界面就是空的。 */
    public static List<Rect2i> slotAreas(Screen screen) {
        return isCuriosScreen(screen) ? Impl.slotAreas(screen) : List.of();
    }

    private static final class Impl {
        static boolean isCuriosScreen(Screen screen) {
            return screen instanceof CuriosScreen;
        }

        static boolean closeToInventory(Screen screen) {
            for (GuiEventListener child : screen.children()) {
                if (child instanceof CuriosButton button && button.isActive()) {
                    button.onPress(new MouseButtonEvent(button.getX(), button.getY(), new MouseButtonInfo(0, 0)));
                    return true;
                }
            }
            return false;
        }

        static List<Rect2i> slotAreas(Screen screen) {
            return CuriosExclusionAreas.create((CuriosScreen) screen);
        }
    }
}
