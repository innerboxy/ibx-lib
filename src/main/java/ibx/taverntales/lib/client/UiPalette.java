package ibx.taverntales.lib.client;

import ibx.taverntales.lib.TavernTalesLib;
import net.minecraft.resources.Identifier;

/**
 * 本模组界面的统一配色：胡桃木底、奶油色字、烛光琥珀强调。界面代码只引用这里的语义名，
 * 不直接写色值，换色只改这一处。值都是 ARGB。
 *
 * <p>说明框的木色底和琥珀边框是贴图（{@link #TOOLTIP_STYLE}，材质包可以换），这里的 {@code TOOLTIP_*} 只给自己画框的地方对色用。
 * HUD 的条框和快捷栏也是贴图，用同一套木色、琥珀选中框，由素材脚本生成，不读这里；快捷栏在模组自带材质包「酒馆轶事：界面材质」里
 * （在主模组 tavern 的 {@code resourcepacks/hud}），玩家可以关掉。
 * 锻造台不依赖 lib，用自己的一套配色。</p>
 */
public final class UiPalette {
    private UiPalette() {}

    // ---------------------------------------------------------------- 文字

    /** 标题：法术名、面板标题（烛光）。 */
    public static final int TITLE = 0xFFFFD58A;
    /** 正文（奶油）。 */
    public static final int BODY = 0xFFEBD9B4;
    /** 次要字：描述、冷却这类说明（旧木）。 */
    public static final int MUTED = 0xFFA88A68;
    /** 数值：伤害这类要一眼看到的数字。 */
    public static final int VALUE = 0xFFFFF4DC;
    /** 强调（琥珀）。 */
    public static final int ACCENT = 0xFFFFB347;

    // ---------------------------------------------------------------- 语义色

    /** 法力（文字用，比蓝条 {@link #MANA_BAR} 亮，深色底上看得清）。 */
    public static final int MANA = 0xFF5E9BFF;
    /** 法力条、法术伤害数字的主色。 */
    public static final int MANA_BAR = 0xFF3A7CFF;
    public static final int HEALTH = 0xFFE0503C;
    /** 法术名、法术相关。 */
    public static final int SPELL = 0xFFA58CFF;
    public static final int HEAL = 0xFF8FDB5A;
    public static final int CRIT = 0xFFFF8A3D;
    /** 正面：够用、成功。 */
    public static final int POSITIVE = 0xFF9FD86B;
    /** 负面：不够、失败、冷却中。 */
    public static final int NEGATIVE = 0xFFFF6A4D;
    public static final int WARNING = 0xFFFFD24A;
    public static final int DISABLED = 0xFF7A6656;

    // ---------------------------------------------------------------- 面板、说明框

    /** 面板底（胡桃木，半透明）、亮边、暗边、描边。 */
    public static final int PANEL = 0xF02B1D14;
    public static final int PANEL_LIGHT = 0xFF4A3222;
    public static final int PANEL_DARK = 0xFF120B07;
    public static final int OUTLINE = 0xFF000000;
    /** 说明框底、边框上沿、下沿（同贴图）。 */
    public static final int TOOLTIP_BACKGROUND = 0xF0231710;
    public static final int TOOLTIP_BORDER_TOP = 0xFFFFB347;
    public static final int TOOLTIP_BORDER_BOTTOM = 0xFF8A5A20;
    /**
     * 说明框样式：贴图 {@code taverntales_l1b:tooltip/tavern_background}、{@code tooltip/tavern_frame}（九宫格，同原版）。
     * 自己画说明框时传给 {@code tooltip(...)} 的最后一个参数；物品可以用原版 {@code tooltip_style} 组件指向它。
     */
    public static final Identifier TOOLTIP_STYLE = Identifier.fromNamespaceAndPath(TavernTalesLib.MODID, "tavern");

    // ---------------------------------------------------------------- 法术轮盘（按 GUI 像素画的框，见 WheelShape）

    /** 最外圈描边。 */
    public static final int WHEEL_OUTLINE = 0xFF120B07;
    /** 框：朝上 / 左的亮边、朝下 / 右的暗边。 */
    public static final int WHEEL_RIM_LIGHT = 0xFF9A6B42;
    public static final int WHEEL_RIM_DARK = 0xFF4A3222;
    /** 内沿：朝上 / 左的暗、朝下 / 右的亮（和框反过来，做出凹进去的感觉）。 */
    public static final int WHEEL_BEVEL_DARK = 0xFF4A3222;
    public static final int WHEEL_BEVEL_LIGHT = 0xFF6E4B30;
    /** 格子底（半透明）。 */
    public static final int WHEEL_FILL = 0xC82B1D14;
    /** 指着 / 选中那一格的三圈：浅、中、深（琥珀）。 */
    public static final int WHEEL_SELECT_LIGHT = 0xFFFFE0A0;
    public static final int WHEEL_SELECT_MID = 0xFFFFB347;
    public static final int WHEEL_SELECT_DARK = 0xFFB86E1E;
}
