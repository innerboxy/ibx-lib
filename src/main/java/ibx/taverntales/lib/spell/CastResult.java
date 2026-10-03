package ibx.taverntales.lib.spell;

import ibx.taverntales.lib.TavernTalesLib;

/**
 * {@link SpellCasting#cast} 的结果。失败时框架已经把原因发到玩家的 action bar，调用方不用再翻译。
 * 检查顺序就是枚举里失败项的顺序。
 */
public enum CastResult {
    /** 已生效（瞬发）或已开始读条（吟唱 / 引导 / 蓄力）。 */
    SUCCESS,
    /** 正在施别的法术。 */
    ALREADY_CASTING,
    /** 法术自己的条件不满足（{@link Spell#canCast}），或被 {@code SpellCastEvent.Pre} 取消。 */
    CONDITION_FAILED,
    /** 冷却没好。 */
    ON_COOLDOWN,
    /** 法力不够。 */
    NOT_ENOUGH_MANA,
    /** 身上没有任何可放的法术（只有"放当前选中"那条路会返回）。 */
    NO_SPELL;

    public boolean isSuccess() {
        return this == SUCCESS;
    }

    /** 给玩家看的提示语言键：{@code message.taverntales_l1b.cast.<小写名>}。 */
    public String messageKey() {
        return "message." + TavernTalesLib.MODID + ".cast." + this.name().toLowerCase(java.util.Locale.ROOT);
    }
}
