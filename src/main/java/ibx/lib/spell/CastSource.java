package ibx.lib.spell;

/**
 * 施法来源，决定扣不扣蓝、进不进冷却（照铁魔法的 {@code CastSource}）。创造模式的玩家另外免蓝、免冷却。
 */
public enum CastSource {
    /** 手持施法物品：扣蓝、进冷却。玩家施法的默认来源。 */
    ITEM(true, true),
    /** 卷轴：不扣蓝、不进冷却，生效过就消耗掉来源物品。 */
    SCROLL(false, false),
    /** 生物：不扣蓝（生物法力默认 0，扣了一个都放不出），进冷却（AI 不用自己记）。普通生物施法的默认来源。 */
    MOB(false, true),
    /** Boss（{@code #c:bosses}）：不扣蓝、不进冷却，放多勤只看 Boss 自己招式的冷却。Boss 施法的默认来源。 */
    BOSS(false, false),
    /** 指令：不扣蓝、不进冷却，正在施法时直接取消原来的再放。 */
    COMMAND(false, false);

    private final boolean consumesMana;
    private final boolean triggersCooldown;

    CastSource(boolean consumesMana, boolean triggersCooldown) {
        this.consumesMana = consumesMana;
        this.triggersCooldown = triggersCooldown;
    }

    public boolean consumesMana() {
        return this.consumesMana;
    }

    public boolean triggersCooldown() {
        return this.triggersCooldown;
    }
}
