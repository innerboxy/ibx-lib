package ibx.lib.client.hud;

import ibx.lib.IbxLib;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * HUD 自动避让：别的模组画在 HUD 上的东西由联动类报到这里（这一帧占的矩形，GUI 坐标），
 * 本模组的 HUD 元素摆之前先问一下，压到了就横着往外挪开。现在用它的是当前法术框（{@link TavernHud}）。
 *
 * <p>来源在客户端入口里 {@link #register} 进来（InventoryHUD+ 见 {@code compat.InventoryHudCompat}）。
 * 每帧现算、不缓存：别的模组的 HUD 会随手里拿的东西、它自己的设置变。
 * 来源抛异常（多半是对方模组换了版本、对不上）时记一条警告，之后不再问它，HUD 照常画。</p>
 */
public final class HudAvoidance {
    private HudAvoidance() {}

    /** 挪开后和障碍之间留的空隙。 */
    private static final int GAP = 2;

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

    /** 一个会在 HUD 上画东西的模组。 */
    @FunctionalInterface
    public interface Source {
        /** 把这一帧画出来的东西占的矩形（GUI 坐标）交给 {@code out}；这一帧不画就什么都不交。 */
        void collect(Player player, int guiWidth, int guiHeight, Consumer<Rect2i> out);
    }

    public static void register(Source source) {
        SOURCES.add(source);
    }

    /**
     * 把 {@code [x, x+width) × [y, y+height)} 横着推出所有障碍（{@code leftward} 往左推，否则往右），和障碍隔开 {@link #GAP}；
     * 没压到就原样返回。推到屏幕边上就停：宁可压着，也不画到屏幕外。
     *
     * @return 推完的 x
     */
    public static int pushOut(Player player, int guiWidth, int guiHeight, int x, int y, int width, int height, boolean leftward) {
        if (SOURCES.isEmpty()) {
            return x;
        }
        List<Rect2i> obstacles = new ArrayList<>();
        for (Source source : SOURCES) {
            try {
                source.collect(player, guiWidth, guiHeight, obstacles::add);
            } catch (RuntimeException | LinkageError e) {
                SOURCES.remove(source);
                IbxLib.LOGGER.warn("HUD 避让：有一个来源出错，之后不再问它（见下面的堆栈）", e);
            }
        }
        // 每次都推到压着的那个障碍外侧，之后只会更往外，所以每个障碍最多推一次
        for (int i = 0; i < obstacles.size(); i++) {
            Rect2i hit = firstOverlap(obstacles, x - GAP, y, width + 2 * GAP, height);
            if (hit == null) {
                break;
            }
            x = leftward ? hit.getX() - GAP - width : hit.getX() + hit.getWidth() + GAP;
        }
        return Math.max(0, Math.min(x, guiWidth - width));
    }

    private static @Nullable Rect2i firstOverlap(List<Rect2i> obstacles, int x, int y, int width, int height) {
        for (Rect2i o : obstacles) {
            if (o.getWidth() > 0 && o.getHeight() > 0
                    && o.getX() < x + width && x < o.getX() + o.getWidth()
                    && o.getY() < y + height && y < o.getY() + o.getHeight()) {
                return o;
            }
        }
        return null;
    }
}
