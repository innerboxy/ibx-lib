package ibx.lib.spell;

/**
 * 施法结束的原因，传给 {@link Spell#onCastEnd}，也同步到客户端（吟唱条按它决定结束时的颜色）。
 */
public enum CastEndReason {
    /** 正常完成：瞬发 / 吟唱生效，蓄力松手生效。 */
    COMPLETED,
    /** 主动结束：松手（吟唱取消、引导停止、蓄力不足）、死亡、离开世界等。 */
    CANCELLED,
    /** 被打断：吟唱中受击。 */
    INTERRUPTED,
    /** 法力不够：引导中途蓝尽，或吟唱 / 蓄力到生效那一刻付不起。 */
    OUT_OF_MANA
}
