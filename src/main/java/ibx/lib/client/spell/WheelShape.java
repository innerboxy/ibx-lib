package ibx.lib.client.spell;

import ibx.lib.client.UiPalette;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * 法术轮盘的形状：{@code n} 个扇形拼成一个圆，每个扇形一格，尖朝中心，第 0 格在正上方、顺时针排；
 * 扇形之间留 {@link #GAP} 像素的透明缝。按 GUI 像素光栅化（画出来是像素画，和原版界面一个精度），结果按（格数, 半径）缓存。
 *
 * <p>每个扇形自成一格，分层照原版快捷栏（hud/hotbar.png）的画法、颜色用模组配色（{@link UiPalette} 的 {@code WHEEL_*}，胡桃木）：
 * 最外一圈描边，描边内一圈框（朝上 / 左亮、朝下 / 右暗），再一圈内沿（明暗和框反过来），格子底半透明。
 * 指向的那一格把框、内沿和再往里一圈换成选中框的三圈（琥珀：浅、中、深），描边不变。</p>
 *
 * <p>每层的"深度"用 4 邻接腐蚀算（离格子边界几步），所以斜边和圆弧也是一像素宽、对角相连的像素线。
 * 半径先从小往大试出"刚好放得下"的值（每格的 16×16 图标完整落在格子底色里，不压到任何一圈框，见 {@link #fitRadius}），
 * 实际画多大由轮盘按配置的倍数放大（{@code spellWheelScale}），图标仍是 16×16。</p>
 */
final class WheelShape {
    static final int MIN_SLOTS = 3;
    static final int ICON = 16;
    /** 格子之间的缝宽（GUI 像素）：离分界线不到一半的像素不画。 */
    static final double GAP = 2.0;
    private static final int MIN_RADIUS = 24;
    private static final int MAX_RADIUS = 200;
    /** 图标离格子边界至少这么多层：描边 1 + 指向时的三圈选中框 3，图标都压不到。 */
    private static final int ICON_DEPTH = 5;

    private static final int OUTLINE = UiPalette.WHEEL_OUTLINE;
    private static final int RIM_LIGHT = UiPalette.WHEEL_RIM_LIGHT;
    private static final int RIM_DARK = UiPalette.WHEEL_RIM_DARK;
    private static final int BEVEL_DARK = UiPalette.WHEEL_BEVEL_DARK;
    private static final int BEVEL_LIGHT = UiPalette.WHEEL_BEVEL_LIGHT;
    private static final int FILL = UiPalette.WHEEL_FILL;
    private static final int SELECT_LIGHT = UiPalette.WHEEL_SELECT_LIGHT;
    private static final int SELECT_MID = UiPalette.WHEEL_SELECT_MID;
    private static final int SELECT_DARK = UiPalette.WHEEL_SELECT_DARK;

    private static final int[][] NEIGHBORS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
    private static final Map<Integer, Integer> FIT_RADIUS = new HashMap<>();
    private static final Map<Long, WheelShape> CACHE = new HashMap<>();

    /** 格数（至少 {@link #MIN_SLOTS}）。 */
    final int slots;
    /** 圆的半径。 */
    final int radius;
    /** 中心在本形状左上角往右下 {@code half} 像素处；整个形状 {@code size × size}。 */
    final int half;
    final int size;
    /** 实际画到的范围（相对形状左上角，含两端）：放名字、说明框用。 */
    final int top;
    final int bottom;
    final int left;
    final int right;
    /** 每格图标的左上角（相对形状左上角）。 */
    final int[] iconX;
    final int[] iconY;
    /** 像素 → 画在哪一格，-1 为不画（轮盘外或缝里）。 */
    private final byte[] slotOf;
    /** 像素 → 指向判定算哪一格：按扇区分，不扣缝，鼠标划过缝时高亮不闪。-1 为轮盘外。 */
    private final byte[] sectorOf;
    /** 每个像素离本格边界几层。 */
    private final byte[] depth;
    /** 平时的样子：按行合并的色段，每段 4 个数 {x0, x1, y, ARGB}。 */
    final int[] baseRuns;
    /** 指向第 i 格时盖在上面的选中框色段（不透明）。 */
    final int[][] hoverRuns;

    /** {@code slots} 格（不足 {@link #MIN_SLOTS} 按它算）、半径 {@code radius} 的轮盘。 */
    static WheelShape of(int slots, int radius) {
        int n = Math.max(MIN_SLOTS, slots);
        return CACHE.computeIfAbsent((long) n << 32 | radius, key -> new WheelShape(n, radius));
    }

    /** 图标刚好放得下的最小半径（按格数缓存）。 */
    static int fitRadius(int slots) {
        return FIT_RADIUS.computeIfAbsent(Math.max(MIN_SLOTS, slots), n -> {
            for (int radius = MIN_RADIUS; radius < MAX_RADIUS; radius++) {
                if (new WheelShape(n, radius).iconsFit()) {
                    return radius;
                }
            }
            return MAX_RADIUS;
        });
    }

    /** 中心在 (0, 0)、指向 (dx, dy) 的那一格；不在轮盘里为 -1。 */
    int slotAt(double dx, double dy) {
        int x = (int) Math.floor(dx) + this.half;
        int y = (int) Math.floor(dy) + this.half;
        if (x < 0 || y < 0 || x >= this.size || y >= this.size) {
            return -1;
        }
        return this.sectorOf[y * this.size + x];
    }

    private WheelShape(int slots, int radius) {
        this.slots = slots;
        this.radius = radius;
        this.half = radius + 2;
        this.size = this.half * 2;
        int pixels = this.size * this.size;
        double step = 2 * Math.PI / slots;
        // 第 k 条分界线（中心 → 第 k 个顶点）的方向；第 i 格夹在第 i、i + 1 条之间
        double[] ux = new double[slots + 1];
        double[] uy = new double[slots + 1];
        for (int k = 0; k <= slots; k++) {
            double angle = -Math.PI / 2 + (k - 0.5) * step;
            ux[k] = Math.cos(angle);
            uy[k] = Math.sin(angle);
        }

        // 每个像素中心先按方向分到扇区，再看在不在圆里，最后扣掉离两条分界线太近的（缝）
        this.slotOf = new byte[pixels];
        this.sectorOf = new byte[pixels];
        for (int y = 0; y < this.size; y++) {
            for (int x = 0; x < this.size; x++) {
                double px = x - this.half + 0.5;
                double py = y - this.half + 0.5;
                double t = Math.atan2(py, px) + Math.PI / 2 + step / 2;
                t = ((t % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
                int i = (int) (t / step) % slots;
                int p = y * this.size + x;
                boolean inside = px * px + py * py <= (double) radius * radius;
                this.sectorOf[p] = (byte) (inside ? i : -1);
                boolean inGap = Math.min(rayDistance(px, py, ux[i], uy[i]), rayDistance(px, py, ux[i + 1], uy[i + 1])) < GAP / 2;
                this.slotOf[p] = (byte) (inside && !inGap ? i : -1);
            }
        }
        int top = this.size;
        int bottom = 0;
        int left = this.size;
        int right = 0;
        for (int p = 0; p < pixels; p++) {
            if (this.slotOf[p] >= 0) {
                top = Math.min(top, p / this.size);
                bottom = Math.max(bottom, p / this.size);
                left = Math.min(left, p % this.size);
                right = Math.max(right, p % this.size);
            }
        }
        this.top = top;
        this.bottom = bottom;
        this.left = left;
        this.right = right;
        this.depth = this.depth();

        // 图标放在扣掉缝之后这一格的内切圆圆心：沿角平分线走 t，离两条（往里收了半个缝的）分界线和圆弧一样远
        this.iconX = new int[slots];
        this.iconY = new int[slots];
        double halfAngle = step / 2;
        double t = (radius + GAP / 2) / (1 + Math.sin(halfAngle));
        for (int i = 0; i < slots; i++) {
            double angle = -Math.PI / 2 + i * step;
            this.iconX[i] = (int) Math.round(t * Math.cos(angle) - ICON / 2.0) + this.half;
            this.iconY[i] = (int) Math.round(t * Math.sin(angle) - ICON / 2.0) + this.half;
        }

        int[] base = new int[pixels];
        int[][] hover = new int[slots][pixels];
        for (int p = 0; p < pixels; p++) {
            int slot = this.slotOf[p];
            if (slot < 0) {
                continue;
            }
            int d = this.depth[p];
            base[p] = switch (d) {
                case 1 -> OUTLINE;
                case 2 -> this.facesUpOrLeft(p) ? RIM_LIGHT : RIM_DARK;
                case 3 -> this.facesUpOrLeft(p) ? BEVEL_DARK : BEVEL_LIGHT;
                default -> FILL;
            };
            if (d >= 2 && d <= 4) {
                hover[slot][p] = d == 2 ? SELECT_LIGHT : d == 3 ? SELECT_MID : SELECT_DARK;
            }
        }
        this.baseRuns = this.runs(base);
        this.hoverRuns = new int[slots][];
        for (int i = 0; i < slots; i++) {
            this.hoverRuns[i] = this.runs(hover[i]);
        }
    }

    /** 点到射线（从中心出发、方向为单位向量 u）的距离。 */
    private static double rayDistance(double px, double py, double ux, double uy) {
        double along = px * ux + py * uy;
        return along > 0 ? Math.abs(px * uy - py * ux) : Math.hypot(px, py);
    }

    /**
     * 上一层（深度小 1 的像素或格子外）在这个像素的哪边：上 / 左算"朝上左"（框亮、内沿暗），下 / 右算"朝下右"。
     * 同时有上和下时以上为准，先竖后横。
     */
    private boolean facesUpOrLeft(int p) {
        int x = p % this.size;
        int y = p / this.size;
        int want = this.depth[p] - 1;
        boolean[] outside = new boolean[4];
        for (int n = 0; n < 4; n++) {
            int nx = x + NEIGHBORS[n][0];
            int ny = y + NEIGHBORS[n][1];
            if (nx < 0 || ny < 0 || nx >= this.size || ny >= this.size) {
                outside[n] = true;
                continue;
            }
            int q = ny * this.size + nx;
            outside[n] = this.slotOf[q] != this.slotOf[p] || this.depth[q] == want;
        }
        if (outside[0]) {
            return true;
        }
        if (outside[1]) {
            return false;
        }
        return outside[2];
    }

    /** 4 邻接腐蚀出的层数：挨着本格边界（格子外、缝、轮盘外）的是 1，往里一步加一，最多算到 7。 */
    private byte[] depth() {
        int pixels = this.size * this.size;
        byte[] depth = new byte[pixels];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int p = 0; p < pixels; p++) {
            if (this.slotOf[p] < 0) {
                continue;
            }
            int x = p % this.size;
            int y = p / this.size;
            for (int[] n : NEIGHBORS) {
                int nx = x + n[0];
                int ny = y + n[1];
                if (nx < 0 || ny < 0 || nx >= this.size || ny >= this.size || this.slotOf[ny * this.size + nx] != this.slotOf[p]) {
                    depth[p] = 1;
                    queue.add(p);
                    break;
                }
            }
        }
        while (!queue.isEmpty()) {
            int p = queue.poll();
            if (depth[p] >= 6) {
                continue;
            }
            int x = p % this.size;
            int y = p / this.size;
            for (int[] n : NEIGHBORS) {
                int q = (y + n[1]) * this.size + x + n[0];
                if (depth[q] == 0 && this.slotOf[q] == this.slotOf[p]) {
                    depth[q] = (byte) (depth[p] + 1);
                    queue.add(q);
                }
            }
        }
        for (int p = 0; p < pixels; p++) {
            if (this.slotOf[p] >= 0 && depth[p] == 0) {
                depth[p] = 7;
            }
        }
        return depth;
    }

    private boolean iconsFit() {
        for (int i = 0; i < this.slots; i++) {
            for (int y = this.iconY[i]; y < this.iconY[i] + ICON; y++) {
                for (int x = this.iconX[i]; x < this.iconX[i] + ICON; x++) {
                    if (x < 0 || y < 0 || x >= this.size || y >= this.size) {
                        return false;
                    }
                    int p = y * this.size + x;
                    if (this.slotOf[p] != i || this.depth[p] < ICON_DEPTH) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** 逐行把相邻同色像素并成一段；颜色 0 表示不画。 */
    private int[] runs(int[] colors) {
        IntArrayList runs = new IntArrayList();
        for (int y = 0; y < this.size; y++) {
            int x = 0;
            while (x < this.size) {
                int color = colors[y * this.size + x];
                int end = x + 1;
                while (end < this.size && colors[y * this.size + end] == color) {
                    end++;
                }
                if (color != 0) {
                    runs.add(x);
                    runs.add(end);
                    runs.add(y);
                    runs.add(color);
                }
                x = end;
            }
        }
        return runs.toIntArray();
    }

    @Override
    public String toString() {
        return "WheelShape[slots=" + this.slots + ", radius=" + this.radius + ", runs=" + this.baseRuns.length / 4 + "]";
    }
}
