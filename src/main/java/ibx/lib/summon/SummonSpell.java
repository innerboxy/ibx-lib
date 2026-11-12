package ibx.lib.summon;

import ibx.lib.IbxLib;
import ibx.lib.api.SummonApi;
import ibx.lib.spell.CastContext;
import ibx.lib.spell.Spell;
import ibx.lib.target.Targeting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/**
 * 召唤法术的基类。一次召几只、每只占几个召唤物名额写在构造里（注册行给）；放之前查名额，单只就超过主人的上限时放不出；
 * 轮盘的说明框多显示召唤那一行。
 *
 * <pre>{@code
 * SPELLS.register("summon_wolf", () -> new SummonWolfSpell(Spell.props().manaCost(30).castTime(1.5F), 2, 1));
 *
 * public final class SummonWolfSpell extends SummonSpell {
 *     public SummonWolfSpell(Properties props, int summonCount, int summonCost) { super(props, summonCount, summonCost); }
 *     @Override public void onCast(CastContext ctx) {
 *         for (int i = 0; i < this.summonCount(); i++) { ... SummonApi.summon(ctx.caster(), wolf, this) ... }
 *     }
 * }
 * }</pre>
 *
 * <p>召唤本身写在 {@code onCast} 里，用 {@link SummonApi#summon(LivingEntity, net.minecraft.world.entity.Mob, SummonSpell)}，
 * 名额按这里的数值走。重写 {@link #canCast} 加别的条件时先调 {@code super.canCast(ctx)}，否则名额就不查了。</p>
 */
public abstract class SummonSpell extends Spell {
    /** 名额不够的提示：每只占几个、上限多少。 */
    private static final String OVER_CAPACITY = "message." + IbxLib.MODID + ".cast.summon_over_capacity";

    private final int summonCount;
    private final int summonCost;

    /**
     * @param summonCount 一次召几只，最少 1
     * @param summonCost  每只占几个召唤物名额（主人的属性「召唤物上限」是总名额），最少 0
     */
    protected SummonSpell(Properties properties, int summonCount, int summonCost) {
        this(properties, Targeting.self(), summonCount, summonCost);
    }

    /** 要选目标的召唤法术（召在准星落点……），瞄准方式同 {@link Spell#Spell(Properties, Targeting)}。 */
    protected SummonSpell(Properties properties, Targeting targeting, int summonCount, int summonCost) {
        super(properties, targeting);
        this.summonCount = Math.max(1, summonCount);
        this.summonCost = Math.max(0, summonCost);
    }

    /** 一次召几只。 */
    public int summonCount() {
        return this.summonCount;
    }

    /** 每只占几个召唤物名额。 */
    public int summonCost() {
        return this.summonCost;
    }

    /** {@code caster} 的名额放不放得下一只（只看上限，不看已用：满了会挤掉最早的）。两端都能问。 */
    public boolean fits(LivingEntity caster) {
        return SummonApi.fits(caster, this.summonCost);
    }

    /** 名额不够时给玩家看的提示。 */
    public Component overCapacityMessage(LivingEntity caster) {
        return Component.translatable(OVER_CAPACITY, this.summonCost, SummonApi.capacity(caster));
    }

    @Override
    public boolean canCast(CastContext ctx) {
        if (!this.fits(ctx.caster())) {
            ctx.fail(this.overCapacityMessage(ctx.caster()));
            return false;
        }
        return true;
    }
}
