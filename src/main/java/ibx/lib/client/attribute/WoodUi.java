package ibx.lib.client.attribute;

import ibx.lib.client.UiPalette;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 属性面板、它的设置界面和创造栏子栏共用的木框画法（同 lib 说明框的配色，见 {@link UiPalette}），都按 GUI 像素画。
 */
public final class WoodUi {
    private WoodUi() {}

    /** 面板：胡桃木底、琥珀渐变细边（上亮下暗）、最外圈深色描边，四角各缺一格。 */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, UiPalette.PANEL_DARK);
        g.fill(x, y + 1, x + w, y + h - 1, UiPalette.PANEL_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, UiPalette.PANEL | 0xFF000000);
        g.fillGradient(x + 1, y + 1, x + 2, y + h - 1, UiPalette.TOOLTIP_BORDER_TOP, UiPalette.TOOLTIP_BORDER_BOTTOM);
        g.fillGradient(x + w - 2, y + 1, x + w - 1, y + h - 1, UiPalette.TOOLTIP_BORDER_TOP, UiPalette.TOOLTIP_BORDER_BOTTOM);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, UiPalette.TOOLTIP_BORDER_TOP);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, UiPalette.TOOLTIP_BORDER_BOTTOM);
    }

    /** 凹进去的一块（列表底）：左 / 上沿暗、右 / 下沿亮。 */
    public static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, UiPalette.INSET);
        g.fill(x, y, x + w, y + 1, UiPalette.PANEL_DARK);
        g.fill(x, y, x + 1, y + h, UiPalette.PANEL_DARK);
        g.fill(x, y + h - 1, x + w, y + h, UiPalette.EDGE);
        g.fill(x + w - 1, y, x + w, y + h, UiPalette.EDGE);
    }

    /** 凸起的木按钮：深色描边，左 / 上沿亮、右 / 下沿暗；指着时整块亮一档。 */
    static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hovered) {
        g.fill(x, y, x + w, y + h, UiPalette.PANEL_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hovered ? UiPalette.EDGE : UiPalette.PANEL_LIGHT);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, hovered ? UiPalette.WHEEL_RIM_LIGHT : UiPalette.EDGE);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, hovered ? UiPalette.WHEEL_RIM_LIGHT : UiPalette.EDGE);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, UiPalette.PANEL_DARK);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, UiPalette.PANEL_DARK);
    }

    /** 9×9 勾选框：四边一样的 1 像素亮边、里面 7×7 深底；选中时正中一个 5×5 去掉四角的琥珀色点（四周各空 1 格）。 */
    static void checkbox(GuiGraphicsExtractor g, int x, int y, boolean on) {
        g.fill(x, y, x + 9, y + 9, UiPalette.EDGE);
        g.fill(x + 1, y + 1, x + 8, y + 8, UiPalette.PANEL_DARK);
        if (on) {
            g.fill(x + 3, y + 2, x + 6, y + 7, UiPalette.ACCENT);
            g.fill(x + 2, y + 3, x + 7, y + 6, UiPalette.ACCENT);
        }
    }

    /** 竖滚动条：4 宽的深槽 + 木色滑块；{@code first} 是第一行的序号，{@code visible} 一屏几行，{@code total} 共几行。 */
    static void scrollbar(GuiGraphicsExtractor g, int x, int y, int h, int first, int visible, int total) {
        g.fill(x, y, x + 4, y + h, UiPalette.PANEL_DARK);
        if (total <= visible) {
            button(g, x, y, 4, h, false);
            return;
        }
        int thumb = Math.max(8, h * visible / total);
        int top = y + (h - thumb) * first / (total - visible);
        button(g, x, top, 4, thumb, false);
    }

    /** 滚动条上按到 {@code mouseY} 时第一行该是第几行（滑块中心跟着鼠标）。 */
    static int scrollFromMouse(double mouseY, int y, int h, int visible, int total) {
        if (total <= visible) {
            return 0;
        }
        int thumb = Math.max(8, h * visible / total);
        double t = (mouseY - y - thumb / 2.0) / (h - thumb);
        return (int) Math.round(Math.max(0.0, Math.min(1.0, t)) * (total - visible));
    }
}
