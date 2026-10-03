package ibx.taverntales.lib.client.hud;

import ibx.taverntales.lib.api.ManaApi;
import ibx.taverntales.lib.registry.AttachmentRegistry;
import ibx.taverntales.lib.spell.ActiveCast;
import ibx.taverntales.lib.spell.CastContext;
import ibx.taverntales.lib.spell.CastEndReason;
import ibx.taverntales.lib.spell.Spell;
import ibx.taverntales.lib.spell.SpellCasting;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.function.IntSupplier;

/**
 * 吟唱条的客户端状态，{@link TavernHud} 每帧照着画（左边，叠在心形那几行上方）。
 *
 * <p>数据来自本地玩家的施法状态（{@code ActiveCast}，服务端同步过来：法术、来源、开始时刻、读条长度、结束原因）。
 * 每个客户端 tick 由 {@link #tick} 看一眼：换了一次施法就开条，没了就按结束原因收尾。瞬发没有条。
 * 只在客户端用。</p>
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

    /** 上一 tick 看到的那次施法；和这一 tick 不是同一个对象就说明开始 / 结束了。 */
    private static @Nullable CastContext tracked;

    /**
     * 每个客户端 tick 调一次：对照本地玩家的施法状态开条、收尾。
     * 同一 tick 里结束又开始（指令施法顶掉原来的）时，旧的按取消收尾、新的接着开条。
     */
    static void tick(@Nullable Player player) {
        if (player == null) {
            tracked = null;
            kind = null;
            result = null;
            return;
        }
        CastContext ctx = SpellCasting.getCurrentCast(player);
        if (ctx == tracked) {
            return;
        }
        if (tracked != null) {
            ActiveCast active = player.getExistingDataOrNull(AttachmentRegistry.ACTIVE_CAST);
            CastEndReason reason = ctx == null && active != null ? active.lastEnd() : null;
            end(reason != null ? result(reason) : Result.CANCELLED);
        }
        tracked = ctx;
        if (ctx != null) {
            start(player, ctx);
        }
    }

    /** 开条。服务端开始施法到客户端看到会晚一点，起点按已施法 tick 往回推，进度和服务端对齐。 */
    private static void start(Player player, CastContext ctx) {
        Spell spell = ctx.spell();
        switch (spell.castType()) {
            case INSTANT -> {
                // 瞬发没有条
            }
            case CAST -> {
                begin(Kind.CAST, ctx.elapsed());
                totalTicks = Math.max(1, ctx.castTicks());
            }
            case CHARGE -> {
                begin(Kind.CHARGE, ctx.elapsed());
                totalTicks = Math.max(1, ctx.castTicks());
                minCharge = spell.minCharge();
            }
            case CHANNEL -> {
                begin(Kind.CHANNEL, ctx.elapsed());
                channelRemaining = channelRemaining(player, ctx);
                channelFull = 0;
            }
        }
    }

    /**
     * 引导还能撑多久（tick）：够付 {@code n} 次就还能生效 n 次，蓝在第 n + 1 次付不起时停，
     * 即 {@code n × 间隔 + 离下一次生效还剩的 tick}。不扣蓝的来源（卷轴、指令）、不耗蓝的法术、创造模式算不出，返回 null（满条 + 流光）。
     */
    private static @Nullable IntSupplier channelRemaining(Player player, CastContext ctx) {
        Spell spell = ctx.spell();
        float cost = ManaApi.getActualCost(player, spell.manaCost());
        if (!ctx.source().consumesMana() || cost <= 0.0F || player.isCreative()) {
            return null;
        }
        int interval = spell.tickInterval();
        return () -> (int) (ManaApi.getMana(player) / cost) * interval + interval - ctx.elapsed() % interval;
    }

    private static Result result(CastEndReason reason) {
        return switch (reason) {
            case COMPLETED -> Result.COMPLETED;
            case INTERRUPTED -> Result.INTERRUPTED;
            case CANCELLED -> Result.CANCELLED;
            case OUT_OF_MANA -> Result.OUT_OF_MANA;
        };
    }

    /** 施法结束：条停在结束时的进度、换成结果的颜色，约半秒后淡出。没在施法时忽略。 */
    private static void end(Result result) {
        if (kind == null) {
            return;
        }
        // 完成按满宽画；引导蓝尽时剩余时间正好见底，也按满宽画，不然蓝色反馈只剩一条缝
        resultProgress = result == Result.COMPLETED || (kind == Kind.CHANNEL && result == Result.OUT_OF_MANA)
                ? 1.0F : progress(TavernHud.ticks(), 0.0F);
        CastBar.result = result;
        resultTick = TavernHud.ticks();
        kind = null;
        channelRemaining = null;
    }

    private static void begin(Kind newKind, int alreadyElapsed) {
        kind = newKind;
        startTick = TavernHud.ticks() - Math.max(0, alreadyElapsed);
        result = null;
        channelRemaining = null;
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
