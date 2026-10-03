package ibx.taverntales.lib.client.hud;

import org.jspecify.annotations.Nullable;

import java.util.function.IntSupplier;

/**
 * 吟唱条的客户端状态。施法开始 / 结束时调用这里的方法，{@link TavernHud} 每帧照着画（左边，叠在心形那几行上方）。
 *
 * <p>法术系统还没做，目前只有预览指令 {@link HudPreviewCommand} 在调用；将来由法术框架在
 * {@code ActiveCast} 同步到客户端时调用。只在客户端用。</p>
 */
public final class CastBar {
    private CastBar() {}

    /** 施法类型，决定条的颜色和走法。瞬发没有条。 */
    public enum Kind {
        /** 吟唱：从左往右走，写剩余秒数。 */
        CAST,
        /** 引导：按剩余时间从满往空退；算不出时间（卷轴不耗蓝）时画满条 + 流光。 */
        CHANNEL,
        /** 蓄力：从左往右走，写百分比，蓄满变金并闪光边。 */
        CHARGE
    }

    /** 结束原因，决定结束时变成什么颜色。 */
    public enum Result {
        COMPLETED, INTERRUPTED, CANCELLED, OUT_OF_MANA
    }

    static @Nullable Kind kind;
    static long startTick;
    static int totalTicks;
    static float minCharge;
    static @Nullable IntSupplier channelRemaining;
    static int channelFull;

    static @Nullable Result result;
    static long resultTick;
    static float resultProgress;

    /** 开始吟唱，{@code totalTicks} 是实际读条时长（已经算过施法速率）。 */
    public static void startCast(int totalTicks) {
        begin(Kind.CAST);
        CastBar.totalTicks = Math.max(1, totalTicks);
    }

    /** 开始蓄力，{@code fullTicks} 是蓄满时长，{@code minCharge} 是最低蓄力比例（低于它松手算取消）。 */
    public static void startCharge(int fullTicks, float minCharge) {
        begin(Kind.CHARGE);
        CastBar.totalTicks = Math.max(1, fullTicks);
        CastBar.minCharge = minCharge;
    }

    /**
     * 开始引导。{@code remainingTicks} 每帧被调用，返回按当前蓝量倒算的剩余 tick；
     * 传 null 表示算不出（不耗蓝的来源），画满条 + 流光。
     */
    public static void startChannel(@Nullable IntSupplier remainingTicks) {
        begin(Kind.CHANNEL);
        CastBar.channelRemaining = remainingTicks;
        CastBar.channelFull = 0;
    }

    /** 施法结束：条停在结束时的进度、换成结果的颜色，约半秒后淡出。没在施法时忽略。 */
    public static void end(Result result) {
        if (kind == null) {
            return;
        }
        resultProgress = result == Result.COMPLETED ? 1.0F : progress(TavernHud.ticks(), 0.0F);
        CastBar.result = result;
        resultTick = TavernHud.ticks();
        kind = null;
        channelRemaining = null;
    }

    public static boolean isCasting() {
        return kind != null;
    }

    private static void begin(Kind newKind) {
        kind = newKind;
        startTick = TavernHud.ticks();
        result = null;
    }

    static void clearResult() {
        result = null;
    }

    static float elapsed(long now, float partialTick) {
        return now - startTick + partialTick;
    }

    /** 当前进度 0~1：吟唱 / 蓄力按已读时间，引导按剩余时间占满值的比例。 */
    static float progress(long now, float partialTick) {
        if (kind == null) {
            return 0.0F;
        }
        return switch (kind) {
            case CAST, CHARGE -> Math.min(1.0F, elapsed(now, partialTick) / totalTicks);
            case CHANNEL -> {
                if (channelRemaining == null) {
                    yield 1.0F;
                }
                int remaining = Math.max(0, channelRemaining.getAsInt());
                // 满值取开始时算出的时长；中途回蓝让时长变长就抬高满值，条不溢出
                channelFull = Math.max(channelFull, remaining);
                yield channelFull == 0 ? 0.0F : (float) remaining / channelFull;
            }
        };
    }
}
