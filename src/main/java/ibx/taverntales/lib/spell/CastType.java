package ibx.taverntales.lib.spell;

/**
 * 施法类型。吟唱条（{@code client.hud.CastBar}）直接按它决定条的样式。
 *
 * <p>不写时按吟唱时间推断：0 为 {@link #INSTANT}，大于 0 为 {@link #CAST}；引导、蓄力要在 Properties 里显式写。</p>
 */
public enum CastType {
    /** 瞬发：施放即生效，没有读条。 */
    INSTANT,
    /** 吟唱：读条到点自动生效；中途松手或被打断则取消，不扣蓝、不进冷却。 */
    CAST,
    /** 引导：按住期间每隔 {@code tickInterval} 扣一次蓝、生效一次（开始那一 tick 就生效第一次）；松手或蓝尽结束。 */
    CHANNEL,
    /** 蓄力：按住蓄力，松手时按蓄力比例生效一次；比例低于 {@code minCharge} 视为取消。 */
    CHARGE
}
